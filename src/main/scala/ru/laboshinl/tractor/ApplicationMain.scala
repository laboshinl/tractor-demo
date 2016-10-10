package ru.laboshinl.tractor

import java.io.File
import java.net.InetAddress

import akka.actor._
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.routing.RandomPool
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

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
  val chunkSize = options.get('chunkSize).map(_.asInstanceOf[Int]).getOrElse(30) * 1024 * 1024

  implicit val timeout = Timeout(1000 seconds)

  val file = new File(filename)
  println(s"Running program with arguments \nFile: $filename %s Mb. \nChunk size: %s Mb. \nNumber of workers per node: $nWorkers".format(file.length() / 1024 / 1024, chunkSize / 1024 / 1024))

  System.setProperty("akka.cluster.seed-nodes.0", seedNode)
  System.setProperty("akka.remote.netty.tcp.hostname", InetAddress.getLocalHost.getHostAddress)

  val system = ActorSystem("ClusterSystem", ConfigFactory.load())

  scala.io.StdIn.readLine()
  println("Start processing file %s".format(filename))

  val reader = system.actorOf(
    ClusterRouterPool(RandomPool(0), ClusterRouterPoolSettings(
      totalInstances = 1000, maxInstancesPerNode = nWorkers,
      allowLocalRoutees = true, useRole = None)).props(Props[ReadFileChunk]),
    name = "reader")


  def job(t0 : Long, chunk :Long): Unit = {
    val splits = splitFile(file, chunk)

    val listOfFutures = ListBuffer[Future[Map[Long,BidirectionalTcpFlow]]]()
    splits.foreach((s: (Long, Long)) => listOfFutures += akka.pattern.ask(reader, FileChunk(file, s._1, s._2)).mapTo[Map[Long, BidirectionalTcpFlow]])

    //val aggregator = scala.collection.mutable.Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())

    val t1 = System.currentTimeMillis()
    val result = Await.result(Future.sequence(listOfFutures), 1000 seconds)
    println(chunk / 1024 / 1024, "takes to await", System.currentTimeMillis() - t1)
//    val t2 = System.currentTimeMillis()
//            result.foreach((f: Map[Long, BidirectionalTcpFlow]) => {
//              f.foreach((x: (Long, BidirectionalTcpFlow)) => aggregator(x._1) ++= x._2)
//            })
//    println("takes to merge", System.currentTimeMillis() - t2)
//    println("total flows %s".format(aggregator.size))
//    println("total time", System.currentTimeMillis() - t0)

//    result onSuccess {
//      case res =>
//        res.foreach((f: Map[Long, BidirectionalTcpFlow]) => {
//          f.foreach((x: (Long, BidirectionalTcpFlow)) => aggregator(x._1) ++= x._2)
//        })
//        println("total flows %s".format(aggregator.size))
//        println(System.currentTimeMillis() - t0)
//    }
    //aggregator.clear()
  }

  1.to(100).foreach((i : Int) => job(System.currentTimeMillis(), chunkSize + i * 1024 * 1024))


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
