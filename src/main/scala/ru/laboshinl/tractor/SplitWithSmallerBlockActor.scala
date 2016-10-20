package ru.laboshinl.tractor

import java.io.File

import akka.actor._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.routing._

class SplitWithSmallerBlockActor extends Actor with ActorLogging {
  implicit val timeout = Timeout(1000 seconds)
  val cores = Runtime.getRuntime.availableProcessors()
  val worker = context.actorOf(RoundRobinPool(cores * 2).props(Props[ProcessFileBlockActor]))

  val bs = ConfigFactory.load().getInt("tractor.block.size")

  override def receive: Actor.Receive = {
    case FileBlock(file, start, stop) =>
      val recipient = sender()
      val listOfFutures = splitFile(file, start, stop, bs * 1024 * 1024 ).map(akka.pattern.ask(worker, _))
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
    case _ =>
      log.error("SplitWithBs: unknown message ")
  }

  private def splitFile(file: File, start: Long, stop: Long, bs: Long): List[FileBlock] = {
    val count = math.ceil((stop - start).toFloat / bs).toInt
    1.to(count).foldLeft(List[FileBlock]()) { (splits, i) =>
      val end: Long = if (i.equals(count)) stop else bs * i + start
      FileBlock(file, bs * (i - 1) + start, end) :: splits
    }
  }
}


