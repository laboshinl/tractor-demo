package ru.laboshinl.tractor

import akka.actor.{Actor, ActorLogging, Props}
import akka.routing.RoundRobinPool
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

class SendWorkWaitResult(props: Props) extends Actor with ActorLogging {
  val cores = Runtime.getRuntime.availableProcessors()
  val worker = context.actorOf(RoundRobinPool(cores).props(props))
  implicit val timeout = Timeout(100 seconds)

  override def receive: Receive = {
    case m: Iterable[Any] =>
      log.info("SendWorkWaitResult received {} {}", m.head.getClass, m.size)
      val recipient = sender()
      val listOfFutures = m.map(akka.pattern.ask(worker, _))
      log.info("SendWorkWaitResult send to {}", props.actorClass())
      val futureSequence = Future sequence listOfFutures
      futureSequence.onSuccess {
        case res: List[Any] =>
          if (res.head.getClass.getCanonicalName.equals("ru.laboshinl.tractor.HashedPacket")) {
            recipient ! BidirectionalFlows(res.map(_.asInstanceOf[HashedPacket]).filter(_.notEmpty)
              .foldLeft(Map[Long,BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow()))((r, i) => {
              r.updated(i.hash, r(i.hash).addPacket(i.packet))
            }))
          }
          else {
            recipient ! res.map(_.asInstanceOf[BidirectionalFlows]).foldLeft(BidirectionalFlows())((r, i) => {
              r.concat(i)
            })
          }
      }
  }
}
