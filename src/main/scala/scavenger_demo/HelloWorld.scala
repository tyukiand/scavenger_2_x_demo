package scavenger_demo
import scala.concurrent.Future
import scavenger._
import scavenger.app.DistributedScavengerApp

/**
 * Scavenger 2.x-specific hello-world example
 */
object HelloWorld extends DistributedScavengerApp {
  def main(args: Array[String]): Unit = {
    // Some data to work with.
    val encryptedMessage = "Uryyb, Jbeyq!"

    // An atomic algorithm that applies ROT13 to characters
    // (and prints some messages to the console)
    val rot13 = cheap("rot13"){ (c: Char) => 
      println("Decoding '" + c + "'")
      if ('A' <= c && c <= 'Z') ('A' + (c + 13 - 'A') % 26).toChar
      else if ('a' <= c && c <= 'z') ('a' + (c + 13 - 'a') % 26).toChar
      else c
    }

    // Each character becomes a trivial computation
    val data = for (character <- encryptedMessage) yield {
      Computation("" + character, character)
    }

    // Now we apply `rot13` algorithm to the data and 
    // generate bunch of jobs: one job for each character
    val jobs = for (trivialComp <- data) yield {
      rot13(trivialComp)
    }

    // Nothing has been computed so far. We have merely created
    // descriptions of jobs. Here is the full list of jobs that
    // we want to submit:
    println("Full list of jobs:")
    jobs foreach println

    // initialize the master node of the scavenger framework
    scavengerInit()

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
      println(str)
      scavengerShutdown()
    }

  }
}
