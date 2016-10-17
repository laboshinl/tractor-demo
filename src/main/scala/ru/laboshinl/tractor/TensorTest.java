package ru.laboshinl.tractor;


import org.bytedeco.javacpp.tensorflow;
import org.bytedeco.javacpp.tensorflow.*;

import java.nio.FloatBuffer;

import static org.bytedeco.javacpp.tensorflow.ReadBinaryProto;

/**
 * Created by laboshinl on 14.10.16.
 */
public class TensorTest {
    public static void main(String[] args) {
        final Session session = new Session(new SessionOptions());
        GraphDef def = new GraphDef();
        ReadBinaryProto(Env.Default(), "/home/laboshinl/workspace/tractor-demo/trained_model.proto", def);
        Status s = session.Create(def);
        if (!s.ok()) {
            throw new RuntimeException(s.error_message().getString());
        }
        Tensor fn = new Tensor(tensorflow.DT_STRING, new TensorShape(1));

        org.bytedeco.javacpp.helper.tensorflow.StringArray a = fn.createStringArray();
        a.position(0).put("/home/laboshinl/workspace/tractor-demo/trained_model.sd");

        s = session.Run(new StringTensorPairVector(
                new String[]{"save/Const:0"}, new Tensor[]{fn}),
                new StringVector(),
                new StringVector("save/restore_all"),
                new TensorVector());

        if (!s.ok()) {
            throw new RuntimeException(s.error_message().getString());
        }
        // try to predict for two (2) sets of inputs.
        Tensor inputs = new Tensor(
                tensorflow.DT_FLOAT, new TensorShape(1,67));
        FloatBuffer x = inputs.createBuffer();
        x.put(new float[]{70f,70f,369.586f,70f,938f,938f,175879.966f,76f,993f,1222.474f,1530f,1530f,1530f,275591.824f,
                70f,70f,853.313f,938f,1530f,1530f,410392.703f,54f,54f,54.414f,54f,54f,66f,4.966f,54f,54f,54.316f,
                54f,54f,66f,3.789f,54f,54f,54.358f,54f,54f,66f,4.233f,12f,37f,28f,38f,11f,11f,
                0f,0f,0f,0f,0f,0f,12f,37,2795f,10f,10f,1f,1f,1f,1f,0f,1f,0f,0f});
       // x.put(new float[]{66.0f,22.0f,2422.0f,45.72160947712418f,0.4f});
        s = session.Run(new StringTensorPairVector(new String[]{}, new Tensor[]{inputs}), new StringVector(64809350), new StringVector(), new TensorVector() );
//        Tensor keepall = new Tensor(
//                tensorflow.DT_FLOAT, new TensorShape(1,1));
//        ((FloatBuffer)keepall.createBuffer()).put(new float[]{1f});
//
//        TensorVector outputs = new TensorVector();
//
//// to predict each time, pass in values for placeholders
//        outputs.resize(0);
//        s = session.Run(new StringTensorPairVector(new String[] {"Placeholder"}, new Tensor[] {inputs, keepall}),
//        new StringVector("Placeholder2"), new StringVector(), outputs);
        if (!s.ok()) {
            throw new RuntimeException(s.error_message().getString());
        }
//
//// this is how you get back the predicted value from outputs
//        FloatBuffer output = outputs.get(0).createBuffer();
//        for (int k=0; k < output.limit(); ++k){
//            System.out.println("prediction=" + output.get(k));
//        }
    }
}
