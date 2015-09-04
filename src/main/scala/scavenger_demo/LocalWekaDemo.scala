/*
package scavenger_demo
import scala.concurrent.Future
import scavenger._
import scavenger.app.LocalScavengerApp

import java.io.BufferedReader
import java.io.FileReader

import weka.core.Instances
import weka.classifiers.functions.SMO

/** 
 * Builds an SMO with grid-search optimized metaparameter `C`.
 * Demonstrates how a computation can spawn further computations.
 */
case object SmoBuilder extends AtomicAlgorithm[Instances, SMO] {
  def apply(insts: Instances, ctx: Context): Future[SMO] = {

    // Range of `C`-values we want to test
    val GridSteps = 10
    val GridMin = 2
    val GridMax = 8
    val grid = for (i <- 0 to GridSteps) yield {
      val c = (GridMax - GridMin).toDouble / GridSteps * i + GridMin
      Computation("" + c, c)
    }

    // Now for each `C`-value, we generate a job
    val jobs = for (c <- grid) yield {
      SmoParamTest(data zip c)
    }

    // submit all jobs, obtain bunch of `Future[Double]`, which will 
    // eventually contain the performances for each `C`-value.
    // Prepend the position on the grid, so that we get tuples
    // with `(parameter, performance)`.
    val resultFutures = for ((j, g) <- jobs zip grid) yield {
      scavengerContext.submit(j).map {
        case performanceMeasure => (g, performanceMeasure)
      }
    }
 
    // select the best grid point
    val bestParamFuture = for (seq <- Future.sequence(resultFutures)) yield {
      val (bestParam, bestPerformance) = seq.sortBy(_._2).last
      bestParam
    }

    // build smo with best parameter
    bestParamFuture.map{ case c => 
      val finalSmo = new weka.classifiers.functions.SMO();
      // set options
      finalSmo.setOptions(weka.core.Utils.splitOptions(
        "-C %d -L 0.0010 -P 1.0E-12 -N 0".format(c) + 
        "-V -1 -W 1 -K " +
        "\"weka.classifiers.functions.supportVector.PolyKernel" + 
        " -C 250007 -E 1.0\""
      ))
      finalSmo.buildClassifier(insts)
      finalSmo
    }
  }
  def identifier = scavenger.categories.formalccc.Atom("BuildSmo")
  def difficulty = Expensive
}

/**
 * This algorithm takes instances and a candidate `C`-parameter 
 * for an SMO, trains a support vector machine with this parameter,
 * and then returns the resulting accuracy.
 */
case object SmoParamTest extends AtomicAlgorithm[(Instances, Double), Double] {
  def apply(smoComplexityParam: Double, ctx: Context): Future[Double] = {
    val res = math.random
    Future(res)(ctx.executionContext)
  }
  def identifier = scavenger.categories.formalccc.Atom("SmoCTest")
  def difficulty = Expensive
}

/**
 * Demo that performs a distributed grid-search for optimal SMO parameters
 * (SMO is a support vector machine implementation from Weka)
 */
object LocalWekaDemo extends LocalScavengerApp(4) {
  def main(args: Array[String]): Unit = {

    // before we do anything, we have to initialize the master node
    scavengerInit()

    // the following trivial computation simply loads a dataset
    val data = expensive[Unit, Instances]("loadInst") { case (Unit, Context) => 
      val reader = new BufferedReader(new FileReader("yeast.arff"))
      val instances = new Instances(reader)
      reader.close()
      // setting class attribute
      instances.setClassIndex(instances.numAttributes() - 1)
      instances
    }

    // formal description of the job: build an SMO from the data
    val smo = SmoBuilder(data)

    // submit the job, obtain a future that eventually returns the SMO
    val smoFuture = scavengerContext.submit(smo)

    // install a callback on the result: once the SMO is there, 
    // print it's description and shut everything down
    smoFuture.onSuccess { case x =>
      "#" * 80 + "\n" +
      "Result: " + x + "\n" +
      "#" * 80 + "\n"
      scavengerShutdown()
    }

  }
}
*/