package ru.laboshinl.tractor

import java.io.{File, ObjectInputStream}
import java.net.InetAddress

import akka.actor._
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.routing._
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

object ApplicationMain extends App {
  val usage =
    """
    Usage: tractor [--chunk-size MB] [--num-workers num] [--seed-node IP] file
    """

  if (args.length == 0) {
    println(usage)
    sys.exit(1)
  }
  val arglist = args.toList
  type OptionMap = Map[Symbol, Any]

  def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = s(0) == '-'
    list match {
      case Nil => map
      case "--chunk-size" :: value :: tail =>
        nextOption(map ++ Map('chunkSize -> value.toInt), tail)
      case "--num-workers" :: value :: tail =>
        nextOption(map ++ Map('nWorkers -> value.toInt), tail)
      case "--seed-node" :: value :: tail =>
        nextOption(map ++ Map('seedNode -> value), tail)
      case string :: opt2 :: tail if isSwitch(opt2) =>
        nextOption(map ++ Map('inFile -> string), list.tail)
      case string :: Nil => nextOption(map ++ Map('inFile -> string), list.tail)
      case option :: tail =>
        println("Unknown option " + option)
        sys.exit(1)
    }
  }

  //val address = InetAddress.getLocalHost.getHostAddress
  val options = nextOption(Map(), arglist)

  val nWorkers = options.get('nWorkers).map(_.asInstanceOf[Int]).getOrElse(8)
  val filename = options.get('inFile).map(_.asInstanceOf[String]).getOrElse("")
  val seedNode = "akka.tcp://ClusterSystem@%s:2551".format(options.get('seedNode).map(_.asInstanceOf[String]).getOrElse(InetAddress.getLocalHost.getHostAddress))
  val chunkSize = options.get('chunkSize).map(_.asInstanceOf[Int]).getOrElse(1)
  /** 1024 * 1024 */

  implicit val timeout = Timeout(1000 seconds)

  val file = new File(filename)
  // println(s"Running program with arguments \nFile: $filename %s Mb. \nChunk size: %s Mb. \nNumber of workers per node: $nWorkers".format(file.length() / 1024 / 1024, chunkSize / 1024 / 1024))

  System.setProperty("akka.cluster.seed-nodes.0", seedNode)
  System.setProperty("akka.remote.netty.tcp.hostname", InetAddress.getLocalHost.getHostAddress)

  val system = ActorSystem("ClusterSystem", ConfigFactory.load())
  //  val reader = system.actorOf(
  //    ClusterRouterPool(BalancingPool(0), ClusterRouterPoolSettings(
  //      totalInstances = 1000, maxInstancesPerNode = 1,
  //      allowLocalRoutees = true, useRole = None)).props(Props[ReadFileChunk]))

  val reader = system.actorOf(
    ClusterRouterPool(RoundRobinPool(0), ClusterRouterPoolSettings(
      totalInstances = 1000, maxInstancesPerNode = 1,
      allowLocalRoutees = true, useRole = None)).props(Props[LocalWorker]))

  //  ///***********************************************
  //  var config = ConfigFactory.parseString("akka.remote.netty.tcp { port = 2553, bind-port = 2553}").withFallback(ConfigFactory.load())
  //  val system2 = ActorSystem("ClusterSystem", config)
  //  //*************************************************

  val routees = Await.result(akka.pattern.ask(reader, GetRoutees).mapTo[Routees], 100 second)
  val nodesCount = routees.getRoutees.size()

  //scala.io.StdIn.readLine()
  //  println("Start processing file %s".format(filename))

  val fis = getClass.getResourceAsStream("/ports.ser")
  val ois = new ObjectInputStream(fis)
  val ports = ois.readObject().asInstanceOf[scala.collection.mutable.Map[Int, String]]
  ois.close()

  val t0 = System.currentTimeMillis()
  //val splits = splitFile(file, size * chunkSize)
  val splits = splitFile(file, nodesCount)
  val aggregator = system.actorOf(Props[GlobalAggregator])
  splits.foreach((s: (Long, Long)) => reader tell(FileChunk2(file, s._1, s._2, chunkSize), aggregator))
  //try {
  val res = Await.result(akka.pattern.ask(aggregator, splits.size).mapTo[BidirectionalFlows], file.length() / 1024 / 1024 second)
  /////////////////////////////EXAMPLES///////////////////////////////////////////////////////
  //println(res.getProtocolStatistics(ports))
  println(res.flows.filter(_._2.getProtoByPort(ports) == "http").groupBy((p: (Long, BidirectionalTcpFlow)) => {
    val a = p._2.clientHttpHeadersAsMap(file)
    if (a nonEmpty)
      a.head.getOrElse("Host", "")
    else ""
  }).mapValues(_.size).toSeq.sortBy(-_._2))
  println(res.flows.filter(_._2.getProtoByPort(ports) == "http").groupBy((p: (Long, BidirectionalTcpFlow)) => {
    val a = p._2.serverHttpHeadersAsMap(file)
    if (a nonEmpty)
      a.head.getOrElse("Content-Type", "")
    else ""
  }).mapValues(_.size).toSeq.sortBy(-_._2))

  sys.exit(0)

  //        println(res.flows.groupBy(_._2.getProtoByPort(ports)).mapValues(_.size).toSeq.sortBy(- _._2))  //  Protocol Statistics
  //        println(res.flows.groupBy(_._2.getClientIp).mapValues(_.size).toSeq.sortBy(- _._2))  // Top Clients
  //        println(res.flows.groupBy(_._2.getServerIp).mapValues(_.size).toSeq.sortBy(- _._2))  // Top Servers
  //println(res.flows.groupBy(_._2.getServerFirstPacketSignature(file).map("%02X" format _).mkString).mapValues(_.size).toSeq.sortBy(- _._2))
  //println(res.flows.groupBy(_._2.getClientFirstPacketSignature(file).map("%02X" format _).mkString).mapValues(_.size).toSeq.sortBy(- _._2))

  //        res.flows.foreach((p: (Long,BidirectionalTcpFlow)) =>
  //          println(p._2.getClientFirstPacketSignature(file).map("%02X" format _).mkString, p._2.getServerFirstPacketSignature(file).map("%02X" format _).mkString,
  //        new String(p._2.getClientFirstPacketSignature(file), "UTF-8"), new String(p._2.getServerFirstPacketSignature(file), "UTF-8")))
  //println(res.flows.groupBy((p: (Long,BidirectionalTcpFlow)) => "%s:%s".format(p._2.getServerIp,  p._2.serverFlow.portSrc.toString)).mapValues(_.size).toSeq.sortBy(- _._2))  // Top Applications
  ////////////////////////////////////////////////////////////////////////////////////////////
  //res.flows.foreach((p: (Long,BidirectionalTcpFlow)) => println(p._2.getProtoByPort(ports)))
  //res.flows.filter((p: (Long,BidirectionalTcpFlow)) => p._2.getProtoByPort == "HTTP").filter((p: (Long,BidirectionalTcpFlow)) => p._2.serchClientData(file, "password")).foreach((p: (Long,BidirectionalTcpFlow)) => println(new String(p._2.extractClientData(file), Charset.forName("UTF8"))))
  //println("%s flows. Throughput = %s Mbit/s with bs = %sMb".format(res.flows.size, file.length() * 8 / 1024 / 1024 / ((System.currentTimeMillis() - t0) / 1000), chunkSize))
  //      }
  //      catch {
  //        case e: Exception =>
  //          println(e)
  //
  //      }
  //    }
  //println("%s Mb with block %s Mb in %s".format(file.length()/1024/1024, size * chunkSize/1024/1024, System.currentTimeMillis() - t0))

  //Await.result(akka.pattern.gracefulStop(reader, 20 seconds, Broadcast(PoisonPill)), 20 seconds)

  //    scala.io.StdIn.readLine()


  private def splitFile(file: File, nWorkers: Int): ListBuffer[(Long, Long)] = {
    // println(file.length())
    val size: Long = math.ceil(file.length.toDouble / nWorkers).toInt
    var splits = new ListBuffer[(Long, Long)]()
    for (i <- 0L to nWorkers - 1) {
      val start: Long = size * i
      val stop: Long = if (i.equals(nWorkers - 1)) file.length() else size * (i + 1) - 1
      splits += Tuple2(start, stop)
    }
    splits
  }

  private def splitFile(file: File, chunkSize: Long): ListBuffer[(Long, Long)] = {
    val chunksCount: Long = math.ceil(file.length.toDouble / chunkSize).toInt
    var splits = new ListBuffer[(Long, Long)]()
    for (i <- 0L until chunksCount) {
      val start: Long = chunkSize * i
      val stop: Long = if (i.equals(chunksCount - 1)) file.length() else chunkSize * (i + 1) - 1
      splits += Tuple2(start, stop)
    }
    splits
  }

}


case class FileChunk(file: File, start: Long, stop: Long)

case class FileChunk2(file: File, start: Long, stop: Long, bs: Int)

case class FileJob(file: File, chunkSize: Long, nWorkers: Int)

case class ReadPacket(packet: ByteString, filePosition: Long)
