package ru.laboshinl.tractor

import java.io.{File, FileInputStream}
import java.nio.channels.FileChannel
import java.nio.{ByteBuffer, ByteOrder}

import akka.actor._
import akka.util.{ByteString, Timeout}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.Breaks._

case class BidirectionalFlows(flows: Map[Long, BidirectionalTcpFlow]) extends Serializable

case class HashedFlow(hash: Long, Flow: TractorTcpFlow) extends Serializable

class GlobalAggregator extends Actor {
  var replyTo = ActorRef.noSender
  //implicit val timeout = Timeout(5 seconds)
  //val t1 = System.currentTimeMillis()
  private var jobSize = Int.MaxValue
  private var counter = 0
  //  val counter = Agent(0)
  //  val jobSize = Agent(Int.MaxValue)

  var aggRes = Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())

  //val tick = context.system.scheduler.schedule(300 millis, 300 millis, self, "tick")

  override def receive: Actor.Receive = {
    case x: Int =>
      jobSize = x
      replyTo = sender()
      //jobSize send x
      check()
    case BidirectionalFlows(f) => //f : Map[Long, BidirectionalTcpFlow] =>
      f.foreach((x: (Long, BidirectionalTcpFlow)) => aggRes = aggRes.updated(x._1, aggRes(x._1) ++ x._2))
      counter += 1
      //counter send (_ + 1)
      check()

    //    case "tick" =>
    //      println("tick")
    //      check()
  }

  private def check(): Unit = {
    if (counter.equals(jobSize)) {
      //    val result = Future sequence List(counter.future, jobSize.future)
      //    result.onSuccess {
      //      case res =>
      //        if (res(0).equals(res(1))) {
      //println("Finished in", System.currentTimeMillis() - t1, "flows", aggRes.size)
      replyTo ! BidirectionalFlows(aggRes)
      context.stop(self)
    }
    //else println("%.2f %%"format(counter.toFloat/jobSize * 100))
    //}
  }
}

class LocalAggregator(replyTo: ActorRef) extends Actor {
  implicit val timeout = Timeout(5 seconds)
  //val jobStatus = List()
  private var jobSize = Int.MaxValue
  private var counter = 0
  //  val counter = Agent(0)
  //  val jobSize = Agent(Int.MaxValue)
  var aggRes = Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())

  //  val tick = context.system.scheduler.schedule(1000 millis, 1000 millis, self, "tick")
  override def receive: Actor.Receive = {
    case x: Int =>
      jobSize = x
      check()
    case HashedFlow(h, f) => //p: Tuple2[Long, TractorTcpFlow] =>
      counter += 1
      if (!h.equals(0L)) {
        aggRes = aggRes.updated(h, aggRes(h) + f)
      }
      check()
    //    case "tick" =>
    //      check()
  }

  private def check(): Unit = {
    if (counter.equals(jobSize)) {
      replyTo ! BidirectionalFlows(aggRes)
      context stop self
    }
  }

  //  private def check() : Unit = {
  //    val result = Future sequence List(counter.future, jobSize.future)
  //    result.onSuccess {
  //      case res =>
  //        if (res.head.equals(res(1))){
  //          replyTo ! aggRes
  //          context stop self
  //        }
  //    }
  //  }
}

class ReadFileChunk extends Actor {

  //val packetReader = context.actorOf(RandomPool(100).props(Props[ReadPacketActor]), "packetReader")

  implicit val timeout = Timeout(1000 seconds)

