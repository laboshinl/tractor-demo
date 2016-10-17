package ru.laboshinl.tractor;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class SystemCmd {
    public static void main(String[] args) {
        try {
            parseWithLpi("/home/laboshinl/Downloads/holly.pcap");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static scala.collection.mutable.Map<String, String> parseWithLpi(String file) throws Exception {
        //HashMap<String, String> result = new HashMap();
        scala.collection.mutable.Map<String,String> result = new  scala.collection.mutable.HashMap<>();
        ProcessBuilder builder = new ProcessBuilder(
                "/usr/local/bin/lpi_arff", "-b", file);
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        for(int i = 0; i < 29; i++){
            r.readLine();
        }
        while (true) {
            line = r.readLine();
            if (line == null) { break; }
            String[] values = line.split(",");
            //test.put(values[23], values[0]);
            result.put(values[23], values[0]);
        }
        //System.out.println(test);
        return result;
    }
}