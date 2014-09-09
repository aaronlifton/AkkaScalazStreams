import akka.actor.{Actor, ActorRef, ActorSystem, Props, _}
import akka.pattern.{after, ask, pipe}
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, _}
import scala.util.{Failure, Success}

import scalaz.\/
import scalaz.\/._
import scalaz.concurrent.Task

case object JobDoneMessage

case object JobRequestMessage

case class JobReq(worktimes: List[Int], timeout: FiniteDuration, async: Boolean)

case object JobStopMessage

case class JobStatusMessage(message: String)

/**
 * Description: An Akka Actor example
 * Author: Aaron Lifton
 */

object Util {
  def time[A](a: => A): (A,Long) = {
    val now = System.nanoTime
    val result = a
    val micros = (System.nanoTime - now) / 1000
    // println("%d microseconds".format(micros))
    (result,micros)
  }
}
class Job(jobTracker: ActorRef) extends Actor {
  var count = 0
  def perform {count += 1; println("doing work")}

  /*

    We can also produce a stream by wrapping some callback-based
    API. For instance, here's a hypothetical API that we'd rather
    not use directly:

  */
  def asyncReadInt(callback: Throwable \/ Int => Unit): Unit = {
    // imagine an asynchronous task which eventually produces an `Int`
    try {
      Thread.sleep(50)
      val result = (math.random * 100).toInt
      callback(right(result))
    } catch { case t: Throwable => callback(left(t)) }
  }

  /* We can wrap this in `Task`, which forms a `Monad` */
  val intTask: Task[Int] = Task.async(asyncReadInt)

  def doWork(worktime: Int): Future[String] = future {
    //    Thread sleep worktime
    val (result,micros) = Util.time(intTask.run)
    val totaltime = micros // worktime + micros
    s"found $result in $totaltime ms"
  }

  def nonAsyncReadInt: Int = {(math.random * 100).toInt}

  def doWorkWithoutTask(worktime: Int): Future[String] = future {
    //    Thread sleep worktime
    val (result,micros) = Util.time(nonAsyncReadInt)
    val totaltime = micros // worktime + micros
    s"found $result in $totaltime ms"
  }

  def receive = {
    case JobRequestMessage =>
      perform
      jobTracker ! JobDoneMessage
    case JobStatusMessage =>
      val c = count
      jobTracker ! JobStatusMessage(s"count is $c")
    case JobStopMessage =>
      context.stop(self)
    case JobReq(worktimes, timeout, async) => {
      val searchFutures = worktimes map { worktime =>
        val searchFuture = if(async == true) {
          doWork(worktime)
        } else {
          doWorkWithoutTask(worktime)
        }
        val fallback = after(timeout, context.system.scheduler) {
          Future successful s"$worktime ms > $timeout"
        }
        Future firstCompletedOf Seq(searchFuture, fallback)
      }

      // Pipe future results to sender
      (Future sequence searchFutures) pipeTo sender
    }
  }
}

class JobTracker extends Actor {
  def receive = {
    case JobStatusMessage(m) =>
      println(s"job status: $m")
    case JobDoneMessage =>
      println("--job done")
      sender ! JobStopMessage
      context.stop(self)
  }
}

object JobTest {
  // extends App
  def run = {
    println("running")
    val system = ActorSystem("JobSystem")
    val jobTracker = system.actorOf(Props[JobTracker], name = "jobTracker")
    val job = system.actorOf(Props(new Job(jobTracker)), name = "job")
    job ! JobRequestMessage
  }

  def runWithFutures(async: Boolean = true) = {
    val system = ActorSystem("futures")
    val jobTracker = system.actorOf(Props[JobTracker], name = "jobTracker")
    val job = system.actorOf(Props(new Job(jobTracker)), name="job")
    val worktimes = List(1000, 1500, 1200, 800, 2000, 600, 3500, 8000, 250)
    val fallbackTimeout = 2 seconds
    implicit val timeout = Timeout(5 seconds)

    val futureResults = (job ? JobReq(worktimes, fallbackTimeout, async))
      // Cast to correct type
      .mapTo[List[String]]
      // In case something went wrong
      .recover {
      case e: TimeoutException => List("timeout")
      case e: Exception => List(e getMessage)
    }
      // Callback (non-blocking)
      .onComplete {
      case Success(results) =>
        println(s":: Results(async=$async) ::")
        results foreach (r => println(s"  $r"))
        system shutdown()
      case Failure(t) =>
        t printStackTrace()
        system shutdown()
    }
  }

  def measure[A](a: => A) = {
    def time[A](a: => A) = {
      val now = System.nanoTime
      val result = a
      val micros = (System.nanoTime - now) / 1000
      println("%d microseconds".format(micros))
      result
    }
    time(a)
  }

  def measureTime = {
    val time1 = measure {
      runWithFutures(async = true)
    }
    println(s"Total time1: $time1")

    val time2 = measure {
      runWithFutures(async = false)
    }
    println(s"Total time2: $time2")
  }
}

object run extends App {
  import JobTest._
  runWithFutures(async=true)
  Thread sleep 500
  runWithFutures(async=false)
}

// scala> time(JobTest.runWithFutures(true))
// 10403 microseconds
// scala> time(JobTest.runWithFutures(false))
// 16085 microseconds