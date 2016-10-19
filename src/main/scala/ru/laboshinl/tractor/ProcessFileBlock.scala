package ru.laboshinl.tractor

import java.io.{File, FileInputStream}
import java.nio.channels.FileChannel
import java.nio.{ByteBuffer, ByteOrder}

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString

import scala.util.control.Breaks._

class ProcessFileBlock extends Actor with ActorLogging {
  val PcapHeaderLen = 16
  val waiter = context.actorOf(Props(new SendWorkWaitResult(Props[ReadPacketActor])))

  override def receive: Actor.Receive = {
    case FileBlock(file, start, stop) =>
      log.debug("ProcessFileBlock received FileBlock {}-{}", start, stop)
      //val chunk = new FileInputStream(file).getChannel
      val chunk = new RandomAccessFile(file)(ByteConverterLittleEndian)
      val packets = readFileChunk(chunk,start,stop)
      waiter tell(packets, sender())
      log.debug("Packets sent")
    case _ =>
      log.error("unknown message")
  }

  @throws(classOf[java.io.EOFException])
  private def seekToPacketRecord(rafObj: RandomAccessFile): Unit = {
    breakable {
      while (rafObj.getFilePointer < rafObj.length) {
        val position = rafObj.getFilePointer
        val timestamp = rafObj.readInt()
        rafObj.skipBytes(4)
        val lengths = rafObj.readInt32(2)
        if (lengths(0).equals(lengths(1)) // included len == captured len
          && 41.to(65535).contains(lengths(0))) {
          // MinSize  > packet len > MaxSize
          rafObj.skipBytes(lengths(0)) // Jump to the next packet
          if (0.to(600).contains(timestamp - rafObj.readInt())) {
            //600
            rafObj.seek(position)
            break()
          }
        }
        rafObj.seek(position + 1)
      }
    }
  }


  private def reedLeInt(file: FileChannel): Int = {
    val buff = ByteBuffer.allocate(4)
    file.read(buff)
    buff.order(ByteOrder.LITTLE_ENDIAN).getInt(0)
  }


  private def seekToPacketRecord(file: FileChannel): Unit = {
    val PcapHeaderLen = 16
    breakable {
      while (file.position() < file.size()) {
        val position = file.position()
        val timestamp = reedLeInt(file)
        reedLeInt(file)
        val length = Array(reedLeInt(file), reedLeInt(file))
        if (length(0).equals(length(1)) && 41.to(65535).contains(length(0))) {
          file.position(position + PcapHeaderLen + length(0))
          if (0.to(600).contains(timestamp - reedLeInt(file))) {
            file.position(position)
            break()
          }
        } else file.position(position + 1)
      }
    }
  }

  private def readFileChunk(rafObj: RandomAccessFile, start: Long, stop: Long ) : List[ReadPacket] = {
    var packets = List[ReadPacket]()
    rafObj.seek(start)
    try {
      seekToPacketRecord(rafObj)
    }
    catch {
      case e: Exception =>
        log.error("Cannot seek to fist packet, {}", e)
    }
    breakable {
      while (rafObj.getFilePointer < stop) {
        val packetStart = rafObj.getFilePointer
        rafObj.skipBytes(8)
        val lengths = rafObj.readInt32(2)
        if (lengths(0).equals(lengths(1)) // additional check
          && 41.to(65535).contains(lengths(0))) {
          rafObj.seek(packetStart)
          if (packetStart + PcapHeaderLen + lengths(0) > rafObj.length) {
            log.error("Packet exceeded file size")
            break()
          }
          val packet = rafObj.readByte(PcapHeaderLen + lengths(0))
          packets = ReadPacket(ByteString.fromArray(packet), packetStart) :: packets
        }
        else {
          log.error("Wrong packet at position {}", packetStart)
          try {
            rafObj.seek(packetStart)
            seekToPacketRecord(rafObj)
          }
          catch {
            case e: Exception =>
              log.error("Cannot recover, skipping block {}", e)
              break()
          }
        }
      }
    }
    packets
  }

  private def readFileChunk(chunk: FileChannel, start: Long, stop: Long): List[ReadPacket] = {
    chunk.position(stop)
    seekToPacketRecord(chunk)
    val realStop = chunk.position()
    chunk.position(start)
    seekToPacketRecord(chunk)
    val realStart = chunk.position()
    val buffer = ByteBuffer.allocate((realStop - realStart).toInt)
    chunk.read(buffer)
    buffer.rewind()
    val result = splitPackets(ByteString.fromByteBuffer(buffer), realStart)
    chunk close()
    result
  }

  private def splitPackets(chunk: ByteString, start: Long): List[ReadPacket] = {
    var packets = List[ReadPacket]()
    val it = chunk.iterator
    val PcapHeaderLen = 16
    var packetCount = 0
    var currentPosition = 0
    while (it.hasNext) {
      val itClone = it.clone()
      itClone.drop(12)
      val length = itClone.getInt(ByteOrder.LITTLE_ENDIAN)
      packets = ReadPacket(it.getByteString(PcapHeaderLen + length), start + currentPosition) :: packets
      currentPosition = currentPosition + PcapHeaderLen + length
    }
    packets
  }
}
