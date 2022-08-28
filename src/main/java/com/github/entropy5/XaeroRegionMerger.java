package com.github.entropy5;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

public class XaeroRegionMerger {

    public static void main(String[] args) {
        // First folder gets pixel priority, put the important stuff here
        Path firstFolderIn = new File("C:\\Users\\mcmic\\Downloads\\in1").toPath();
        Path secondFolderIn = new File("C:\\Users\\mcmic\\Downloads\\in2").toPath();
        Path folderOut = new File("C:\\Users\\mcmic\\Downloads\\out").toPath();

        HashSet<String> firstSet = getFileNames(firstFolderIn);
        HashSet<String> secondSet = getFileNames(secondFolderIn);

        HashSet<String> onlyFirst = new HashSet<>(firstSet);
        onlyFirst.removeAll(secondSet);  // Difference
        System.out.println("Only present in first: " + onlyFirst);
        copyFull(firstFolderIn, folderOut, onlyFirst);

        HashSet<String> onlySecond = new HashSet<>(secondSet);
        onlySecond.removeAll(firstSet);  // Difference
        System.out.println("Only present in second: " + onlySecond);
        copyFull(secondFolderIn, folderOut, onlySecond);

        firstSet.retainAll(secondSet);  // Intersection
    }

    private static void copyFull(Path folderIn, Path folderOut, HashSet<String> regionSet) {
        for (String s : regionSet) {
            Path from = folderIn.resolve(s);
            Path to = folderOut.resolve(s);
            try {
                Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static HashSet<String> getFileNames(Path folder) {
        HashSet<String> nameSet = new HashSet<>();
        Arrays.stream(Objects.requireNonNull(folder.toFile().listFiles()))
                .forEach(fileIn -> nameSet.add(fileIn.getName()));
        return nameSet;
    }
}
