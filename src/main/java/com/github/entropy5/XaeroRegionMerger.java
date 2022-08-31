package com.github.entropy5;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class XaeroRegionMerger {

    static int counter = 0;

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

        HashSet<String> inter = new HashSet<>(firstSet);
        inter.retainAll(secondSet);  // Intersection
        System.out.println("Need to deep merge: " + inter);
        deepMerge(firstFolderIn, secondFolderIn, folderOut, inter);

    }

    private static void deepMerge(Path inp1, Path inp2, Path outp, HashSet<String> rNames) {
        for (String rName : rNames) {
            System.out.println("Merging " + rName);
            File fileIn1 = inp1.resolve(rName).toFile();
            File fileIn2 = inp2.resolve(rName).toFile();
            File fileOut = outp.resolve(rName).toFile();
            DataInputStream in = null;
            DataInputStream in2 = null;
            DataOutputStream out = null;
            int saveVersion = -1;
            try {
                try {
                    ZipInputStream zipIn1 = new ZipInputStream(new BufferedInputStream(Files.newInputStream(fileIn1.toPath()), 2048));
                    ZipInputStream zipIn2 = new ZipInputStream(new BufferedInputStream(Files.newInputStream(fileIn2.toPath()), 2048));
                    in = new DataInputStream(zipIn1);
                    in2 = new DataInputStream(zipIn2);
                    zipIn1.getNextEntry();
                    zipIn2.getNextEntry();

                    final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(fileOut.toPath())));
                    out = new DataOutputStream(zipOut);
                    final ZipEntry e = new ZipEntry("region.xaero");
                    zipOut.putNextEntry(e);
                    out.write(255);  // mimicking logic from Xaero format
                    out.writeInt(4);


                    int firstByte = in.read();
                    System.out.println("firstbyte: " + firstByte);
                    if (firstByte == 255) {
                        saveVersion = in.readInt();
                        System.out.println("version: " + saveVersion);
                        if (4 < saveVersion) {
                            zipIn1.closeEntry();
                            in.close();
                            throw new RuntimeException(rName + " made using newer xaero version");
                        }
                        firstByte = -1;
                    }
                    int p;

                    while (true) {  // Keeps reading TileChunks (max 8x8) until done
                        int chunkCoords = firstByte == -1 ? in.read() : firstByte;
                        System.out.println("ChunkCoords: " + chunkCoords);
                        if (chunkCoords == -1) {  // -1 as chunk coord means its over
                            System.out.println("closing");
                            zipIn1.closeEntry();
                            break;
                        }

                        firstByte = -1;
                        int o = chunkCoords >> 4;
                        p = chunkCoords & 15;
                        System.out.println("Chunk: " + o + "," + p);

                        for (int i = 0; i < 4; ++i) {
                            for (int j = 0; j < 4; ++j) {
                                Integer nextTile = in.readInt();
                                System.out.println("parameters: " + nextTile);
                                if (nextTile != -1) {  // Skip empty chunk
                                    for (int x = 0; x < 16; ++x) {
                                        for (int z = 0; z < 16; ++z) {
                                            passPixel(nextTile, in, out, saveVersion, true);
                                            nextTile = null;
                                        }
                                    }
                                    if (saveVersion == 4) {
                                        int worldInterVersion = in.read();
                                    }
                                } else {
                                    System.out.println("Empty chunk " + o + "," + p + "," + i + "," + j);
                                }
                            }
                        }
                    }
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private static void passPixel(Integer next, DataInputStream in, DataOutputStream out, int saveVersion, boolean write) throws IOException {
        int parametres;
        if (next != null) {
            parametres = next;
        } else {
            parametres = in.readInt();
            System.out.println("special parameters: " + parametres);
            if (write) {
                out.writeInt(parametres);
            }
        }

        if ((parametres & 1) != 0) {
            int state = in.readInt();
            System.out.println("counter: " + counter + " - state: " + state);
            counter++;
            if (write) {
                out.writeInt(state);
            }
        }

        if ((parametres & 64) != 0) {
            int height = in.read();
            System.out.println("height: " + height);
            if (write) {
                out.write(height);
            }
        }

        if ((parametres & 16777216) != 0) {
            int topHeight = in.read();
        }

        int savedColourType;
        int biomeKey;
        if ((parametres & 2) != 0) {
            savedColourType = in.read();
            System.out.println("savedcolortype: " + savedColourType);
            if (write) {
                out.write(savedColourType);
            }

            for(biomeKey = 0; biomeKey < savedColourType; ++biomeKey) {
                passOverlay(in, out, saveVersion, write);
            }
        }
        savedColourType = parametres >> 2 & 3;
        if (savedColourType == 3) {
            int customColour = in.readInt();
            System.out.println("customc: " + customColour);
            if (write) {
                out.writeInt(customColour);
            }
        }

        biomeKey = -1;
        if (savedColourType != 0 && savedColourType != 3 || (parametres & 1048576) != 0) {
            int biomeByte = in.read();
            if (write) {
                out.write(biomeByte);
            }
            if (saveVersion >= 3 && biomeByte >= 255) {
                biomeKey = in.readInt();
                if (write) {
                    out.writeInt(biomeKey);
                }
            }
        }
    }


    private static void passOverlay(DataInputStream in, DataOutputStream out, int saveVersion, boolean write) throws IOException {
        int parametres = in.readInt();
        int state;
        if ((parametres & 1) != 0) {
            state = in.readInt();
            if (write) {
                out.writeInt(state);
            }
        }
        int opacity = 1;
        if (saveVersion < 1 && (parametres & 2) != 0) {
            int something = in.readInt();
            if (write) {
                out.writeInt(something);
            }
        }
        byte savedColourType = (byte)(parametres >> 8 & 3);
        if (savedColourType == 2 || (parametres & 4) != 0) {
            int biomeBuffer = in.readInt();
            if (write) {
                out.writeInt(biomeBuffer);
            }
        }
        if ((parametres & 8) != 0) {
            opacity = in.readInt();
            if (write) {
                out.writeInt(opacity);
            }
        }
    }


    private static void copyFull(Path folderIn, Path folderOut, HashSet<String> regionSet) {
        for (String s : regionSet) {
            try {
                Files.copy(folderIn.resolve(s), folderOut.resolve(s),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static HashSet<String> getFileNames(Path folder) {
        HashSet<String> nameSet = new HashSet<>();
        Arrays.stream(Objects.requireNonNull(folder.toFile().listFiles()))
                .forEach(fileIn -> nameSet.add(fileIn.getName()));
        return nameSet;
    }
}
