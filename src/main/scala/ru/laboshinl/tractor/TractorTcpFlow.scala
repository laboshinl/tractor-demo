package ru.laboshinl.tractor

import java.io.File

/**
 * Created by laboshinl on 10/10/16.
 */

case class TractorTcpFlow(timestamps: scala.collection.immutable.List[Double] = List[Double](), ipSrc: Array[Byte] = Array[Byte](), portSrc: Int = 0, ipDst: Array[Byte] = Array[Byte](), portDst: Int = 0, length: scala.collection.immutable.List[Int] = List[Int](), payloadLen: scala.collection.immutable.List[Int] = List[Int](), tcpFlags: Array[Int] = new Array[Int](9), ackSet: Int = 0, payloads: collection.immutable.TreeMap[Long, (Long, Int)] = collection.immutable.TreeMap(), packetsWithSuck: Int = 0, packetsWithSuckAck: Int = 0, sackMax: Short = 0) {

  def +(p: TractorTcpPacket): TractorTcpFlow = {
    TractorTcpFlow(
      this.timestamps :+ p.timestamp,
      p.ipSrc,
      p.portSrc,
      p.ipDst,
      p.portDst,
      this.length :+ p.length,
      this.payloadLen :+ p.payloadLen,
      (this.tcpFlags, p.tcpFlags).zipped.map(_ + _),
      if ((p.tcpFlags(3) > 0) && tcpFlags.count(x => x > 0) == 1)
        this.ackSet + 1
      else
        this.ackSet,
      this.payloads + (p.seq ->(p.payloadStart, p.payloadLen)),
      if (p.sackBlocksCount > 0)
        this.packetsWithSuck + 1
      else this.packetsWithSuck,
      if (p.tcpFlags(3) > 0 && p.sackBlocksCount > 0)
        this.packetsWithSuckAck + 1
      else this.packetsWithSuckAck,
      this.sackMax.max(p.sackBlocksCount))
  }

  def ++(f: TractorTcpFlow): TractorTcpFlow = {
    TractorTcpFlow(
      this.timestamps ++ f.timestamps,
      f.ipSrc,
      f.portSrc,
      f.ipDst,
      f.portDst,
      this.length ++ f.length,
      this.payloadLen ++ f.payloadLen,
      (this.tcpFlags, f.tcpFlags).zipped.map(_ + _),
      this.ackSet + f.ackSet,
      this.payloads ++ f.payloads
    )
  }

  def isSever = {
    portSrc < portDst
  }

  def getFirstPacketSignature(file: File): Array[Byte] = {
    var data = Array[Byte](4)
    val zippedFlow = payloads.filter((p: (Long, (Long, Int))) => p._2._2 > 0)
    if (zippedFlow.nonEmpty) {
      val (key, value) = zippedFlow.head
      val rafObj = new RandomAccessFile(file)(ByteConverterLittleEndian)
      rafObj.seek(value._1)
      data = rafObj.readByte(4)
      rafObj.close
    }
    data
  }

  def extractData(file: File): Array[Byte] = {
    val rafObj = new RandomAccessFile(file)(ByteConverterLittleEndian)
    var data = Array[Byte]()
    payloads.foreach((p: (Long, (Long, Int))) => {
      rafObj.seek(p._2._1)
      data ++= rafObj.readByte(p._2._2)
    })
    rafObj.close
    data
  }

  override def toString: String = {
    s"$ipSrc:$portSrc -> $ipDst:$portDst, $timestamps $payloads $length $payloadLen %s".format(tcpFlags.toList)
  }
}
