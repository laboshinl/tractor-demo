package ru.laboshinl.tractor

import java.io.File
import java.nio.ByteBuffer

import akka.util.ByteString
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._

/**
 * Created by laboshinl on 10/13/16.
 */

case class Skipped() extends Serializable

case class FileChunk(file: File, start: Long, stop: Long, nWorkers : Int) extends Serializable

case class FileChunkWithBs(file: File, start: Long, stop: Long, bs: Int, nWorkers : Int) extends Serializable

case class FileJob(file: File, chunkSize: Long, nWorkers: Int) extends Serializable

case class ReadPacket(packet: ByteString, filePosition: Long) extends Serializable

case class HashedPacket(hash: Long, packet: TractorTcpPacket) extends Serializable {
  def notEmpty: Boolean = {
    hash != 0
  }
}

case class BidirectionalFlows(flows: Map[Long, BidirectionalTcpFlow] = Map[Long,BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())) extends Serializable {
  def getProtocolStatistics(ports: scala.collection.mutable.Map[Int, String]): Seq[(String, Int)] = {
    flows.groupBy(_._2.getProtoByPort(ports)).mapValues(_.size).toSeq.sortBy(-_._2)
  }

  def concat(flow : BidirectionalFlows) : BidirectionalFlows = {
    var temp = this.flows
    flow.flows.foreach(flow => temp = temp.updated(flow._1, temp(flow._1) ++ flow._2))
    BidirectionalFlows(temp)
  }

  def getServerIpStatistics: Seq[(String, Int)] = {
    flows.groupBy(_._2.getServerIp).mapValues(_.size).toSeq.sortBy(-_._2)
  }

  def getClientIpStatistics: Seq[(String, Int)] = {
    flows.groupBy(_._2.getClientIp).mapValues(_.size).toSeq.sortBy(-_._2)
  }
  //  println(res.flows.filter(_._2.getProtoByPort(ports) == "http").groupBy((p: (Long, BidirectionalTcpFlow)) => {
  //    val a = p._2.clientHttpHeadersAsMap(file)
  //    if (a nonEmpty)
  //      a.head.getOrElse("Host", "")
  //    else ""
  //  }).mapValues(_.size).toSeq.sortBy(-_._2))
  //  println(res.flows.filter(_._2.getProtoByPort(ports) == "http").groupBy((p: (Long, BidirectionalTcpFlow)) => {
  //    val a = p._2.serverHttpHeadersAsMap(file)
  //    if (a nonEmpty)
  //      a.head.getOrElse("Content-Type", "")
  //    else ""
  //  }).mapValues(_.size).toSeq.sortBy(-_._2))
  //  println(res.flows.groupBy(_._2.getProtoByPort(ports)).mapValues(_.size).toSeq.sortBy(- _._2))  //  Protocol Statistics
  //  println(res.flows.groupBy(_._2.getClientIp).mapValues(_.size).toSeq.sortBy(- _._2))  // Top Clients
  //  println(res.flows.groupBy(_._2.getServerIp).mapValues(_.size).toSeq.sortBy(- _._2))  // Top Servers
}



