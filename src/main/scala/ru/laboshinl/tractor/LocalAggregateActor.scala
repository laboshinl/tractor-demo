package ru.laboshinl.tractor
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._

import scala.language.postfixOps

/**
 * Created by laboshinl on 10/13/16.
 */
class LocalAggregateActor(replyTo: ActorRef) extends Actor {
  private val scheduled = new AtomicInteger(Int.MaxValue)
  private val completed = new AtomicInteger(0)
  var aggRes = Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())

  override def receive: Actor.Receive = {
    case x: Int =>
      scheduled.set(x)
      replyOnComplete()
    case Skipped =>
      completed.getAndIncrement()
      replyOnComplete()
    case HashedPacket(h, p) =>
      aggRes = aggRes.updated(h, aggRes(h).addPacket(p))
      //completed.getAndIncrement()
      //replyOnComplete()
  }

  private def replyOnComplete(): Unit = {
    if (completed.get().equals(scheduled.get())) {
      if (aggRes nonEmpty)
        replyTo ! BidirectionalFlows(aggRes)
      else replyTo ! Skipped
    }
  }
}
