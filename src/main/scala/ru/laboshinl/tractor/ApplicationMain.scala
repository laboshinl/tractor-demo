package ru.laboshinl.tractor

import java.io.{File, ObjectInputStream}
import java.net.InetAddress

import akka.actor._
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.routing._
import akka.util.Timeout
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

  val localAddress = InetAddress.getLocalHost.getHostAddress
  val options = nextOption(Map(), arglist)

  val nWorkers = options.get('nWorkers).map(_.asInstanceOf[Int]).getOrElse(10)
  val inputFile = options.get('inFile).map(_.asInstanceOf[String]).getOrElse("")
  val seedNode = "akka.tcp://ClusterSystem@%s:2551".format(options.get('seedNode).map(_.asInstanceOf[String]).getOrElse(localAddress))
  val chunkSize = options.get('chunkSize).map(_.asInstanceOf[Int]).getOrElse(10)

  implicit val timeout = Timeout(1000 seconds)

  val file = new File(inputFile)

  System.setProperty("akka.cluster.seed-nodes.0", seedNode)
  System.setProperty("akka.remote.netty.tcp.hostname", localAddress)

  val system = ActorSystem("ClusterSystem", ConfigFactory.load())

  val reader = system.actorOf(
    ClusterRouterPool(RoundRobinPool(0), ClusterRouterPoolSettings(
      totalInstances = 1000, maxInstancesPerNode = 1,
      allowLocalRoutees = true, useRole = None)).props(Props[LocalWorkerActor]))

//    ///***********************************************
//    var config2 = ConfigFactory.parseString("akka.remote.netty.tcp { port = 2553, bind-port = 2553}").withFallback(ConfigFactory.load())
//    ActorSystem("ClusterSystem", config2)
//    //*************************************************
//    var config3 = ConfigFactory.parseString("akka.remote.netty.tcp { port = 2554, bind-port = 2554}").withFallback(ConfigFactory.load())
//    ActorSystem("ClusterSystem", config3)
//  //*************************************************



  val fis = getClass.getResourceAsStream("/ports.ser")
  val ois = new ObjectInputStream(fis)
  val ports = ois.readObject().asInstanceOf[scala.collection.mutable.Map[Int, String]]
  ois.close()

  scala.io.StdIn.readLine("Hit Return to start >")

  while (true) {

    val routees = Await.result(akka.pattern.ask(reader, GetRoutees).mapTo[Routees], 100 second)
    val nodesCount = routees.getRoutees.size()

    val t0 = System.currentTimeMillis()

    val splits = splitFile(file, nodesCount)

    val aggregator = system.actorOf(Props[GlobalAggregateActor])
    splits.foreach((s: (Long, Long)) => reader tell(FileChunkWithBs(file, s._1, s._2, chunkSize, nWorkers), aggregator))

    try {
      val res = Await.result(akka.pattern.ask(aggregator, splits.size).mapTo[BidirectionalFlows], ((file.length() / 1024 / 1024 / 15) + 5) second)

      res.getProtocolStatistics(ports) //Some Work

      val takenTime = (System.currentTimeMillis() - t0).toFloat /1000
      val fileSizeMb = file.length().toFloat/1024/1024
      val throughput = (fileSizeMb / takenTime).toInt

      println(s"Throughput = $throughput MB/s")
    } catch {
      case e: Exception => println(s"Something went wrong! $e")
    }
    aggregator ! PoisonPill
  }
  //sys.exit(0)


  private def splitFile(file: File, nWorkers: Int): ListBuffer[(Long, Long)] = {
    val size: Long = math.ceil(file.length.toDouble / nWorkers).toInt
    var splits = new ListBuffer[(Long, Long)]()
    for (i <- 0L to nWorkers - 1) {
      val start: Long = size * i
      val stop: Long = if (i.equals(nWorkers - 1)) file.length() else size * (i + 1) - 1
      splits += Tuple2(start, stop)
    }
    splits
  }

}