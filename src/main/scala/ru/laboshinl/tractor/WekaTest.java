package ru.laboshinl.tractor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.rules.DecisionTable;
import weka.classifiers.rules.PART;
import weka.classifiers.trees.DecisionStump;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.LMT;
import weka.classifiers.functions.SGD;
import weka.classifiers.lazy.IBk;
import weka.classifiers.lazy.LWL;
import weka.classifiers.trees.HoeffdingTree;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SMO;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.trees.RandomForest;
import weka.core.*;

public class WekaTest {
    public static BufferedReader readDataFile(String filename) {
        BufferedReader inputReader = null;

        try {
            inputReader = new BufferedReader(new FileReader(filename));
        } catch (FileNotFoundException ex) {
            System.err.println("File not found: " + filename);
        }

        return inputReader;
    }

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

    public static void main(String[] args) throws Exception {
        BufferedReader datafile = readDataFile("/home/laboshinl/Downloads/articles/bigFlows.arff");

        Instances data = new Instances(datafile);
        data.setClassIndex(data.numAttributes() - 1);

        // Do 10-split cross validation
        Instances[][] split = crossValidationSplit(data, 3);

        // Separate split into training and testing arrays
        Instances[] trainingSplits = split[0];
        Instances[] testingSplits = split[1];

        // Use a set of classifiers
        Classifier[] models = {
                //new J48(), // a decision tree
                //new PART(),
                //new DecisionTable(),//decision table majority classifier
                //new DecisionStump(), //one-level decision tree
                new RandomForest()/*,
                new IBk()*/
                //new NaiveBayes(),
               // new SMO(),
                //new Logistic(),
                //new MultilayerPerceptron()
        };

        // Run for each model
        for (int j = 0; j < models.length; j++) {

            // Collect every group of predictions for current model in a FastVector
            FastVector predictions = new FastVector();

            // For each training-testing split pair, train and test the classifier
            for (int i = 0; i < trainingSplits.length; i++) {
                ((RandomForest) models[j]).setOptions(weka.core.Utils.splitOptions("-I 200"));
                Evaluation validation = classify(models[j], trainingSplits[i], testingSplits[i]);

                predictions.appendElements(validation.predictions());

                // Uncomment to see the summary for each training-testing pair.
                //System.out.println(models[j].toString());
            }

            // Calculate overall accuracy of current classifier on all splits
            double accuracy = calculateAccuracy(predictions);
            models[j].buildClassifier(data);
            weka.core.SerializationHelper.write(String.format("/home/laboshinl/%s.%s", models[j].getClass().getSimpleName(), "model"), models[j]);
            //Instance inst = new Instance();

//            Instance inst_co;
//
//            ArrayList<Attribute> attributes = new ArrayList<Attribute>();
//            for (int i = 0; i < 67; i++){
//                attributes.add(new Attribute(String.format("attribute_%s", i)));
//            }
//
//            Instances testing = new Instances("test",attributes,0);
//            System.out.println((testing.numAttributes()));
//
//            inst_co = new DenseInstance(testing.numAttributes());
            double [] array = {54,54,141.429,54,485,571,36902.619,54,54,105.8,58,236,236,6263.2,54,54,126.583,56,252.75,571,22742.811,54,54,55.143,54,60.4,62,9.143,54,54,54.8,54,58,58,3.2,54,54,55,54,58.2,62,6.182,4,3,6,5,0,0,0,0,0,0,0,0,2,2,604,2,2,1,1,0,0,1,0,0,0};
            System.out.println(array.length);
//
//
//            for (int i = 0; i < 67; i++){
//                inst_co.setValue(attributes.get(i), array[i]);
//            }
//
//            testing.add(inst_co);
//            inst_co.setDataset(testing);
//            testing.setClassIndex(testing.numAttributes() - 1);
//
////            Instances dataUnlabeled = new Instances("TestInstances", atts, 0);
////            dataUnlabeled.add(newInst);
////            dataUnlabeled.setClassIndex(dataUnlabeled.numAttributes() - 1);
//
//            System.out.println(inst_co.toString());

//            ArrayList<Attribute> attributes = new ArrayList<>();
//            for (int i = 0; i < 68; i++){
//                attributes.add(new Attribute(String.format("attribute_%s", i)));
//            }
//            Instances testing = new Instances("test",attributes,0);

            Instance inst = new DenseInstance(1.0, array);
            data.add(inst);
//            for (int i = 0; i < 67; i++){
//                inst.setValue(attributes.get(i), array[i]);
//            }
//            //inst.setMissing(attributes.get(67));
//            inst.setDataset(testing);
//            testing.setClassIndex(testing.numAttributes() - 1);
//            testing.add(inst);
            int pred = (int) models[j].classifyInstance(data.lastInstance()); //.classifyInstance(testing.firstInstance()));
            System.out.println("actual: " + data.classAttribute().value((int) data.lastInstance().classValue()));
            System.out.println("predicted: " + data.classAttribute().value(pred));
            // Print current classifier's name and accuracy in a complicated,
            // but nice-looking way.
            System.out.println("Accuracy of " + models[j].getClass().getSimpleName() + ": "
                    + String.format("%.2f%%", accuracy)
                    + "\n---------------------------------");
        }

    }
}