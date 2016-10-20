package ru.laboshinl.tractor

import java.io.{File, ObjectInputStream}
import java.net.InetAddress

import akka.actor._
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.routing._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ApplicationMain extends App {
  val usage =
    """
    Usage: tractor [--chunk-size MB] [--num-workers num] [--seed-node IP] file
    """

  if (args.length == 0) {
    println(usage)
    sys.exit(1)
  }

  val argList = args.toList
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
  val options = nextOption(Map(), argList)

  val nWorkers = options.get('nWorkers).map(_.asInstanceOf[Int]).getOrElse(1)
  val inputFile = options.get('inFile).map(_.asInstanceOf[String]).getOrElse("")
  val seedNode = "akka.tcp://ClusterSystem@%s:2551".format(options.get('seedNode).map(_.asInstanceOf[String]).getOrElse(localAddress))
  val chunkSize = options.get('chunkSize).map(_.asInstanceOf[Int]).getOrElse(10)

  implicit val timeout = Timeout(1000 seconds)

  val file = new File(inputFile)

  System.setProperty("akka.cluster.seed-nodes.0", seedNode)
  System.setProperty("akka.remote.netty.tcp.hostname", localAddress)
  System.setProperty("tractor.block.size", chunkSize.toString)

  val system = ActorSystem("ClusterSystem", ConfigFactory.load())

  if (options.get('seedNode).isEmpty) {
    val workers = system.actorOf(
      ClusterRouterPool(RoundRobinPool(0), ClusterRouterPoolSettings(
        totalInstances = 1000, maxInstancesPerNode = nWorkers,
        allowLocalRoutees = true, useRole = None)).props(Props[SplitWithSmallerBlockActor]))

////**************Second AS for testing**********************
//    ActorSystem("ClusterSystem", ConfigFactory.parseString("akka.remote.netty.tcp { port = 2552, bind-port = 2552}").withFallback(ConfigFactory.load()))
////**************Third  AS for testing**********************
//    ActorSystem("ClusterSystem", ConfigFactory.parseString("akka.remote.netty.tcp { port = 2553, bind-port = 2553}").withFallback(ConfigFactory.load()))
////*********************************************************


    val fis = getClass.getResourceAsStream("/ports.ser")
    val ois = new ObjectInputStream(fis)
    val ports = ois.readObject().asInstanceOf[scala.collection.mutable.Map[Int, String]]
    ois.close()

    scala.io.StdIn.readLine("Hit Return to start >")

    val waiter = system.actorOf(Props(new SplitWithDefaultBlockActor(workers, 64)))

    val startTime = System.currentTimeMillis()
    val result = akka.pattern.ask(waiter, file)

    result.onComplete {
      case Success(value) =>
        println("number of flows", value.asInstanceOf[BidirectionalFlows].flows.size)
        println("top talker", value.asInstanceOf[BidirectionalFlows].getClientIpStatistics.head)

      case Failure(e) =>
        println("Failed", e)
    }
    Await.result(result, 1000 second)

    val time = System.currentTimeMillis() - startTime
    println("bs=%s, nW=%s, fileSize=%s MB, time=%s s, speed=%s MB/s".format(chunkSize, nWorkers, file.length() / 1024 / 1024, time, file.length() / 1024 / time))
  }
}
