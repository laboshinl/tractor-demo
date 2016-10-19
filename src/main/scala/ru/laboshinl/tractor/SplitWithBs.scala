package ru.laboshinl.tractor

import java.io.File
import akka.actor._


class SplitWithBs extends Actor with ActorLogging {
  val waiter = context.actorOf(Props(new SendWorkWaitResult(Props[ProcessFileBlock])))

  override def receive: Actor.Receive = {
    case FileBlock(file, start, stop) =>
      log.debug("SplitWithBs received {} - {}", start, stop)
      val splits = splitFile(file, start, stop, 30 * 1024 * 1024)
      waiter tell(splits, sender())
      log.debug("SplitWithBs sent {}", splits)
  }

  private def splitFile(file: File, start: Long, stop: Long, bs: Long): List[FileBlock] = {
    val count = math.ceil((stop - start).toFloat / bs).toInt
    1.to(count).foldLeft(List[FileBlock]()) { (splits, i) =>
      val end: Long = if (i.equals(count)) stop else bs * i + start
      FileBlock(file, bs * (i - 1) + start, end) :: splits
    }
  }
}


