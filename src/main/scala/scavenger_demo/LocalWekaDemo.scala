package scavenger_demo.local
import scala.concurrent.Future
import scavenger._
import scavenger.app.LocalScavengerApp

import java.io._
import java.util.Random

import weka.filters.Filter
import weka.filters.supervised.attribute.AttributeSelection
import weka.attributeSelection.ReliefFAttributeEval
import weka.attributeSelection.ASEvaluation
import weka.core.converters.ConverterUtils.DataSource;
import weka.attributeSelection.Ranker
import weka.classifiers.functions.SMO
import weka.classifiers.Classifier
import weka.core.Instances

/** 
 * An atomic algorithm that takes a training set and builds a 
 * `ReliefFAttributeEval`
 */
case class ReliefF(neighbours: Int) 
extends AtomicAlgorithm[Instances, ReliefFAttributeEval] {
  def apply(trainingSet: Instances, ctx: Context) = {
    val reliefEvaluator = new ReliefFAttributeEval()
    reliefEvaluator.setNumNeighbours(neighbours)
    reliefEvaluator.buildEvaluator(trainingSet)
    Future(reliefEvaluator)(ctx.executionContext)
  }
  def identifier = 
    scavenger.categories.formalccc.Atom("ReliefF[" + neighbours + "]")
  def difficulty = Expensive
}

/**
 * A cheap algorithm that applies the attribute selector to a dataset,
 * and returns a filtered dataset.
 */
case object SelectFeatures
extends AtomicAlgorithm[(Instances, ReliefFAttributeEval), Instances] {
  def apply(arg: (Instances, ReliefFAttributeEval), ctx: Context) = {
    val (data, mutable_reliefEvaluator) = arg
    
    // Weka has shared mutable state in the most unexpected places.
    // For example, if you don't clone this "reliefFEvaluator", you get
    // weird race conditions, and everything breaks.
    val reliefEvaluator = ASEvaluation.makeCopies(mutable_reliefEvaluator, 1)(0)
    
    val ranker = new Ranker()
    ranker.setNumToSelect(5)
    val attrSel = new AttributeSelection()
    attrSel.setInputFormat(data)
    attrSel.setSearch(ranker)
    attrSel.setEvaluator(reliefEvaluator)
    val filteredData = Filter.useFilter(data, attrSel)
    Future(filteredData)(ctx.executionContext)
  }
  def identifier = scavenger.categories.formalccc.Atom("select")
  def difficulty = Cheap
}

/**
 * A simple algorithm that evaluates the accuracy of a classifier
 */
case object EvalAccuracy
extends AtomicAlgorithm[(Instances, Classifier), Double] {
  def apply(arg: (Instances, Classifier), ctx: Context) = {
    val (testData, mutable_trainedClassifier) = arg
    val trainedClassifier = 
      MutableStateEliminator.copy(mutable_trainedClassifier)

    var total = 0
    var correct = 0

    for (i <- 0 until testData.numInstances) {
      val inst = testData.instance(i)
      val expected = inst.classValue()
      val predicted = trainedClassifier.classifyInstance(inst)
      if (expected == predicted) correct += 1
      total += 1
    }
    Future(correct.toDouble / total)(ctx.executionContext)
  }
  def identifier = scavenger.categories.formalccc.Atom("accuracy")
  def difficulty = Cheap
}

/** 
 * Algorithm that takes a dataset, and trains an SMO on it
 */
case class TrainSmo(c: Double) extends AtomicAlgorithm[Instances, SMO] {
  def apply(data: Instances, ctx: Context) = {
    val smo = new SMO()
    smo.setC(c)
    smo.buildClassifier(data)
    Future(smo)(ctx.executionContext)
  }
  def identifier = scavenger.categories.formalccc.Atom("smo[" + c + "]")
  def difficulty = Expensive
}

/** 
 * Loads `Instances` from file and
 * splits it into train/test subsets 
 * (a more elaborate version will be incorporated into the weka-compat-API)
 */
case class LoadTrainTest(filePath: String) 
extends ExplicitComputation[(Instances, Instances)] {
  def getExplicitValue(implicit ctx: scala.concurrent.ExecutionContext) = {
    val source = new DataSource(filePath)
    val data = source.getDataSet()
    data.setClassIndex(3 /*data.numAttributes() - 1 */)
    
    val randSeed = 42
    val folds = 5

    data.randomize(new Random(randSeed))
    val trainData = data.trainCV(folds, 1)
    val testData = data.testCV(folds, 1)

    Future((trainData, testData))(ctx)
  }
  def identifier = 
    scavenger.categories.formalccc.Atom("loadTrainTest[" + filePath + "]")
  def cachingPolicy = CachingPolicy.Nowhere
}

/**
 * Demonstrates training of a parameterized composite algorithm for
 * multiple parameter combinations. 
 */
