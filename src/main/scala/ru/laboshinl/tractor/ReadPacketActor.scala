package ru.laboshinl.tractor

import java.nio.ByteOrder.{LITTLE_ENDIAN => LE, BIG_ENDIAN => BE}

import akka.actor._

/**
 * Created by laboshinl on 10/10/16.
 */

class ReadPacketActor extends Actor {
  val EthHeaderLen = 14

  private def computeIpHeaderLength(byte: Byte): Int = {
    var res = 0D
    0.to(3).foreach((shift: Int) => if ((byte >> shift & 1).equals(1)) res += math.pow(2, shift))
    res.toInt * 4
  }

  override def receive: Actor.Receive = {
    case r : ReadPacket/*(packet, filePosition)*/ =>
      var packet = r.packet
      val filePosition = r.filePosition
//      var tPacket = TractorTcpPacket()
      val it = packet.iterator
      val timestamp = it.getInt(LE) * 1000000D + it.getInt(LE)
      it.getBytes(4)
      /* Included length */
      val packetLen = it.getInt(LE)
      val macSrc = it.getBytes(6)
      val macDst = it.getBytes(6)
      val etherType = it.getShort(LE)
      if ((etherType & 0xFF).equals(8)) {
        val ipHeaderLen = computeIpHeaderLength(it.getByte) //Version, IHL
        it.getBytes(8)
        val proto = it.getByte & 0xFF
        it.getBytes(2) //check
        val ipSrc = it.getBytes(4)
        val ipDst = it.getBytes(4)
        if (proto.equals(6)) {
          val portSrc = it.getShort(BE) & 0xffff
          val portDst = it.getShort(BE) & 0xffff
          val seq = it.getInt(BE) & 0xffffffffl
          //58
          val ack = it.getInt(BE) & 0xffffffffl
          //62
          val tcpHeaderLen = (it.getByte & 0xFF) / 4

          val flagsAsByte = it.getByte
          val tcpFlags = new Array[Short](9)
          tcpFlags(8) = 0
          0.to(7).foreach((x: Int) => tcpFlags(7 - x) = (flagsAsByte >> x & 1).toShort)

          /*val window = */ it.getShort(LE)
          it.getBytes(4)
          val tcpOptionsSize = tcpHeaderLen - 20
          var sackBlocksCount = 0.toShort
          if (tcpOptionsSize > 0) {
            var counter = 2
            while (counter < tcpOptionsSize) {
              val kind = it.getByte & 0xFF
              var itCopy = it.clone()
              if (kind.equals(4)) {
                if ((itCopy.getByte & 0xFF).equals(2))
                  tcpFlags(8) = 1
                itCopy = it.clone()
              }
              else if (kind.equals(5)) {
                val len = itCopy.getByte & 0xFF
                if (len.equals(tcpOptionsSize - counter + 1)) {
                  sackBlocksCount = ((len - 2) / 8).toShort
                }
                else
                  itCopy = it.clone()
              }
              counter += 1
            }
          }
          val packetHeadersLen = EthHeaderLen + ipHeaderLen + tcpHeaderLen
          val payloadLen = packet.size - packetHeadersLen
          sender ! TractorTcpPacket(timestamp, ipSrc, portSrc, ipDst, portDst,
            seq, tcpFlags, filePosition + packetHeadersLen, payloadLen, packet.size, sackBlocksCount)
        } else sender ! TractorTcpPacket()
      } else sender ! TractorTcpPacket()
  }
}
