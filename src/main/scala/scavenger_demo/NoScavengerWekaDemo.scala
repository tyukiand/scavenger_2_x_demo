// Prototype of scavenger + weka demo, but without the scavenger part.
// 
// Selecting attributes and training an SMO on a dataset.

import weka.filters.Filter
import weka.filters.supervised.attribute.AttributeSelection
import weka.attributeSelection.ReliefFAttributeEval
import weka.core.converters.ConverterUtils.DataSource;
import weka.attributeSelection.Ranker
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import weka.classifiers.functions.SMO
import java.util.Random
import weka.classifiers.Classifier
import weka.core.Instances
import weka.attributeSelection.ASEvaluation

object NoScavengerWekaDemo {

  def sizeOf(o: Any): Int = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(o)
    baos.toByteArray.size
  }

  // I just don't get how one is supposed to use `Evaluation`...
  /** 
   * Computes accuracy (TP + TN / (TP + TN + FP + FN)) of a classifier
   * an a test dataset.
   */
  def accuracy(trainedClassifier: Classifier, testData: Instances): Double = {
    var total = 0
    var correct = 0
    for (i <- 0 until testData.numInstances) {
      val inst = testData.instance(i)
      val expected = inst.classValue()
      val predicted = trainedClassifier.classifyInstance(inst)
      if (expected == predicted) correct += 1
      total += 1
    }
    correct.toDouble / total
  }

  def main(args: Array[String]): Unit = {
    val source = new DataSource("yeast.arff")
    val data = source.getDataSet()
    data.setClassIndex(3 /* data.numAttributes() - 1 */)
    
    val randSeed = 42
    val folds = 5

    data.randomize(new Random(randSeed))
    val trainData = data.trainCV(folds, 1)
    val testData = data.testCV(folds, 1)

    val ranker = new Ranker()
    ranker.setNumToSelect(5)

    val reliefEvaluator = new ReliefFAttributeEval()
    reliefEvaluator.setNumNeighbours(10)
    reliefEvaluator.buildEvaluator(trainData)
    println("ReliefF num neighbors = " + reliefEvaluator.getNumNeighbours)

    val attrSel = new AttributeSelection()
    attrSel.setInputFormat(trainData)
    attrSel.setSearch(ranker)
    attrSel.setEvaluator(reliefEvaluator)

    val filteredTrainData = Filter.useFilter(trainData, attrSel)

    val smo = new SMO()
    println("SMO default c = " + smo.getC)
    smo.setC(1.0)

    smo.buildClassifier(filteredTrainData)

    val testAttrSel = new AttributeSelection()
    testAttrSel.setInputFormat(trainData)
    testAttrSel.setSearch(ranker)
    testAttrSel.setEvaluator(reliefEvaluator)

    // TAGS: WEKA_BUGS AttributeSelection has weirdest side-effects
    // if we replace `testAttrSel` by `attrSel` in the next line, we
    // somehow magically get +30% more accuracy?
    val filteredTestData = Filter.useFilter(testData, testAttrSel)
    val result = accuracy(smo, filteredTestData)

    println("Accuracy: " + result)

  }
}
