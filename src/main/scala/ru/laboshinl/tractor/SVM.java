package ru.laboshinl.tractor;

import edu.berkeley.compbio.jlibsvm.ImmutableSvmParameter;
import edu.berkeley.compbio.jlibsvm.ImmutableSvmParameterGrid;
import edu.berkeley.compbio.jlibsvm.binary.BinaryModel;
import edu.berkeley.compbio.jlibsvm.binary.C_SVC;
import edu.berkeley.compbio.jlibsvm.binary.MutableBinaryClassificationProblemImpl;
import edu.berkeley.compbio.jlibsvm.kernel.LinearKernel;
import edu.berkeley.compbio.jlibsvm.kernel.CompositeGaussianRBFKernel;
import edu.berkeley.compbio.jlibsvm.util.SparseVector;
import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

/**
 * This is an example of how to use the JLibSVM library.
 * <p>
 * It is translated from the Clojure code at
 * http://lilyx.net/2011/07/02/using-svm-support-vector-machine-from-clojure/
 * <p>
 * The SVM is trained with 2 labels, each with 1 data entry as a proof-of-concept
 * and for simplicity.
 * <p>
 * Putting in features characteristic of the label yields the correct result.
 * <p>
 * Created by joel on 7/7/15.
 */
public class SVM {

    public static void main (String[] args) {
        // create a new SVM implementation in the C SVC style.
        C_SVC svm = new C_SVC();
        // build parameters
        ImmutableSvmParameterGrid.Builder builder = ImmutableSvmParameterGrid.builder();

        // create training parameters ------------
        HashSet<Float> cSet = new HashSet<>();
        HashSet<LinearKernel> kernelSet = new HashSet<>();

        cSet.add(1.0f);

        kernelSet.add(new LinearKernel());

        // configure finetuning parameters
        builder.eps = 0.001f; // epsilon
        builder.Cset = cSet; // C values used
        builder.kernelSet = kernelSet; //Kernel used

        ImmutableSvmParameter params = builder.build();
        // / create training parameters ------------

        Map<String, Integer> labels = new HashMap<>();
        // create problem -------------------
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream("/home/laboshinl/Downloads/remotepcaps/goodpcaps/bigFlows.csv")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String line = null;
        Map<SparseVector, Integer> example = new HashMap<>();
        //List<SparseVector> example = new ArrayList<SparseVector>();
        int counter = 0;
        while (true) {
            try {
                line = r.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (line == null) { break; }
            String[] values = line.split(",");
            try {
                float [] vector = new float[68];
                if (! labels.containsKey(values[67])) {
                    labels.put(values[67], counter);
                    counter ++;
                }
                for(int i = 0; i < 67; i++){
                    vector[i] = Float.parseFloat(values[i]);
                }
                example.put(generateFeatures(vector), labels.get(values[67]));
            }
            catch (Exception e) {
                System.out.println(e);
            }
        }

        MutableBinaryClassificationProblemImpl problem
                = new MutableBinaryClassificationProblemImpl(String.class, 1000);

        example.forEach( (key,value) ->
                problem.addExample(key, value)
        );

        BinaryModel model = svm.train(problem, params);



//        SparseVector x1 = generateFeatures(new float[]{0.1f, 0.2f});
//        SparseVector x2 = generateFeatures(new float[]{0.8f, 0.9f});
//        MutableBinaryClassificationProblemImpl problem
//                = new MutableBinaryClassificationProblemImpl(String.class, 2);
//        problem.addExample(x1, 1); // label with 1
//        problem.addExample(x2, -1); // label with -1
//        // / create problem -------------------
//
//        // train ------------------------
//        BinaryModel model = svm.train(problem, params);
//        // / train ------------------------
//
//        // predict -------------------------
//        SparseVector xTest = generateFeatures(new float[]{0.2f, 0.1f});
//        int predictedLabel = (Integer) model.predictLabel(xTest);
//        System.out.println("predicted:" + predictedLabel);
        // / predict -------------------------


    }

    /**
     * Helper function to generate a single featureset.
     *
     * @param floats
     * @return
     */
    private static SparseVector generateFeatures(float[] floats) {
        SparseVector sparseVector = new SparseVector(floats.length);
        int[] indices = new int[68];
        for (int i = 0; i < floats.length; i++) {
            indices[i] = new Integer(i);
        }
        sparseVector.indexes = indices;
        sparseVector.values = floats;
        return sparseVector;
    }
}