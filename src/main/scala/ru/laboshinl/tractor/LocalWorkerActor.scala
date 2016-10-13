package ru.laboshinl.tractor

import akka.actor.{Actor, ActorRef, Props}
import akka.routing.RoundRobinPool

import scala.collection.mutable.ListBuffer

/**
 * Created by laboshinl on 10/11/16.
 */
class LocalWorkerActor extends Actor {
  var replyTo = ActorRef.noSender
  val cores = Runtime.getRuntime.availableProcessors()
  val reader = context.actorOf(RoundRobinPool(cores*2).props(Props[ChunkReadActor]), "localreader")
  override def receive: Receive = {
    case FileChunkWithBs(file, start, stop, size, nWorkers) =>
      replyTo = sender()
      val aggregator = context.actorOf(Props[GlobalAggregateActor])
      val splits = splitFile(start, stop, size * 1024 * 1024)
      aggregator ! splits.size * nWorkers
      splits.foreach{
        (split : (Long, Long)) => reader tell (FileChunk(file, split._1, split._2, nWorkers), aggregator)
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
    splits
  }
}
