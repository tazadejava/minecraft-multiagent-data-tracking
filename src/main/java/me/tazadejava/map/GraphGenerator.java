package me.tazadejava.map;

import me.tazadejava.mission.MissionGraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//goal: creates a graph representation of a file based on a CSV file
public class GraphGenerator {

    public static void main(String[] args) {
        File file = new File("/home/yoshi/Documents/GenesisUROP/OriginalMaps/sparky.csv");

        MissionGraph graph = getGraphFromCSV(file);
    }

    //assumes CSV file
    //assumes:
    //  D - door
    //  S - spawn point; assuming it is in a hallway
    //  none - air, walking space
    //room definition:
    //  space with at least 2x2 space
    //  surrounded by either door or a 1x1 area to get out
    public static MissionGraph getGraphFromCSV(File inputFile) {
        List<String[]> lines = new ArrayList<>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));

            String read;
            while((read = reader.readLine()) != null) {
                List<String> line = new ArrayList<>();
                int startIndex = 0;
                int currentIndex = 0;
                for(char val : read.toCharArray()) {
                    if(val == ',') {
                        line.add(read.substring(startIndex, currentIndex));
                        startIndex = currentIndex + 1;
                    }
                    currentIndex++;
                }

                if(startIndex <= read.length()) {
                    line.add(read.substring(startIndex, currentIndex));
                }

                //put border around left and right
                line.add(0, "B");
                line.add("B");

                lines.add(line.toArray(new String[0]));
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //put border around the top and bottom
        lines.add(0, createRow(lines.get(0).length, "B"));
        lines.add(createRow(lines.get(0).length, "B"));

        String[][] mapping = lines.toArray(new String[0][0]);

        for(String[] row : mapping) {
            System.out.println(Arrays.toString(row));
        }

        return null;
    }

    private static String[] createRow(int length, String fill) {
        String[] row = new String[length];
        for(int i = 0; i < length; i++) {
            row[i] = fill;
        }

        return row;
    }
}
