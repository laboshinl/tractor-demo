package ru.laboshinl.tractor

import java.io.File

import akka.util.ByteString
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import scala.collection.mutable.ListBuffer

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

  def extractHttpFiles(file: File): ListBuffer[ByteString] = {
    var files = ListBuffer[ByteString]()
    var data = ByteString.fromArray(serverFlow.extractData(file))
    var beginningOfHeader = 0
    while (!beginningOfHeader.equals(-1)) {
      beginningOfHeader = data.indexOfSlice(ByteString.fromArray(Array[Byte](0x48, 0x54, 0x54, 0x50, 0x2f))) //HTTP-
      val (previous, next) = data.splitAt(beginningOfHeader)
      if (previous.nonEmpty)
        files += previous
      val endOfHeader = next.indexOfSlice(ByteString.fromArray(Array[Byte](0x0d, 0x0a, 0x0d, 0x0a)))
      data = next.splitAt(endOfHeader + 4)._2
    }
    files
  }

  def getProtoByPort: String = {
    val proto = serverFlow.portSrc match {
      case 1 => "TCPMUX"
      case 5 => "RJE"
      case 7 => "ECHO"
      case 18 => "MSP"
      case 20 => "FTP-Data"
      case 21 => "FTP-Control"
      case 22 => "SSH"
      case 23 => "Telnet"
      case 25 => "SMTP"
      case 29 => "MSG ICP"
      case 37 => "Time"
      case 42 => "Nameserv"
      case 43 => "WhoIs"
      case 49 => "Login"
      case 53 => "DNS"
      case 69 => "TFTP"
      case 70 => "Gopher"
      case 79 => "Finger"
      case 80 => "HTTP"
      case 103 => "X.400 Standard"
      case 108 => "SNA Gateway Access Server"
      case 109 => "POP2"
      case 110 => "POP3"
      case 115 => "SFTP"
      case 118 => "SQL Services"
      case 119 => "NNTP"
      case 137 => "NetBIOS Name Service"
      case 139 => "NetBIOS Datagram Service"
      case 143 => "IMAP"
      case 150 => "NetBIOS Session Service"
      case 156 => "SQL Server"
      case 161 => "SNMP"
      case 179 => "BGP"
      case 190 => "GACP"
      case 194 => "IRC"
      case 197 => "DLS"
      case 389 => "LDAP"
      case 396 => "Novell Netware over IP"
      case 443 => "HTTPS"
      case 444 => "SNPP"
      case 445 => "Microsoft-DS"
      case 458 => "Apple QuickTime"
      case 546 => "DHCP Client"
      case 547 => "DHCP Server"
      case 563 => "SNEWS"
      case 569 => "MSN"
      case 1080 => "Socks"
      case _ => "unknown"
    }
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

  def serchServerData(file: File, string: akka.util.ByteString): Boolean = {
    akka.util.ByteString.fromArray(serverFlow.extractData(file)).containsSlice(string)
  }

  def serchClientrData(file: File, string: akka.util.ByteString): Boolean = {
    akka.util.ByteString.fromArray(clientFlow.extractData(file)).containsSlice(string)
  }

  def serchSeverData(file: File, string: String): Boolean = {
    akka.util.ByteString.fromArray(serverFlow.extractData(file)).containsSlice(akka.util.ByteString.fromString(string, "utf-8"))
  }

  def serchClientData(file: File, string: String): Boolean = {
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
