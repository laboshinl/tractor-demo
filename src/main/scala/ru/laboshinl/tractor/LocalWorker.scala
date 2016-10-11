package ru.laboshinl.tractor

import java.io.File

import akka.actor.{Props, ActorRef, Actor}
import akka.actor.Actor.Receive

import scala.collection.mutable.ListBuffer

/**
 * Created by laboshinl on 10/11/16.
 */
class LocalWorker extends Actor {
  var replyTo = ActorRef.noSender
  override def receive: Receive = {
    case FileChunk(file, start, stop) =>
      replyTo = sender()
      val aggregator = context.actorOf(Props[GlobalAggregator])
      val splits = splitFile(start, stop, 20 * 1024 * 1024)
      aggregator ! splits.size
      splits.foreach{
        (split : (Long, Long)) => context.actorOf(Props[ReadFileChunk]) tell (FileChunk(file, split._1, split._2), aggregator)
      }
    case m : BidirectionalFlows =>
      replyTo forward m
  }

  private def splitFile(start : Long, stop : Long, chunkSize: Long): ListBuffer[(Long, Long)] = {
    val chunksCount: Long = math.ceil((stop-start).toDouble / chunkSize).toInt
    var splits = new ListBuffer[(Long, Long)]()
    for (i <- 0L to chunksCount - 1) {
      val begin: Long = chunkSize * i
      val end: Long = if (i.equals(chunksCount - 1)) stop-start else chunkSize * (i + 1) - 1
      splits += Tuple2(start + begin, start + end)
    }
    //println(splits)
    splits
  }
}
