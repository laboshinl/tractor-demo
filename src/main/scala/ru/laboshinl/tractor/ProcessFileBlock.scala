package ru.laboshinl.tractor

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString

import scala.util.control.Breaks._

class ProcessFileBlock extends Actor with ActorLogging {
  val PcapHeaderLen = 16
  val waiter = context.actorOf(Props(new SendWorkWaitResult(Props[ReadPacketActor])))

  override def receive: Actor.Receive = {
    case FileBlock(file, start, stop) =>
      log.debug("ProcessFileBlock received FileBlock {}-{}", start, stop)
      var packets = List[ReadPacket]()
      val rafObj = new RandomAccessFile(file)(ByteConverterLittleEndian)
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
      waiter tell(packets, sender())
      log.debug("Packets sent")
    case _ =>
      log.error("unknown message")
  }

  @throws(classOf[java.io.EOFException])
  private def seekToPacketRecord(rafObj: RandomAccessFile): Unit = {
    breakable {
      while (rafObj.getFilePointer < rafObj.length) {
        var position = rafObj.getFilePointer
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

}
