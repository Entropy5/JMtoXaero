package com.github.entropy5;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public class XaeroRegionMerger {

    public static void main(String[] args) {
        // First folder gets priority, put the important stuff here
        Path firstFolderIn = new File("C:\\Users\\mcmic\\Downloads\\in").toPath();
        Path secondFolderIn = new File("C:\\Users\\mcmic\\Downloads\\in").toPath();
        Path folderOut = new File("D:\\Program Files\\MultiMC\\instances\\2b\\.minecraft\\XaeroWorldMap\\Multiplayer_masonic.wasteofti.me\\null\\mw$default").toPath();

        Arrays.stream(Objects.requireNonNull(firstFolderIn.toFile().listFiles()))
                .forEach(fileIn -> {
                    System.out.println(fileIn.getName());


                });

    }


}
