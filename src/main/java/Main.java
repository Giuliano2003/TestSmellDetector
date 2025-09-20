import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.stream.JsonWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import testsmell.*;
import thresholds.DefaultThresholds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args == null) {
            System.out.println("Please provide the file containing the paths to the collection of test files");
            return;
        }
        if (!args[0].isEmpty()) {
            File inputFile = new File(args[0]);
            if (!inputFile.exists() || inputFile.isDirectory()) {
                System.out.println("Please provide a valid file containing the paths to the collection of test files");
                return;
            }
        }

        TestSmellDetector testSmellDetector = new TestSmellDetector(new DefaultThresholds());

        /*
          Read the input file and build the TestFile objects
         */
        BufferedReader in = new BufferedReader(new FileReader(args[0]));
        String str;

        String[] lineItem;
        TestFile testFile;
        List<TestFile> testFiles = new ArrayList<>();
        while ((str = in.readLine()) != null) {
            // use comma as separator
            lineItem = str.split(",");

            //check if the test file has an associated production file
            if (lineItem.length == 2) {
                testFile = new TestFile(lineItem[0], lineItem[1], "");
            } else {
                testFile = new TestFile(lineItem[0], lineItem[1], lineItem[2]);
            }

            testFiles.add(testFile);
        }

        TestFile tempFile;
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date;

        JSONObject jsonResult = new JSONObject();
        for (TestFile file : testFiles) {
            System.out.println("ciao");
            //detect smells
            tempFile = testSmellDetector.detectSmells(file);
            //write output
            for (AbstractSmell smell : tempFile.getTestSmells()) {
                if(smell.hasSmell()){
                    Map<String, Set<String>> result = smell.getResult();
                    JSONObject smellEntry = new JSONObject();
                    JSONArray array = new JSONArray();
                    Set<String> strings = smell.getResult().get(smell.getSmellName());
                    for (String smelll: strings) {
                        array.add(smelll);
                    }
                    smellEntry.put("methods",array);
                    smellEntry.put("score",smell.getScore().get(smell.getSmellName()));
                    jsonResult.put(smell.getSmellName(),smellEntry);
                }
            }
        }

    }


}
