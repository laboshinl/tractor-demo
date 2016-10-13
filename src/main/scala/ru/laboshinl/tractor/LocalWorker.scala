package ru.laboshinl.tractor

import java.io.File

import akka.actor.{Props, ActorRef, Actor}
import akka.actor.Actor.Receive
import akka.routing.{RoundRobinPool, SmallestMailboxPool, BalancingPool}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ListBuffer

/**
 * Created by laboshinl on 10/11/16.
 */
class LocalWorker extends Actor {
  var replyTo = ActorRef.noSender
//  var blockSize = System.getProperty("tractor.block.size").toInt //ConfigFactory.load().getInt("tractor.block.size")
//  println(blockSize)
  val cores = Runtime.getRuntime.availableProcessors()
  val reader = context.actorOf(RoundRobinPool(cores*2).props(Props[ReadFileChunk]), "localreader")
  override def receive: Receive = {
    case FileChunk2(file, start, stop, size) =>
      replyTo = sender()
      val aggregator = context.actorOf(Props[GlobalAggregator])
      val splits = splitFile(start, stop, size * 1024 * 1024)
      aggregator ! splits.size
      splits.foreach{
        (split : (Long, Long)) => reader tell (FileChunk(file, split._1, split._2), aggregator)
      }
    case m : BidirectionalFlows =>
      replyTo forward m
  }

  private def splitFile(start : Long, stop : Long, chunkSize: Long): ListBuffer[(Long, Long)] = {
    val chunksCount: Long = math.ceil((stop-start).toDouble / chunkSize).toInt
    var splits = new ListBuffer[(Long, Long)]()
    for (i <- 0L until chunksCount) {
      val begin: Long = chunkSize * i
      val end: Long = if (i.equals(chunksCount - 1)) stop-start else chunkSize * (i + 1) - 1
      splits += Tuple2(start + begin, start + end)
    }
    //println(splits)
    splits
  }
}
