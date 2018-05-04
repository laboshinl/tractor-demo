package ru.laboshinl.tractor

import java.io.{PrintWriter, File, ObjectInputStream}
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
import better.files.{File => bFile}

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
  def getListOfFiles(dir: String):List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  val localAddress = InetAddress.getLocalHost.getHostAddress
  val options = nextOption(Map(), argList)

  val nWorkers = options.get('nWorkers).map(_.asInstanceOf[Int]).getOrElse(1)
  val inputFile = options.get('inFile).map(_.asInstanceOf[String]).getOrElse("")
  val seedNode = "akka.tcp://ClusterSystem@%s:2551".format(options.get('seedNode).map(_.asInstanceOf[String]).getOrElse(localAddress))
  val chunkSize = options.get('chunkSize).map(_.asInstanceOf[Int]).getOrElse(10)

  implicit val timeout = Timeout(1000 seconds)

  //val file = new File(inputFile)

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

    val f = bFile(inputFile)
    f.entries.filter(_.contentType.get == "application/vnd.tcpdump.pcap").filter(_.size < 1024*1024*1024).filter(_.name.contains("big")).filter(f => bFile(inputFile + f.nameWithoutExtension + ".ndpi").exists).filter(f => ! bFile(inputFile + f.nameWithoutExtension + ".csv").exists).foreach(file => {
      val startTime = System.currentTimeMillis()
      println("Processing file %s".format(file.name))
      val result = Await.result(akka.pattern.ask(waiter, file.toJava), 1000 second).asInstanceOf[BidirectionalFlows]
      /*examples*/
     // result.flows.filter(f => f._2.serverFlow.portSrc.equals(443)).foreach(f =>
          println(result.getSslHostStatistic(file.toJava,ports))
      //    println(result.getProtocolStatistics(ports))
      //    println(result.getClientIpStatistics)
      //    println(result.getServerIpStatistics)
      //    println(result.getContentStatistic(file,ports))
      //    println(result.getHostStatistic(file,ports))
      println("Total Flows", result.flows.size)
      result.flows.filter(f => f._2.getServerIp == "149.154.167.200").foreach(f => println(f._2.toString, f._1))
      /*Machine Learning*/
      //val lpi = SystemCmd.parseWithLpi(file.getAbsolutePath).filter(p => !Array[String]("HTTP","HTTPS", "HTTP_NonStandard", "Unknown_TCP", "Unsupported", "No_Payload", "Invalid", "Unknown_TCP").contains(p._2)) //detect proto with protoIdent
      val lpi = Dpi.parseWithLpi(inputFile + file.nameWithoutExtension + ".ndpi")
//      lpi.foreach( f =>
//      println(f._1))
//      println("===========================================")
//      result.flows.foreach( f =>
//      println(f._1)
//      )

      val filtered = result.flows.filter(f => lpi.isDefinedAt(f._1))
      println(filtered.size)
      val writer = new PrintWriter(new File(inputFile + file.nameWithoutExtension + ".csv"))
      filtered.foreach(f => {
        writer.println("%s,%s".format(f._2.computeFeatures().map((f: Double) => new java.text.DecimalFormat("#.###").format(f)).mkString(","),lpi.get(f._1).get /*,f._2.getProtoByPort(ports)*/))
      })

      writer.close()

      val time = System.currentTimeMillis() - startTime
      println("bs=%s, nW=%s, fileSize=%s MB, time=%s ms, speed=%s MB/s".format(chunkSize, nWorkers, file.size / 1024 / 1024, time, file.size / 1024 / time))
    })
    sys.exit(0)
  }
}
