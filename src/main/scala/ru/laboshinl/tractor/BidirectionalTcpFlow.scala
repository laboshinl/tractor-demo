package ru.laboshinl.tractor

import java.io.{ObjectInputStream, File}

import akka.util.ByteString
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.util.control.Breaks._
/**
 * Created by laboshinl on 10/10/16.
 */
case class BidirectionalTcpFlow(clientFlow: TractorTcpFlow = TractorTcpFlow(), serverFlow: TractorTcpFlow = TractorTcpFlow()) {
  def +(f: TractorTcpFlow): BidirectionalTcpFlow = {
    if (f.isSever)
      BidirectionalTcpFlow(this.clientFlow, this.serverFlow ++ f)
    else
      BidirectionalTcpFlow(this.clientFlow ++ f, this.serverFlow)
  }

  def ++(f: BidirectionalTcpFlow): BidirectionalTcpFlow = {
    BidirectionalTcpFlow(this.clientFlow ++ f.clientFlow, this.serverFlow ++ f.serverFlow)
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
    features
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
}

