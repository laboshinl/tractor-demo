package ru.laboshinl.tractor


import java.util.concurrent.atomic.AtomicInteger
import akka.actor._
import scala.language.postfixOps



/**
 * Created by laboshinl on 10/13/16.
 */
class GlobalAggregateActor extends Actor  with ActorLogging {
  var replyTo = ActorRef.noSender
  private val scheduled = new AtomicInteger(Int.MaxValue)
  private val completed = new AtomicInteger(0)

  //var aggRes = Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())
var aggRes = BidirectionalFlows()

  var prevCompleted = 0

  override def receive: Actor.Receive = {
    case x: Int =>
      scheduled.set(x)
      replyTo = sender()
      replyOnComplete()
    case Skipped =>
      completed.getAndIncrement()
      replyOnComplete()
    case f : BidirectionalFlows =>
      //f.foreach(flow => aggRes = aggRes.updated(flow._1, aggRes(flow._1) ++ flow._2))
      aggRes = aggRes.concat(f)
      completed.getAndIncrement()
      sender() ! PoisonPill
      replyOnComplete()
  }

  private def replyOnComplete(): Unit = {
    if (completed.get().equals(scheduled.get())) {
    //if((scheduled.get() - completed.get()) < 5 ) {
      if (aggRes.flows.nonEmpty)
        replyTo ! aggRes//BidirectionalFlows(aggRes)
      else replyTo ! Skipped
     // println()
    }
    else {
//      print("*")
      val percentCompleted = completed.get() * 100 / scheduled.get()
      if (/*(percentCompleted % 3).equals(0) && */percentCompleted > prevCompleted) {
        log.info("{} % completed", percentCompleted)
        prevCompleted = percentCompleted
      }
    }
  }
}
