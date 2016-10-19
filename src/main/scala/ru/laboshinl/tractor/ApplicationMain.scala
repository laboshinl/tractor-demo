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

  val nWorkers = options.get('nWorkers).map(_.asInstanceOf[Int]).getOrElse(10)
  val inputFile = options.get('inFile).map(_.asInstanceOf[String]).getOrElse("")
  val seedNode = "akka.tcp://ClusterSystem@%s:2551".format(options.get('seedNode).map(_.asInstanceOf[String]).getOrElse(localAddress))
  val chunkSize = options.get('chunkSize).map(_.asInstanceOf[Int]).getOrElse(10)

  implicit val timeout = Timeout(1000 seconds)

  val file = new File(inputFile)

  System.setProperty("akka.cluster.seed-nodes.0", seedNode)
  System.setProperty("akka.remote.netty.tcp.hostname", localAddress)

  val system = ActorSystem("ClusterSystem", ConfigFactory.load())

  val waiter = system.actorOf(
    ClusterRouterPool(RoundRobinPool(0), ClusterRouterPoolSettings(
      totalInstances = 1000, maxInstancesPerNode = 1,
      allowLocalRoutees = true, useRole = None)).props(Props(new SendWorkWaitResult(Props[SplitWithBs]))))

      //*************************************************
      var config2 = ConfigFactory.parseString("akka.remote.netty.tcp { port = 2553, bind-port = 2553}").withFallback(ConfigFactory.load())
      ActorSystem("ClusterSystem", config2)
      //*************************************************
      var config3 = ConfigFactory.parseString("akka.remote.netty.tcp { port = 2554, bind-port = 2554}").withFallback(ConfigFactory.load())
      ActorSystem("ClusterSystem", config3)
      //*************************************************


  val fis = getClass.getResourceAsStream("/ports.ser")
  val ois = new ObjectInputStream(fis)
  val ports = ois.readObject().asInstanceOf[scala.collection.mutable.Map[Int, String]]
  ois.close()

  scala.io.StdIn.readLine("Hit Return to start >")

  val routees = Await.result(akka.pattern.ask(waiter, GetRoutees).mapTo[Routees], 100 second)
  val nodesCount = routees.getRoutees.size()

  def splitFile(file: File, count: Int): List[FileBlock] = {
    val bs = math.ceil(file.length.toFloat / count).toLong
    1.to(count).foldLeft(List[FileBlock]()) { (splits, i) =>
      val stop: Long = if (i.equals(count)) file.length() else bs * i - 1
      FileBlock(file, bs * (i - 1), stop) :: splits
    }
  }

  val result = akka.pattern.ask(waiter, splitFile(file, nodesCount))
  val startTime = System.currentTimeMillis()
  result.onComplete {
    case Success(value) =>
      println(value.asInstanceOf[BidirectionalFlows].getClientIpStatistics)
      println("finished in %s".format(System.currentTimeMillis() - startTime))


    case Failure(e) =>
      println("Failed")
      sys.exit(1)
  }
  ////  while (true) {
  //
  //    val routees = Await.result(akka.pattern.ask(reader, GetRoutees).mapTo[Routees], 100 second)
  //    val nodesCount = routees.getRoutees.size()
  //
  //    val t0 = System.currentTimeMillis()
  //
  //    val splits = splitFile(file, nodesCount)
  //
  //    val aggregator = system.actorOf(Props[GlobalAggregateActor])
  //    splits.foreach((s: (Long, Long)) => reader tell(FileChunkWithBs(file, s._1, s._2, chunkSize, nWorkers), aggregator))
  //
  //    try {
  //
  //      val lpi = SystemCmd.parseWithLpi(inputFile).filter(p => ! Array[String]("HTTP", "Unknown_TCP", "Unsupported", "No_Payload", "Invalid", "Unknown_TCP").contains(p._2)) //detect proto with protoIdent
  //
  //      val res = Await.result(akka.pattern.ask(aggregator, splits.size).mapTo[BidirectionalFlows], ((file.length() / 1024 / 1024 / 15) + 5) second)
  //
  //      //res.getProtocolStatistics(ports) //Some Work
  //      val writer = new PrintWriter(new File("/tmp/%s.csv".format(file.getName) ))
  //
  //      val filtered = res.flows.filter(f => lpi.isDefinedAt(new java.text.DecimalFormat("#.######").format(f._2.getFlowStart/1000000)))
  //
  ////      println(lpi.groupBy(_._2).mapValues(_.size).toSeq.sortBy(-_._2))
  ////      println(lpi.groupBy(_._2).mapValues(_.size).toSeq.sortBy(-_._2).size)
  //
  //      var stat = collection.mutable.Map[String,Int]().withDefaultValue(0)
  //      filtered.filter(_._2.getProtoByPort(ports) != "http") foreach(f => {
  //        stat(lpi.get(new java.text.DecimalFormat("#.######").format(f._2.getFlowStart/1000000)).get) += 1
  //        writer.println("%s,%s".format(lpi.get(new java.text.DecimalFormat("#.######").format(f._2.getFlowStart/1000000)).get, f._2.computeFeatures().map( (f : Double) => new java.text.DecimalFormat("#.###").format(f)).mkString(",")/*,f._2.getProtoByPort(ports)*/))
  //      })
  //      writer.close()
  //
  //      println(stat.toSeq.sortBy(-_._2))
  //
  //      val takenTime = (System.currentTimeMillis() - t0).toFloat /1000
  //      val fileSizeMb = file.length().toFloat/1024/1024
  //      val throughput = (fileSizeMb / takenTime).toInt
  //
  //      println(s"Throughput = $throughput MB/s")
  //    } catch {
  //      case e: Exception => println(s"Something went wrong! $e")
  //    }
  //    aggregator ! PoisonPill
  ////  }
  //  //sys.exit(0)


}