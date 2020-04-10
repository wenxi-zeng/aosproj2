package util;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileHelper {

    public static String read(String dir, String filename)  throws IOException {
        return String.join("\n", read(dir.isEmpty() ? filename : dir + File.separator + filename));
    }

    public static List<String> read(String filename) throws IOException {
        File file = new File(filename);
        List<String> lines = new ArrayList<>();

        if (file.exists()) {

            try (BufferedReader fileReader = new BufferedReader(new FileReader(filename))) {
                String line = fileReader.readLine();

                while (line != null) {
                    lines.add(line);
                    line = fileReader.readLine();
                }
            }
        }

        return lines;
    }

    public static void append(String filename, String line) throws IOException {
        FileWriter fileWriter = new FileWriter(filename, true);
        fileWriter.write(line.trim() + "\n");
        fileWriter.close();
    }

    public static void append(String dir, String filename, String line) throws IOException {
        mkdir(dir);
        append(dir.isEmpty() ? filename : dir + File.separator + filename, line);
    }

    public static boolean exist(String filename) {
        File file = new File(filename);
        return file.exists();
    }

    public static boolean mkdir(String dir) {
        return new File(dir).mkdirs();
    }

    public static int hash(String filename) {
        String num = filename.replaceAll("[^0-9]", "");
        if (StringUtils.isNoneEmpty(num)) return filename.hashCode() % 7;
        else return Integer.parseInt(num) % 7;
    }
}
