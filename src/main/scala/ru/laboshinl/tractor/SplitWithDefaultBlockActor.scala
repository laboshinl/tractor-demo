package ru.laboshinl.tractor

import java.io.File

import akka.actor._
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class SplitWithDefaultBlockActor(router: ActorRef, bs: Int) extends Actor with ActorLogging {
  implicit val timeout = Timeout(1000 seconds)

  override def receive: Receive = {
    case file: File =>
      val recipient = sender()
      val listOfFutures = splitFile(file, 0, file.length(), bs * 1024 * 1024).map(akka.pattern.ask(router, _))
      listOfFutures.foreach { f =>
        f.onSuccess {
          case _ =>
            log.info("Processed {} MB block", bs)
        }
      }
      val futureSequence = Future sequence listOfFutures

      futureSequence.onSuccess {
        case m: List[BidirectionalFlows] =>
          recipient ! m.foldLeft(BidirectionalFlows()) { (acc, f) =>
            acc.concat(f)
          }
      }
    case m: Any =>
      log.error("SendWorkWaitResult: unknown message {}", m)
  }

  private def splitFile(file: File, start: Long, stop: Long, bs: Long): List[FileBlock] = {
    val count = math.ceil((stop - start).toFloat / bs).toInt
    1.to(count).foldLeft(List[FileBlock]()) { (splits, i) =>
      val end: Long = if (i.equals(count)) stop else bs * i + start
      FileBlock(file, bs * (i - 1) + start, end) :: splits
    }
  }
}