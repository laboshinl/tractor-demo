package ru.laboshinl.tractor;

import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.meta.AttributeSelectedClassifier;
import weka.classifiers.rules.PART;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.FastVector;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;

import java.io.File;
import java.util.Random;

public class TestModel {
//    public static BufferedReader readDataFile(String filename) {
//        BufferedReader inputReader = null;
//
//        try {
//            inputReader = new BufferedReader(new FileReader(filename));
//        } catch (FileNotFoundException ex) {
//            System.err.println("File not found: " + filename);
//        }
//
//        return inputReader;
//    }

    public static Evaluation classify(Classifier model,
                                      Instances trainingSet, Instances testingSet) throws Exception {
        Evaluation evaluation = new Evaluation(trainingSet);

        model.buildClassifier(trainingSet);
        evaluation.evaluateModel(model, testingSet);

        return evaluation;
    }

    public static double calculateAccuracy(FastVector predictions) {
        double correct = 0;

        for (int i = 0; i < predictions.size(); i++) {
            NominalPrediction np = (NominalPrediction) predictions.elementAt(i);
            if (np.predicted() == np.actual()) {
                //System.out.println(np.predicted());
                correct++;
            }
        }

        return 100 * correct / predictions.size();
    }

    public static Instances[][] crossValidationSplit(Instances data, int numberOfFolds) {
        Instances[][] split = new Instances[2][numberOfFolds];

        for (int i = 0; i < numberOfFolds; i++) {
            split[0][i] = data.trainCV(numberOfFolds, i);
            split[1][i] = data.testCV(numberOfFolds, i);
        }

        return split;
    }
    protected static void useClassifier(Instances data) throws Exception {
        System.out.println("\n1. Meta-classfier");
        AttributeSelectedClassifier classifier = new AttributeSelectedClassifier();
        CfsSubsetEval eval = new CfsSubsetEval();
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(true);
        J48 base = new J48();
        classifier.setClassifier(base);
        classifier.setEvaluator(eval);
        classifier.setSearch(search);
        Evaluation evaluation = new Evaluation(data);
        evaluation.crossValidateModel(classifier, data, 10, new Random(1));
        System.out.println(evaluation.toSummaryString());
    }

    /**
     * uses the filter
     */
    protected static void useFilter(Instances data) throws Exception {
        System.out.println("\n2. Filter");
        weka.filters.supervised.attribute.AttributeSelection filter = new weka.filters.supervised.attribute.AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(true);
        filter.setEvaluator(eval);
        filter.setSearch(search);
        filter.setInputFormat(data);
        Instances newData = Filter.useFilter(data, filter);
        System.out.println(newData);
    }

    /**
     * uses the low level approach
     */
    protected static void useLowLevel(Instances data) throws Exception {
        System.out.println("\n3. Low-level");
        AttributeSelection attsel = new AttributeSelection();
        CfsSubsetEval eval = new CfsSubsetEval();
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(true);
        attsel.setEvaluator(eval);
        attsel.setSearch(search);
        attsel.SelectAttributes(data);
        int[] indices = attsel.selectedAttributes();
        System.out.println("selected attribute indices (starting with 0):\n" + Utils.arrayToString(indices));
    }
    public static void main(String[] args) throws Exception {
        //BufferedReader datafile = readDataFile("/home/laboshinl/Downloads/articles/bigFlows.arff");

        CSVLoader loader = new CSVLoader();
        loader.setNoHeaderRowPresent(true);
        loader.setSource(new File("/home/laboshinl/all.csv"));
        Instances data = loader.getDataSet();
        //Instances data = new Instances(datafile);
        data.setClassIndex(data.numAttributes() - 1);

        // Do 10-split cross validation
        Instances[][] split = crossValidationSplit(data, 3);

        // Separate split into training and testing arrays
        Instances[] trainingSplits = split[0];
        Instances[] testingSplits = split[1];
//
//        // Use a set of classifiers
        Classifier[] models = {
                new J48(), // a decision tree
                new PART(),
                //new DecisionTable(),//decision table majority classifier
                //new DecisionStump(), //one-level decision tree
                new RandomForest()/*,
                new IBk()*/
                //new NaiveBayes(),
                // new SMO(),
                //new Logistic(),
                //new MultilayerPerceptron()
        };

//        for (int j = 0; j < models.length; j++) {
//
//            // Collect every group of predictions for current model in a FastVector
//            FastVector predictions = new FastVector();
//
//            // For each training-testing split pair, train and test the classifier
//            for (int i = 0; i < trainingSplits.length; i++) {
//
//
//                Evaluation validation = classify(models[j], trainingSplits[i], testingSplits[i]);
//
//                predictions.appendElements(validation.predictions());
//
//                // Uncomment to see the summary for each training-testing pair.
//                System.out.println(models[j].toString());
//            }
//
//            // Calculate overall accuracy of current classifier on all splits
//            double accuracy = calculateAccuracy(predictions);
//
//            // Print current classifier's name and accuracy in a complicated,
//            // but nice-looking way.
//            System.out.println("Accuracy of " + models[j].getClass().getSimpleName() + ": "
//                    + String.format("%.2f%%", accuracy)
//                    + "\n---------------------------------" + models[1].toString());
//        }

        useClassifier(data);

        // 2. filter
        useFilter(data);

        // 3. low-level
        useLowLevel(data);

    }


}