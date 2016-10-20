package ru.laboshinl.tractor

import java.io.{File, FileInputStream}
import java.nio.channels.FileChannel
import java.nio.{ByteBuffer, ByteOrder}

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString

import scala.io.{BufferedSource, Source}
import scala.util.control.Breaks._

class ProcessFileBlockActor extends Actor with ActorLogging {
  val PcapHeaderLen = 16

  override def receive: Actor.Receive = {
    case FileBlock(file, start, stop) =>
      log.debug("ProcessFileBlock received FileBlock {}-{}", start, stop)
      val chunk = new RandomAccessFile(file)(ByteConverterLittleEndian)
      sender ! readFileChunkPackets(chunk,start,stop)
      chunk.close
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


  @throws(classOf[java.io.EOFException])
  private def readFileChunkPackets(rafObj: RandomAccessFile, start: Long, stop: Long ) : BidirectionalFlows = {
    var flows = BidirectionalFlows()
    try {
      rafObj.seek(start)
      seekToPacketRecord(rafObj)
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
            try {
              val packet = readPacket(rafObj)
              if (packet.nonEmpty) {
                val hash = packet.computeHash()
                flows = flows.addPacket(hash, packet)
              }
            }
            catch {
              case e: Exception =>
                log.error("Cannot read packet {}", e)
                break()
            }
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
    }
    catch {
      case e: Exception =>
        log.error("Processing block failed, {}", e)
    }
    flows
  }

  @throws(classOf[java.io.EOFException])
  private def readPacket(file: RandomAccessFile): TractorTcpPacket = {
    var packet = TractorTcpPacket()
    /*Eth header is always 14 bytes long*/
    val EthHeaderLen = 14
    /* PCap timestamp */
    val timestamp = file.readInt * 1000000D + file.readInt
    /* Original length */
    file.skipBytes(4)
    /* Included length */
    val packetLen = file.readInt()
    /* --- End of PCap Header --- */
    val currentPosition = file.getFilePointer
    /* MAC addresses */
    val macSrc = file.readByte(6)
    val macDst = file.readByte(6)
    /* ethernet type */
    val etherType = file.readUnsignedShort()
    /* IP packet */
    if (etherType.equals(8)) {
      val ipHeaderLen = computeIpHeaderLength(file.readByte())

      file.skipBytes(1) // DSCP
      file.skipBytes(2) // Total Len
      file.skipBytes(2) // ID
      file.skipBytes(2) // Flags, Offset
      file.skipBytes(1) // TTL

      /* ip protocol */
      val protocol = file.readUnsignedByte()
      /* --- TCP  --- */
      if (protocol.equals(6)) {
        file.skipBytes(2) //check
        val ipSrc = file.readByte(4)
        val ipDst = file.readByte(4)
        val portSrc = file.readUnsignedBShort()
        val portDst = file.readUnsignedBShort()
        val seq = file.readUnsignedBInt32()
        val ack = file.readUnsignedBInt32()
        val tcpHeaderLen = file.readUnsignedByte() / 4
        /* TCP flags */
        val flagsAsByte = file.readByte()
        var tcpFlags = new Array[Short](9)
        tcpFlags(8) = 0
        0.to(7).foreach((x: Int) => tcpFlags(7 - x) = (flagsAsByte >> x & 1).toShort)
        /* window */
        val window = file.readShort()
        file.skipBytes(2) //Checksum
        file.skipBytes(2) //Urgent Pointer
        var tcpOptionsSize = tcpHeaderLen - 20
        /* Find sack option */
        //sackPermitted: Short = 0
        var sackBlocksCount = 0.toShort
        if (tcpOptionsSize > 0) {
          var currentPosition = file.getFilePointer
          val stop = currentPosition + tcpOptionsSize
          while (currentPosition < stop) {
            val kind = file.readUnsignedByte()
            currentPosition = file.getFilePointer
            if (kind.equals(4)) {
              if (file.readUnsignedByte().equals(2))
                tcpFlags(8) = 1
              else file.seek(currentPosition)
            }
            else if (kind.equals(5)) {
              val len = file.readUnsignedByte()
              if (len.equals((stop - currentPosition + 1).toInt)) {
                sackBlocksCount = ((len - 2) / 8).toShort
              }
              else
                file.seek(currentPosition)
            }
          }
        }
        val packetHeadersLen = EthHeaderLen + ipHeaderLen + tcpHeaderLen
        val payloadLen = packetLen - packetHeadersLen
        packet = TractorTcpPacket(timestamp, ipSrc, portSrc, ipDst, portDst,
          seq, tcpFlags, currentPosition + packetHeadersLen, payloadLen, packetLen, sackBlocksCount)
      }
    }
    file.seek(currentPosition + packetLen) //skip non ip packet
    packet
  }

  private def computeIpHeaderLength(byte: Byte): Int = {
    var res = 0D
    0.to(3).foreach((shift: Int) => if ((byte >> shift & 1).equals(1)) res += math.pow(2, shift))
    res.toInt * 4
  }
}
