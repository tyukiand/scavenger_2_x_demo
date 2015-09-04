package scavenger_demo
import scala.concurrent.Future
import scavenger._
import scavenger.app.LocalScavengerApp
import scala.concurrent.Future

case object Sqr extends AtomicAlgorithm[Int, Int] {
  def apply(i: Int, ctx: Context): Future[Int] = {
    // Thread.sleep((new scala.util.Random).nextInt(2000)) // pretend it's hard
    val res = i * i
    Future(res)(ctx.executionContext)
  }
  def identifier = scavenger.categories.formalccc.Atom("sqr")
  def difficulty = Expensive
}

/**
 * Scavenger 2.x-specific hello-world example,
 * with a single actor system simulating the entire cluster.
 */
object Local_debug extends LocalScavengerApp(2) {
  def main(args: Array[String]): Unit = {

    // before we do anything, we have to initialize the master node
    scavengerInit()

    // Each character becomes a trivial computation
    val data = for (i <- 1 to 3) yield {
      Computation("" + i, i)
    }

    // Now we apply `sqr` algorithm to the data and 
    // generate bunch of jobs: one job for each character
    val jobs = for (trivialComp <- data) yield {
      Sqr(trivialComp)
    }

    
    // submit all jobs
    val singleIntFutures = for (j <- jobs) yield {
      scavengerContext.submit(j)
    }
 
    // collect all futures into a single one, concatenate single chars
    val resultFuture = for (seq <-  Future.sequence(singleIntFutures)) yield {
      seq.sum
    }
    
    // install a callback on the result: print the result out
    resultFuture.onSuccess { case i: Int =>
      println(("#" * 80 + "\n") * 3 + "\n" + i)
      scavengerShutdown()
    }

  }
}
