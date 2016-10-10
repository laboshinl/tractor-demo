package ru.laboshinl.tractor

import java.io.File

import akka.actor._
import akka.routing.{RandomPool, BalancingPool}
import akka.util.{ByteString, Timeout}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.Breaks._

/**
 * Created by laboshinl on 10/4/16.
 */

//class LocalAggregator extends Actor {
//  override def receive: Actor.Receive = {
//    case File
//  }
//}

class ReadFileChunk extends Actor {

  //val packetReader = context.actorOf(RandomPool(100).props(Props[ReadPacketActor]), "packetReader")

  implicit val timeout = Timeout(1000 seconds)

  override def receive: Receive = {
    case FileChunk(file, start, stop) =>
      val t0 = System.currentTimeMillis()
      val result = Await.result(readFileChunk(file, start, stop), 1000 seconds)
      println("reader await took", System.currentTimeMillis() - t0, result.size)
      sender ! result.filter((x: TractorTcpPacket) => x.notEmply).
        foldLeft(Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())) {
          (m, p) => val hash = p.computeHash(); m.updated(hash, m(hash) + (TractorTcpFlow() + p))
        }
  }

  private def seekToFirstPacketRecord(file: RandomAccessFile): Unit = {
    breakable {
      while (file.getFilePointer < file.length) {
        val position = file.getFilePointer
        val timestamp = file.readInt()
        file.skipBytes(4)
        val length = file.readInt32(2)
        if (length(0).equals(length(1)) && 41.to(65535).contains(length(0))) {
          file.skipBytes(length(0))
          if (0.to(600).contains(timestamp - file.readInt())) {
            file.seek(position)
            break()
          }
        } else file.seek(position + 1)
      }
    }
  }

  private def readFileChunk(file: File, start: Long, stop: Long): Future[ListBuffer[TractorTcpPacket]] = {
    val chunk = new RandomAccessFile(file)(ByteConverterLittleEndian)
    chunk.seek(start)
    seekToFirstPacketRecord(chunk)
    val packets = splitPackets(chunk, stop)
    chunk.close
    packets
  }

  private def splitPackets(chunk: RandomAccessFile, stop: Long): Future[ListBuffer[TractorTcpPacket]] = {
    val PcapHeaderLen = 16
    var listOfFutures = ListBuffer[Future[TractorTcpPacket]]()
    while (chunk.getFilePointer < stop) {
      val currentPosition = chunk.getFilePointer
      chunk.skipBytes(12)
      val packetLen = chunk.readInt()
      chunk.seek(currentPosition)
      listOfFutures += akka.pattern.ask(context.actorOf(Props[ReadPacketActor]), ReadPacket(ByteString.fromArray(chunk.readByte(packetLen + PcapHeaderLen)), currentPosition + PcapHeaderLen)).mapTo[TractorTcpPacket]
    }
    Future.sequence(listOfFutures)
  }
}
