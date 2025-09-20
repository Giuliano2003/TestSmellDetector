package testsmell;

import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is utilized to write output to a CSV file
 */
public class ResultsWriter {

    private String outputFile;
    private FileWriter writer;

    /**
     * Creates the file into which output it to be written into. Results from each file will be stored in a new file
     * @throws IOException
     */
    private ResultsWriter() throws IOException {
        outputFile = MessageFormat.format("{0}_{1}.{2}", "Output","smell", "txt");
        writer = new FileWriter(outputFile,false);
    }

    /**
     * Factory method that provides a new instance of the ResultsWriter
     * @return new ResultsWriter instance
     * @throws IOException
     */
    public static ResultsWriter createResultsWriter() throws IOException {
        return new ResultsWriter();
    }

    public void writeResultOutput(Map<String, Set<String>> result) throws IOException {
        writer = new FileWriter(outputFile,true);
        for(String key : result.keySet()){
            writer.append(key + System.lineSeparator());
            for(String values: result.get(key)){
                writer.append("\t" + values + " ");
            }
            writer.append(System.lineSeparator());
        }
        writer.flush();
        writer.close();
    }


    /**
     * Writes column names into the CSV file
     * @param columnNames the column names
     * @throws IOException
     */

    public void writeColumnName(List<String> columnNames) throws IOException {
        //customWriteOutput(columnNames);
        writeOutput(columnNames);
    }

    /**
     * Writes column values into the CSV file
     * @param columnValues the column values
     * @throws IOException
     */
    public void writeLine(List<String> columnValues) throws IOException {
        writeOutput(columnValues);
    }

    /**
     * Appends the input values into the CSV file
     * @param dataValues the data that needs to be written into the file
     * @throws IOException
     */

    private void customWriteOutput(List<String> dataValues)throws IOException {
        writer = new FileWriter(outputFile,true);

        for (int i=0; i<dataValues.size(); i++) {
            writer.append(String.valueOf(dataValues.get(i)));

            if(i<=dataValues.size())
                writer.append(",");

            if(String.valueOf(dataValues.get(0)).equals("App")){
                if(i > 6){
                    writer.append("Riga");
                    if(i!=dataValues.size()-1)
                        writer.append(",");
                    else
                        writer.append(System.lineSeparator());
                }
            }

        }
        writer.flush();
        writer.close();
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