  override def receive: Receive = {
    case FileChunk(file, start, stop) =>
      readFileChunk(file, start, stop)
    //      val t0 = System.currentTimeMillis()
    //      val result = Await.result(readFileChunk(file, start, stop), 1000 seconds)
    //      println("reader await took", System.currentTimeMillis() - t0, result.size)
    //      sender ! result.filter((x: TractorTcpPacket) => x.notEmply).
    //        foldLeft(Map[Long, BidirectionalTcpFlow]().withDefaultValue(BidirectionalTcpFlow())) {
    //          (m, p) => val hash = p.computeHash(); m.updated(hash, m(hash) + (TractorTcpFlow() + p))
    //        }
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

  private def reedLeInt(file: FileChannel): Int = {
    val buff = ByteBuffer.allocate(4)
    file.read(buff)
    //buff.flip()
    buff.order(ByteOrder.LITTLE_ENDIAN).getInt(0)
  }

  private def seekToPacketRecord(file: FileChannel): Unit = {
    val PcapHeaderLen = 16
    breakable {
      while (file.position() < file.size()) {
        val position = file.position()
        val timestamp = reedLeInt(file)
        reedLeInt(file)
        val length = Array(reedLeInt(file), reedLeInt(file))
        if (length(0).equals(length(1)) && 41.to(65535).contains(length(0))) {
          file.position(position + PcapHeaderLen + length(0))
          if (0.to(600).contains(timestamp - reedLeInt(file))) {
            file.position(position)
            break()
          }
        } else file.position(position + 1)
      }
    }
  }

  private def readFileChunk(file: File, start: Long, stop: Long): Unit = {
    val chunk = new RandomAccessFile(file)(ByteConverterLittleEndian)
    chunk.seek(start)
    seekToFirstPacketRecord(chunk)
    splitPackets(chunk, stop)
    chunk close()
  }

  private def readFileChunk3(file: File, start: Long, stop: Long): Unit = {
    val chunk = new RandomAccessFile(file)(ByteConverterLittleEndian)
    chunk.seek(stop)
    seekToFirstPacketRecord(chunk)
    val realStop = chunk.getFilePointer

    chunk.seek(start)
    seekToFirstPacketRecord(chunk)
    val realStart = chunk.getFilePointer
    //val res = Array[Byte]()
    //println(realStart,realStop)

    if (realStart < realStop) {
      val res = chunk.readByte((realStop - realStart).toInt)
      splitPackets(ByteString.fromArray(res), start)
    }
    chunk close()
  }

  private def readFileChunk2(file: File, start: Long, stop: Long): Unit = {
    val chunk = new FileInputStream(file).getChannel
    chunk.position(stop)
    seekToPacketRecord(chunk)
    val realStop = chunk.position()

    chunk.position(start)
    seekToPacketRecord(chunk)
    val realStart = chunk.position()

    val buffer = ByteBuffer.allocate((realStop - realStart).toInt)
    chunk.read(buffer)
    buffer.rewind()
    splitPackets(ByteString.fromByteBuffer(buffer), realStart)
    //val byteChunk = ByteString.fromByteBuffer(buffer)
    //println(realStart, realStop - realStart, byteChunk.size)

    chunk close()
  }

  private def splitPackets(chunk: ByteString, start: Long): Unit = {
    val aggregator = context.actorOf(Props(new LocalAggregator(sender())))
    val it = chunk.iterator
    val PcapHeaderLen = 16
    var packetCount = 0
    var currentPosition = 0
    while (it.hasNext) {
      val itClone = it.clone()
      itClone.drop(12)
      val length = itClone.getInt(ByteOrder.LITTLE_ENDIAN)
      context.actorOf(Props[ReadPacketActor]) tell(ReadPacket(it.getByteString(PcapHeaderLen + length), start + currentPosition), aggregator)
      currentPosition = currentPosition + PcapHeaderLen + length
      packetCount += 1
    }
    aggregator ! packetCount
  }


  private def splitPackets(chunk: RandomAccessFile, stop: Long): Unit = {
    val aggregator = context.actorOf(Props(new LocalAggregator(sender())))
    val PcapHeaderLen = 16
    var packetCount = 0
    while (chunk.getFilePointer < stop) {
      val currentPosition = chunk.getFilePointer
      chunk.skipBytes(12)
      val packetLen = chunk.readInt()
      chunk.seek(currentPosition)
      context.actorOf(Props[ReadPacketActor]) tell(ReadPacket(ByteString.fromArray(chunk.readByte(packetLen + PcapHeaderLen)), currentPosition + PcapHeaderLen), aggregator)
      packetCount += 1
    }
    aggregator ! packetCount
  }
}
