package ru.laboshinl.tractor

import java.io.File
import java.net.InetAddress
import java.nio.file.{Path, Paths, Files}
import java.util.concurrent.Future

import akka.actor._
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.routing._
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object ApplicationMain extends App {
  val usage =
    """
    Usage: tracktor [--chunk-size MB] [--num-workers num] [--seed-node IP] file
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
        nextOption(map ++ Map('seedNode -> value.toString), tail)
      case string :: opt2 :: tail if isSwitch(opt2) =>
        nextOption(map ++ Map('inFile -> string), list.tail)
      case string :: Nil => nextOption(map ++ Map('inFile -> string), list.tail)
      case option :: tail => {
        println("Unknown option " + option)
        sys.exit(1)
      }
    }
  }

  val options = nextOption(Map(), arglist)

  val nWorkers = options.get('nWorkers).map(_.asInstanceOf[Int]).getOrElse(8)
  val filename = options.get('inFile).map(_.asInstanceOf[String]).getOrElse("")
  val seedNode = "akka.tcp://ClusterSystem@%s:2551".format(options.get('seedNode).map(_.asInstanceOf[String]).getOrElse(InetAddress.getLocalHost.getHostAddress))
  val chunkSize = options.get('chunkSize).map(_.asInstanceOf[Int]).getOrElse(1) * 1024 * 1024

  implicit val timeout = Timeout(1000 seconds)

  val file = new File(filename)
  println(s"Running program with arguments \nFile: $filename %s Mb. \nChunk size: %s Mb. \nNumber of workers per node: $nWorkers".format(file.length() / 1024 / 1024, chunkSize / 1024 / 1024))

  System.setProperty("akka.cluster.seed-nodes.0", seedNode)
  System.setProperty("akka.remote.netty.tcp.hostname", InetAddress.getLocalHost.getHostAddress)

  val system = ActorSystem("ClusterSystem", ConfigFactory.load())
//  val reader = system.actorOf(
//    ClusterRouterPool(BalancingPool(0), ClusterRouterPoolSettings(
//      totalInstances = 1000, maxInstancesPerNode = 1,
//      allowLocalRoutees = true, useRole = None)).props(Props[ReadFileChunk]))

    val reader = system.actorOf(
      ClusterRouterPool(BalancingPool(0), ClusterRouterPoolSettings(
        totalInstances = 1000, maxInstancesPerNode = 1,
        allowLocalRoutees = true, useRole = None)).props(Props[LocalWorker]))

//  ///***********************************************
//  var config = ConfigFactory.parseString("akka.remote.netty.tcp { port = 2553, bind-port = 2553}").withFallback(ConfigFactory.load())
//  val system2 = ActorSystem("ClusterSystem", config)
//  //*************************************************

  val routees = Await.result(akka.pattern.ask(reader, GetRoutees).mapTo[Routees], 100 second)
  val nodesCount = routees.getRoutees.size()

  scala.io.StdIn.readLine()
  println("Start processing file %s".format(filename))

  10.to(64).foreach { (size : Int) =>
    val t0 = System.currentTimeMillis()
    //val splits = splitFile(file, size * chunkSize)
    val splits = splitFile(file, nodesCount)
    val aggregator = system.actorOf(Props[GlobalAggregator])
    splits.foreach((s: (Long, Long)) => reader tell(FileChunk(file, s._1, s._2), aggregator))
    try{
      val res = Await.result(akka.pattern.ask(aggregator, splits.size).mapTo[BidirectionalFlows], 100 second)
      println(res.flows.size)
    }
    catch {
      case e: Exception =>
        println(e)

    }
    //println("%s Mb with block %s Mb in %s".format(file.length()/1024/1024, size * chunkSize/1024/1024, System.currentTimeMillis() - t0))
    println("throughput = %s Mbit/s".format(file.length()*8/1024/1024/((System.currentTimeMillis() - t0)/1000)))
    //Await.result(akka.pattern.gracefulStop(reader, 20 seconds, Broadcast(PoisonPill)), 20 seconds)

    //    scala.io.StdIn.readLine()
  }

  private def splitFile(file: File, nWorkers : Int): ListBuffer[(Long,Long)] = {
   // println(file.length())
    val size : Long = math.ceil(file.length.toDouble / nWorkers).toInt
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
    for (i <- 0L to chunksCount - 1) {
      val start: Long = chunkSize * i
      val stop: Long = if (i.equals(chunksCount - 1)) file.length() else chunkSize * (i + 1) - 1
      splits += Tuple2(start, stop)
    }
    splits
  }

}


case class FileChunk(file: File, start: Long, stop: Long)

case class FileJob(file: File, chunkSize: Long, nWorkers: Int)

case class ReadPacket(packet: ByteString, filePosition: Long)