object LocalWekaDemo extends LocalScavengerApp(4 /* num workers */) {
  /*
  def sizeOf(o: Any): Int = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(o)
    baos.toByteArray.size
  }
  */

  def main(args: Array[String]): Unit = {

    // before we do anything, we have to initialize the master node
    scavengerInit()

    // here are the lists of relevant parameters we want to explore
    // by a simple brute-force grid-search (just test every combination)
    val attributeSelectionParams = List[Int](5, 10, 50)
    val smoParams = List[Double](0.1, 0.5, 1.0)

    // load the dataset, split it into training/test, 
    // tell workers to cache both sets locally
    val trainTestData = LoadTrainTest("yeast.arff")
    val trainData = (Fst()(trainTestData)).cacheLocally
    val testData =  (Snd()(trainTestData)).cacheLocally

    // various ways to build a `ReliefFEvaluator`, the 
    // `numNeighbours` is different for each evaluator
    val reliefFBuilders = 
      for (numNeighbours <- attributeSelectionParams) yield {
        ReliefF(numNeighbours)
      }

    // here are the resources that represent the `ReliefFEvaluator`s.
    // We obtain them by applying the above algorithms to the training set.
    // We assume that this feature-selection is costly, so we tell the master
    // node to cache the intermediate result (`cacheGlobally`)
    val reliefFEvaluators = for (bldr <- reliefFBuilders) yield {
      bldr(trainData).cacheGlobally
    }

    // various ways to build an SMO: the constant `c` varies
    val smoBuilders = for (c <- smoParams) yield TrainSmo(c)

    // list of all jobs: we want to compute accuracies for every combination
    // of `ReliefF`s and `SMO`s.
    val allJobs = for {
      reliefEval <- reliefFEvaluators
      smoBldr <- smoBuilders
    } yield {
      val smo = smoBldr(SelectFeatures(trainData zip reliefEval))
      val acc = EvalAccuracy(SelectFeatures(testData zip reliefEval) zip smo)
      acc
    }
    
    // here is a list of all jobs. Notice that nothing has been
    // loaded or computed so far.
    println("List of all jobs:\n" + allJobs.mkString("\n"))

    // submit all jobs to the scavengerContext
    val resultFutures = for (j <- allJobs) yield {
      scavengerContext.submit(j)
    }
 
    // collect all futures into a single one, concatenate single chars
    val resultFuture = Future.sequence(resultFutures)
    
    // install a callback on the result: print it and shutdown as soon as
    // all results are there
    resultFuture.onSuccess { case accuracyList =>

      // just printing a table with results, in such
      // a way that it does not go under in a million of 
      // [info] and [debug] log-messages
      val paramCombis = 
        for (n <- attributeSelectionParams; c <- smoParams) yield (n, c)

      println(
        "\n" + ("#" * 100 + "\n") * 3 +
"""  -(((((((!|     (((((((((((       /(((!'!    (((((  -((((-  ((((((-        ((((((((((|     '!v((!'!    
    (v    ./J'     0       0     '%/   'v0     (v       0       F           0   (v   (v    vI'   'v0    
    (v      (!     0    !  *     !7      *     (v       0       F           '   (v   ..    A"      *    
    (&(((vvv/      0v(((0         "v(((/'      (v       0       F               (v          !v(((/'     
    (v   -vJ.      0    (              '!&'    (v       0       F      ""       (v               '!&'   
    (v     'J'     0       ((    &'      J!    /&      |%       F      (v       (v        |(       J!   
  -(&&((    '#v- ((0v((((((&v    8vv!|/(J(      |Jv/|!Iv     (((#((((((&v    '((&&((-     /7v(/|/(J(    
                                    '''.           '-'                                        '-'.      """ +
        "\n" + ("#" * 100 + "\n") * 3 +  
        "Results: " + "\n" +
        (for (
          ((n, c), a) <- paramCombis zip accuracyList
        ) yield {
          "neighbours = %d , c = %f, accuracy = %f".format(n,c,a)
        }).mkString("\n") + "\n" +
        ("#" * 100 + "\n") * 3
      )
      scavengerShutdown()
    }
  }
}


// Unfortunately, Weka has shared mutable state all over the place.
// If you want to do *anything*, you have to clone everything you 
// touch every single time, otherwise you will get the craziest race 
// conditions.
object MutableStateEliminator {
  def copy[X](x: X): X = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(x)
    val bytes = baos.toByteArray
    val bais = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bais)
    ois.readObject.asInstanceOf[X]
  }

  // this just prints two multi-line strings side-by-side, 
  // used this for debugging purposes.
  def diff(a: Any, b: Any, width: Int = 80): String = {
    val aStr = a.toString
    val bStr = b.toString
    (for ((aLine, bLine) <- (aStr.split("\n") zip bStr.split("\n"))) yield {
      val aPadded = aLine.padTo(width,' ')
      val bPadded = bLine.padTo(width,' ')
      aPadded + bPadded
    }).mkString("\n")
  }
}