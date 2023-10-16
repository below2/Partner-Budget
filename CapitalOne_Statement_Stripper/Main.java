import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.opencsv.CSVWriter;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java -jar CapitalOne_Statement_Stripper.jar <filename>.pdf");
            return;
        }

        // Load PDF
        // File inputFile = new File("Statement_082023_9736.pdf");
        File inputFile = new File(args[0]);
        PDDocument document = Loader.loadPDF(inputFile);
        PDFTextStripper pdfStripper = new PDFTextStripper();
        String pdfString = pdfStripper.getText(document);
        document.close();

        // Load String into List
        String regexPattern = ".*#\\d{4}: Payments, Credits and Adjustments";
        Pattern pattern = Pattern.compile(regexPattern);
        List<String> rawText = new ArrayList<>(Arrays.asList(pattern.split(pdfString)));

        // Sanitize data
        rawText.remove(0);
        regexPattern = ".*#\\d{4}: Total Transactions \\$.*\\.\\d{2}";
        pattern = Pattern.compile(regexPattern);
        List<String> treatmentOne = new ArrayList<>();
        for (String s : rawText) {
            List<String> tempList = new ArrayList<>(Arrays.asList(pattern.split(s)));
            String[] tempArray = tempList.get(0).split(System.lineSeparator());
            for (String temp : tempArray) {
                treatmentOne.add(temp);
            }
        }

        regexPattern = ".*#\\d{4}: Transactions";
        pattern = Pattern.compile(regexPattern);

        Iterator<String> iterator = treatmentOne.iterator();
        while (iterator.hasNext()) {
            String str = cleanString(iterator.next());
            if (str.contains("Trans Date Post Date Description Amount") || pattern.matcher(str).matches()
                    || str.equals("")) {
                iterator.remove();
            }
        }

        String[][] treatmentTwo = new String[treatmentOne.size()][6];
        for (int i = 0; i < treatmentOne.size(); i++) {
            String[] temp = treatmentOne.get(i).split(" ");
            String price;
            String comment = "";
            price = temp[temp.length - 1];
            for (int j = 4; j < temp.length - 1; j++) {
                comment += temp[j] + " ";
            }

            // Date
            treatmentTwo[i][0] = temp[0] + " " + temp[1];
            // Amount
            treatmentTwo[i][3] = price;
            // Account
            treatmentTwo[i][4] = "Capital One";
            // Comment
            treatmentTwo[i][5] = comment;
        }

        // Print
        for (int i = 0; i < treatmentTwo.length; i++) {
            for (int j = 0; j < treatmentTwo[i].length; j++) {
                System.out.print(treatmentTwo[i][j] + " ");
            }
            System.out.println();
        }

        // Write to CSV
        String tempFile = System.getProperty("user.dir") + "\\statement_temp.csv";
        System.out.println(tempFile);
        File csvFile = new File(tempFile);
        try {
            FileWriter outputFile = new FileWriter(csvFile);
            CSVWriter writer = new CSVWriter(outputFile);

            String[] header = { "Date", "Category", "Split", "Amount", "Account", "Comment" };
            writer.writeNext(header);
            for (int i = 0; i < treatmentTwo.length; i++) {
                writer.writeNext(treatmentTwo[i]);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String cleanString(String input) {
        String temp = input.replaceAll("\\s+", " ").trim();
        temp.replace("\n", "");
        return temp;
    }
}