case class TractorTcpPacket(timestamp: Double = 0, ipSrc: Array[Byte] = Array(), portSrc: Int = 0, ipDst: Array[Byte] = Array(), portDst: Int = 0, seq: Long = 0,
                            tcpFlags: Array[Short] = Array(), payloadStart: Long = 0, payloadLen: Int = 0, length: Int = 0, sackBlocksCount: Short = 0) extends Serializable {
  override def toString: String = {
    s"$timestamp  $ipSrc:$portSrc -> $ipDst:$portDst  $length  %s".format(tcpFlags.toList)
  }

  def isServer = {
    portSrc < portDst
  }

  def isEmpty = {
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

case class TractorTcpFlow(timestamps: scala.collection.immutable.List[Double] = List[Double](), ipSrc: Array[Byte] = Array[Byte](), portSrc: Int = 0, ipDst: Array[Byte] = Array[Byte](), portDst: Int = 0, length: scala.collection.immutable.List[Int] = List[Int](), payloadLen: scala.collection.immutable.List[Int] = List[Int](), tcpFlags: Array[Int] = new Array[Int](9), ackSet: Int = 0, payloads: collection.immutable.TreeMap[Long, (Long, Int)] = new collection.immutable.TreeMap(), packetsWithSuck: Int = 0, packetsWithSuckAck: Int = 0, sackMax: Short = 0) extends Serializable {

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

case class BidirectionalTcpFlow(clientFlow: TractorTcpFlow = TractorTcpFlow(), serverFlow: TractorTcpFlow = TractorTcpFlow()) extends Serializable {
  def +(f: TractorTcpFlow): BidirectionalTcpFlow = {
    if (f.isSever)
      BidirectionalTcpFlow(this.clientFlow, this.serverFlow ++ f)
    else
      BidirectionalTcpFlow(this.clientFlow ++ f, this.serverFlow)
  }


  def addPacket(packet : TractorTcpPacket) : BidirectionalTcpFlow = {
    if (packet.isServer)
      BidirectionalTcpFlow(this.clientFlow, this.serverFlow + packet)
    else
      BidirectionalTcpFlow(this.clientFlow + packet, this.serverFlow)
  }

  def ++(f: BidirectionalTcpFlow): BidirectionalTcpFlow = {
    BidirectionalTcpFlow(this.clientFlow ++ f.clientFlow, this.serverFlow ++ f.serverFlow)
  }

  def getStartTime: Double = {
    (this.serverFlow.timestamps ::: this.clientFlow.timestamps).min
  }

  def computeFeatures(): ListBuffer[Double] = {
    val features = scala.collection.mutable.ListBuffer[Double]()
    //1-21*† Number of bytes in Ethernet packet
    features ++= computeStats(clientFlow.length)
    features ++= computeStats(serverFlow.length)
    features ++= computeStats(clientFlow.length ++ serverFlow.length)
    //22-42*† Number of bytes in IP packet  WTF?
    //43-63*† Number of bytes in IP and TCP headers
    val cl = (clientFlow.length, clientFlow.payloadLen).zipped.map(_ - _)
    val srv = (serverFlow.length, serverFlow.payloadLen).zipped.map(_ - _)
    features ++= computeStats(cl)
    features ++= computeStats(srv)
    features ++= computeStats(cl ++ srv)
    //64-65 Number of packets
    features += clientFlow.payloads.size
    features += serverFlow.payloads.size
    //66-67 Number of packets with TCP ack flag set
    features += clientFlow.tcpFlags(3)
    features += serverFlow.tcpFlags(3)
    //68-69 Number of packets with only the ack flag set
    features += clientFlow.ackSet
    features += serverFlow.ackSet
    //70-71 Number of packets with TCP optional SACK Blocks
    features += clientFlow.packetsWithSuck
    features += serverFlow.packetsWithSuck
    //72-73 Max number of SACK blocks in a single packet
    features += clientFlow.sackMax
    features += serverFlow.sackMax
    //74-75 Number of packets with ack flag set and SACK information
    features += clientFlow.packetsWithSuckAck
    features += serverFlow.packetsWithSuckAck
    //76-77 Number of packets with TCP payloads
    features += clientFlow.payloads.count((x: (Long, (Long, Int))) => x._2._2 > 0)
    features += serverFlow.payloads.count((x: (Long, (Long, Int))) => x._2._2 > 0)
    //78-79 Number of combined bytes within TCP payloads
    features += clientFlow.payloads.foldLeft(0)(_ + _._2._2)
    //80-81 Number of packets with the TCP push flag set
    features += clientFlow.tcpFlags(4)
    features += serverFlow.tcpFlags(4)
    //82-83 Number of packets with TCP syn flag set
    features += clientFlow.tcpFlags(6)
    features += serverFlow.tcpFlags(6)
    //84-85 Number of packets with fin flag set
    features += clientFlow.tcpFlags(7)
    features += serverFlow.tcpFlags(7)
    //86-87 Was a packet sent allowing SACK blocks (Value is Y or N)
    features += clientFlow.tcpFlags(8)
    features += serverFlow.tcpFlags(8)
    //88-89 Number of packets with TCP urgent flag set
    features += clientFlow.tcpFlags(2)
    features += serverFlow.tcpFlags(2)
    //90-91 Number of combined bytes within packets that have urgent flag set
    features.map((f : Double) => if( f.isNaN) 0 else f)
  }

  override def toString: String = {
    clientFlow.toString
  }

  def getClientFirstPacketSignature(file: File): Array[Byte] = {
    clientFlow.getFirstPacketSignature(file)
  }

  def getServerFirstPacketSignature(file: File): Array[Byte] = {
    serverFlow.getFirstPacketSignature(file)
  }

  def getServerIp : String ={
    ipToString(serverFlow.ipSrc)
  }

  def getClientIp : String ={
    ipToString(serverFlow.ipDst)
  }

  private def ipToString(ip: Array[Byte]): String = {
    if (ip.length.equals(4))
      "%s.%s.%s.%s".format(ip(0) & 0xFF, ip(1) & 0xFF, ip(2) & 0xFF, ip(3) & 0xFF)
    else
      "no.ip.address"
  }

  def extractHttpFiles(file: File): ListBuffer[ByteString] = {
    var files = ListBuffer[ByteString]()
    var data = ByteString.fromArray(serverFlow.extractData(file))
    breakable {
      while (true) {
        val beginningOfHeader = data.indexOfSlice(ByteString.fromArray(Array[Byte](0x48, 0x54, 0x54, 0x50, 0x2f))) //HTTP-
        if(beginningOfHeader.equals(-1)) break()
        val (previous, next) = data.splitAt(beginningOfHeader)
        if (previous.nonEmpty)
          files += previous
        val endOfHeader = next.indexOfSlice(ByteString.fromArray(Array[Byte](0x0d, 0x0a, 0x0d, 0x0a)))
        if(endOfHeader.equals(-1)) break()
        data = next.splitAt(endOfHeader + 4)._2
      }
    }
    files
  }

  def extractServerHttpHeaders(file: File): ListBuffer[ByteString] = {
    var files = ListBuffer[ByteString]()
    var data = ByteString.fromArray(serverFlow.extractData(file))
    //var beginningOfHeader = 0
    breakable {
      while (true) {
        val beginningOfHeader = data.indexOfSlice(ByteString.fromArray(Array[Byte](0x48, 0x54, 0x54, 0x50, 0x2f))) //HTTP-
        val endOfHeader = data.indexOfSlice(ByteString.fromArray(Array[Byte](0x0d, 0x0a, 0x0d, 0x0a)))
        if (beginningOfHeader.equals(-1) || endOfHeader.equals(-1))
          break()
        files += data.slice(beginningOfHeader, endOfHeader)
        data = data.splitAt(endOfHeader + 4)._2
      }
    }
    files
  }

  def extractClientHttpHeaders(file: File): ListBuffer[ByteString] = {
    var files = ListBuffer[ByteString]()
    var data = ByteString.fromArray(clientFlow.extractData(file))
    //var beginningOfHeader = 0
    breakable {
      while (true) {
        val beginningOfHeader = data.indexOfSlice(ByteString.fromArray(Array[Byte](0x48, 0x54, 0x54, 0x50, 0x2f))) //HTTP-
        val endOfHeader = data.indexOfSlice(ByteString.fromArray(Array[Byte](0x0d, 0x0a, 0x0d, 0x0a)))
        if (beginningOfHeader.equals(-1) || endOfHeader.equals(-1))
          break()
        files += data.slice(beginningOfHeader, endOfHeader)
        data = data.splitAt(endOfHeader + 4)._2
      }
    }
    files
  }

  def clientHttpHeadersAsMap(file : File) : List[Map[String, String]] = {
    var result = List[Map[String,String]]()
    val headers = extractClientHttpHeaders(file)
    if ( headers.nonEmpty) {
      headers.foreach((f: ByteString) => {
        var m = Map[String,String]().withDefaultValue("")
        val options = f.utf8String.split("\\r?\\n")
        if(options.nonEmpty) {
          options.foreach((s: String) => {
            val k = s.split(": ")
            if (k.size == 2)
              m = m.updated(k.head, k.tail.head)
          })
        }
        result = m :: result
      })
    }
    result
  }

  def serverHttpHeadersAsMap(file : File) : List[Map[String, String]] = {
    var result = List[Map[String,String]]()
    val headers = extractServerHttpHeaders(file)
    if ( headers.nonEmpty) {
      headers.foreach((f: ByteString) => {
        var m = Map[String,String]().withDefaultValue("")
        val options = f.utf8String.split("\\r?\\n")
        if(options.nonEmpty) {
          options.foreach((s: String) => {
            val k = s.split(": ")
            if (k.size == 2)
              m = m.updated(k.head, k.tail.head)
          })
        }
        result = m :: result
      })
    }
    result
  }

  def getProtoByPort(ports : collection.mutable.Map[Int,String]): String = {
    val proto = ports(serverFlow.portSrc)
    proto
  }

  def getFlowStart: Double = {
    (clientFlow.timestamps ++ serverFlow.timestamps).min
  }

  def extractClientData(file: File): Array[Byte] = {
    clientFlow.extractData(file)
  }

  def extractServerData(file: File): Array[Byte] = {
    serverFlow.extractData(file)
  }

  def searchServerData(file: File, string: akka.util.ByteString): Boolean = {
    akka.util.ByteString.fromArray(serverFlow.extractData(file)).containsSlice(string)
  }

  def searchClientData(file: File, string: akka.util.ByteString): Boolean = {
    akka.util.ByteString.fromArray(clientFlow.extractData(file)).containsSlice(string)
  }

  def searchSeverData(file: File, string: String): Boolean = {
    akka.util.ByteString.fromArray(serverFlow.extractData(file)).containsSlice(akka.util.ByteString.fromString(string, "utf-8"))
  }

  def searchClientData(file: File, string: String): Boolean = {
    akka.util.ByteString.fromArray(clientFlow.extractData(file)).containsSlice(akka.util.ByteString.fromString(string, "utf-8"))
  }

  private def computeStats(list: List[Int]): ListBuffer[Double] = {
    val result = scala.collection.mutable.ListBuffer[Double]()
    val dStat = new DescriptiveStatistics()
    list.foreach(x => {
      dStat.addValue(x.toDouble)
    })
    result += dStat.getMin
    result += dStat.getPercentile(25)
    result += dStat.getMean
    result += dStat.getPercentile(50)
    result += dStat.getPercentile(85)
    result += dStat.getMax
    result += dStat.getVariance
    result
  }
  def printFeatures = {
    println(computeFeatures().mkString(","))
  }
}