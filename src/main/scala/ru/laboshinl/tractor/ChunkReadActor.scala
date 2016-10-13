package ru.laboshinl.tractor

import java.io.File

import akka.actor._
import akka.routing.{Broadcast, RoundRobinPool}
import akka.util.ByteString

import scala.language.postfixOps
import scala.util.control.Breaks._


class ChunkReadActor extends Actor with ActorLogging {
  var nWorkers = 1
  override def receive: Receive = {
    case FileChunk(file, start, stop, workers) =>
      nWorkers = workers
      readFileChunk(file, start, stop)
  }

  private def readFileChunk(file: File, start: Long, stop: Long): Unit = {
    val chunk = new RandomAccessFile(file)(ByteConverterLittleEndian)
    chunk.seek(start)
    seekToFirstPacketRecord(chunk)
    splitPackets(chunk, stop)
    chunk close()
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

  private def splitPackets(chunk: RandomAccessFile, stop: Long): Unit = {
    val aggregator = context.actorOf(RoundRobinPool(nWorkers).props(Props(new LocalAggregateActor(sender()))))
    // val aggregator = context.actorOf(Props(new LocalAggregateActor(sender())))
    val PcapHeaderLen = 16
    var packetCount = 0
    breakable {
      while (chunk.getFilePointer < stop) {
        val currentPosition = chunk.getFilePointer
        chunk.skipBytes(12)
        val packetLen = chunk.readInt()
        chunk.seek(currentPosition)
        if (41.to(65535).contains(packetLen)) {
          context.actorOf(Props[ReadPacketActor]) tell(ReadPacket(ByteString.fromArray(chunk.readByte(packetLen + PcapHeaderLen)), currentPosition + PcapHeaderLen), aggregator)
          packetCount += 1
        }
        else {
          log.error("Error checking next packet size at position {}, trying to recover. Already finished packets {}", currentPosition, packetCount)
          seekToFirstPacketRecord(chunk)
        }
      }
    }
    aggregator ! Broadcast(packetCount)
  }
}
