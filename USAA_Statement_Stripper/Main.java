import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.opencsv.CSVWriter;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java -jar USAA_Statement_Stripper.jar <filename>.pdf");
            return;
        }

        // Load PDF
        // File inputFile = new File("20230819_bank_checking_statement_2384.pdf");
        File inputFile = new File(args[0]);
        PDDocument document = Loader.loadPDF(inputFile);
        PDFTextStripper pdfStripper = new PDFTextStripper();
        String pdfString = pdfStripper.getText(document);
        document.close();

        // Load String into List of pages
        // Remove useless pages
        List<String> rawPages = new ArrayList<>(
                Arrays.asList(pdfString.split("Date Description Debits Credits Balance")));
        rawPages.remove(0);
        List<String> tempPages = new ArrayList<>(
                Arrays.asList(rawPages.get(rawPages.size() - 1).split("Interest Paid Information")));
        rawPages.set(rawPages.size() - 1, tempPages.get(0));

        // Load pages into List of lines
        List<String> rawLines = new ArrayList<String>();
        for (int i = 0; i < rawPages.size(); i++) {
            rawLines.addAll(Arrays.asList(rawPages.get(i).split("\n")));
        }

        // Remove beginning and ending balance lines
        rawLines.remove(rawLines.size() - 1);
        rawLines.remove(1);
        rawLines.remove(0);

        // Remove "0 0", "0", and "" lines
        for (int i = rawLines.size() - 1; i >= 0; i--) {
            rawLines.set(i, cleanString(rawLines.get(i)));

            if (rawLines.get(i).equals("0") || rawLines.get(i).equals("0 0") || rawLines.get(i).equals("")) {
                rawLines.remove(i);
            }
            rawLines.set(i, cleanString(rawLines.get(i)));
        }

        // Remove all lines between "Transactions (continued)" and "Page <x> of <x>",
        // inclusive
        boolean continueRemove = false;
        for (int i = rawLines.size() - 1; i >= 0; i--) {
            rawLines.set(i, cleanString(rawLines.get(i)));
            String regexPattern = "Page \\d+ of \\d+";
            Pattern pattern = Pattern.compile(regexPattern);

            if (rawLines.get(i).equals("Transactions (continued)")) {
                continueRemove = true;
            }

            if (continueRemove && !pattern.matcher(rawLines.get(i)).matches()) {
                rawLines.remove(i);
            } else if (continueRemove && pattern.matcher(rawLines.get(i)).matches()) {
                rawLines.remove(i);
                continueRemove = false;
            }
            rawLines.set(i, cleanString(rawLines.get(i)));
        }

        // Convert lines back into string
        String temp = "";
        for (int i = 0; i < rawLines.size(); i++) {
            temp += rawLines.get(i);
        }
        temp.trim();

        // Arranging list into budget categories
        // Split string by preceding number (extracting description)
        List<String> splitLinesPreceding = new ArrayList<>(
                Arrays.asList(temp.split("(?<=\\$\\d{0,3},{0,1}\\d{0,3}\\.\\d{2})")));

        // Split each line of previous list by following number (extracting description)
        List<String> splitLinesFollowed = new ArrayList<>();
        for (int i = 0; i < splitLinesPreceding.size(); i++) {
            splitLinesFollowed.addAll(
                    (Arrays.asList(splitLinesPreceding.get(i).split("(?=\\$\\d{0,3},{0,1}\\d{0,3}\\.\\d{2})"))));
        }
        // Remove all lines which are only " "
        splitLinesFollowed.removeIf(s -> s.equals(" "));
        List<String> splitLines = splitLinesFollowed;
        // Extract date
        for (int i = splitLines.size() - 1; i >= 0; i--) {
            if (i % 3 == 1) {
                String tempDesc = splitLines.get(i);
                splitLines.remove(i);
                splitLines.addAll(i, Arrays.asList(new String[] { tempDesc.substring(0, 5), tempDesc.substring(6) }));
            }
        }
        // Remove amount remaining lines
        for (int i = splitLines.size() - 1; i >= 0; i--) {
            if (i % 4 == 0) {
                splitLines.remove(i);
            }
        }

        // Populating table
        String[][] formattedLines = new String[splitLines.size() / 3][6];
        int j = -1;
        for (int i = 0; i < splitLines.size(); i++) {
            if (i % 3 == 0) {
                j++;
                formattedLines[j][0] = splitLines.get(i);
            } else if (i % 3 == 1) {
                formattedLines[j][5] = splitLines.get(i);
            } else if (i % 3 == 2) {
                formattedLines[j][3] = splitLines.get(i);
            }
            formattedLines[j][4] = "USAA";
        }

        // Print
        for (int i = 0; i < formattedLines.length; i++) {
            for (int j1 = 0; j1 < formattedLines[i].length; j1++) {
                System.out.print(formattedLines[i][j1] + ",");
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
            for (int i = 0; i < formattedLines.length; i++) {
                writer.writeNext(formattedLines[i]);
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