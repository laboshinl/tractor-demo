package ru.laboshinl.tractor

import java.io.File

import akka.actor._
import akka.routing.{RandomPool, RoundRobinPool, BalancingPool}
import akka.util.Timeout

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by laboshinl on 10/10/16.
 */
class SplitActor extends Actor {
  implicit val timeout = Timeout(1000 seconds)

  override def receive: Actor.Receive = {
    case FileJob(file, chunkSize, nWorkers) =>

      val reader = context.actorOf(RandomPool(nWorkers).props(Props[ReadFileChunk]), "reader")

      val t0 = System.currentTimeMillis()

      val splits = splitFile(file, chunkSize)

      val listOfFutures = ListBuffer[Future[Map[Long,BidirectionalTcpFlow]]]()
      splits.foreach((s: (Long, Long)) => listOfFutures += akka.pattern.ask(reader, FileChunk(file, s._1, s._2)).mapTo[Map[Long, BidirectionalTcpFlow]])

      val aggregator = scala.collection.mutable.Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())
      val result = Future.sequence(listOfFutures)

      result onSuccess {
        case res =>
         // val myResult = res.foldLeft(Map[Long,BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())) { (map1, map2) => map1.merged(map2)({ case ((k,v1),(_,v2)) => (k,v1+v2) })}
         // map1 ++ map2.map{ case (k,v) => k -> (v + map1.getOrElse(k,0)) }
          res.foreach((f: Map[Long, BidirectionalTcpFlow]) => {
           // println(f.size)
            f.foreach((x: (Long, BidirectionalTcpFlow)) => aggregator(x._1) ++= x._2)
          })
//          val map = filteredResult.foldLeft(Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())) { (m, p) => m(p.computeHash()) + (TractorTcpFlow() + p); m  }
//          val map = res.foldLeft(Map[Long,BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())) {(m, f) => m(f)}
          println("total flows %s".format(aggregator.size))
          println(System.currentTimeMillis() - t0)
      }
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
