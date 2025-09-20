package testsmell;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CustomResultsWriter{

    private String outputFile;
    private FileWriter writer;

    public static CustomResultsWriter createResultsWriter() throws IOException {
        return new CustomResultsWriter();
    }

    public void writeLine(List<String> columnValues) throws IOException {
        writeOutput(columnValues);
    }

    private void writeOutput(List<String> dataValues)throws IOException {
        writer = new FileWriter(outputFile,true);

        for (int i=0; i<dataValues.size(); i++) {
            writer.append(String.valueOf(dataValues.get(i)));

            if(i!=dataValues.size()-1)
                writer.append(",");
            else
                writer.append(System.lineSeparator());

        }
        writer.flush();
        writer.close();
    }


}