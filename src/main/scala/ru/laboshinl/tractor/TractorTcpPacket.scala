package ru.laboshinl.tractor

import java.nio.ByteBuffer

/**
 * Created by laboshinl on 10/10/16.
 */
case class TractorTcpPacket(timestamp: Double = 0, ipSrc: Array[Byte] = Array(), portSrc: Int = 0, ipDst: Array[Byte] = Array(), portDst: Int = 0, seq: Long = 0,
                            tcpFlags: Array[Short] = Array(), payloadStart: Long = 0, payloadLen: Int = 0, length: Int = 0, sackBlocksCount: Short = 0) extends Serializable {
  override def toString: String = {
    s"$timestamp  $ipSrc:$portSrc -> $ipDst:$portDst  $length  %s".format(tcpFlags.toList)
  }

  def isServer = {
    portSrc < portDst
  }

  def isEmply = {
    length.equals(0)
  }

  def notEmply = {
    length > 0
  }

  def computeHash(): Long = {
    val a = ByteBuffer.allocate(8).put(ipSrc).putInt(portSrc).getLong(0)
    val b = ByteBuffer.allocate(8).put(ipDst).putInt(portDst).getLong(0)

    val d = Math.abs(a - b)
    val min = a + (d & d >> 63)
    val max = b - (d & d >> 63)

    max << 64 | min
  }
}
