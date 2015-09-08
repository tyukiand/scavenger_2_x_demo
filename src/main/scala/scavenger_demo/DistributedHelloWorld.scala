package scavenger_demo.distributed
import scala.concurrent.Future
import scavenger._
import scavenger.app.DistributedScavengerApp
import scala.concurrent.Future

// Rot13 atomic algorithm that accepts Chars and returns Chars
case object Rot13 extends AtomicAlgorithm[Char, Char] {
  def apply(c: Char, ctx: Context): Future[Char] = {
    // Thread.sleep((new scala.util.Random).nextInt(1000)) // pretend it's hard
    val res = if ('A' <= c && c <= 'Z') ('A' + (c + 13 - 'A') % 26).toChar
    else if ('a' <= c && c <= 'z') ('a' + (c + 13 - 'a') % 26).toChar
    else c
    Future(res)(ctx.executionContext)
  }
  def identifier = scavenger.categories.formalccc.Atom("rot13")
  def difficulty = Expensive
}

/**
 * Scavenger 2.x-specific hello-world example,
 * workers are in separate JVM's
 */
object DistributedHelloWorld extends DistributedScavengerApp {
  def main(args: Array[String]): Unit = {

    // before we do anything, we have to initialize the master node
    scavengerInit()

    // Here is some data to work with.
    val encryptedMessage = "Uryyb, Jbeyq!"

    // Each character becomes a trivial computation
    val data = for (character <- encryptedMessage) yield {
      Computation("" + character, character)
    }

    // Now we apply `rot13` algorithm to the data and 
    // generate bunch of jobs: one job for each character
    val jobs = for (trivialComp <- data) yield {
      Rot13(trivialComp)
    }

    // Nothing has been computed so far. We have merely created
    // descriptions of jobs. Here is the full list of jobs that
    // we want to submit:
    println("Full list of jobs:")
    jobs foreach println


    // submit all jobs, `Computation[Char]` are transformed into `Future[Char]`
    val singleCharFutures = for (j <- jobs) yield {
      scavengerContext.submit(j)
    }
 
    // collect all futures into a single one, concatenate single chars
    val resultFuture = for (seq <-  Future.sequence(singleCharFutures)) yield {
      seq.mkString("")
    }
    
    // install a callback on the result: print it and shutdown as soon as
    // the decrypted message is there
    resultFuture.onSuccess { case str: String =>
      // draw the result with a box around it
      val msg = "#" * 80 + "\n" + 
      "#" + (" " * 78) + "#" + "\n" + 
      "#" + (" " * ((78 - str.size) / 2)) + str + 
        (" " * (78 - (78 - str.size) / 2 - str.size)) + "#" + "\n" +
      "#" + (" " * 78) + "#" + "\n" + 
      "#" * 80
      println(msg)
      scavengerShutdown()
    }

  }
}
