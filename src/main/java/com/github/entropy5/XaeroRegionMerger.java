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
            DataInputStream in1 = null;
            DataInputStream in2 = null;
            DataOutputStream out = null;
            int saveVersionA = -1;
            int saveVersionB = -1;
            try {
                try {
                    ZipInputStream zipIn1 = new ZipInputStream(new BufferedInputStream(Files.newInputStream(fileIn1.toPath()), 2048));
                    ZipInputStream zipIn2 = new ZipInputStream(new BufferedInputStream(Files.newInputStream(fileIn2.toPath()), 2048));
                    in1 = new DataInputStream(zipIn1);
                    in2 = new DataInputStream(zipIn2);
                    zipIn1.getNextEntry();
                    zipIn2.getNextEntry();

                    final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(fileOut.toPath())));
                    out = new DataOutputStream(zipOut);
                    final ZipEntry e = new ZipEntry("region.xaero");
                    zipOut.putNextEntry(e);

                    int firstByteA = in1.read();
                    int firstByteB = in2.read();
                    System.out.println("firstbyte: " + firstByteA + ", " + firstByteB);
                    if (firstByteA == 255) {
                        out.write(255);
                        saveVersionA = in1.readInt();
                        saveVersionB = in2.readInt();
                        System.out.println("version: " + saveVersionA + ", " + saveVersionB);
                        if (saveVersionA == 4 && saveVersionB == 4) {
                            out.writeInt(4);
                        } else {
                            throw new RuntimeException(rName + " version problem");
                        }
                        firstByteA = -1;
                        firstByteB = -1;
                    }
                    Process tileProc = Process.BOTH;
                    int tileChunkCoordsA = 0;
                    int tileChunkCoordsB = 0;
                    while (true) {  // Keeps reading TileChunks (max 8x8) until done
                        if (tileProc == Process.A || tileProc == Process.BOTH) {
                            if (firstByteA == -1) {
                                tileChunkCoordsA = in1.read();
                            } else {
                                System.out.println("does this even trigger");
                                tileChunkCoordsA = firstByteA;
                            }
                        }
                        if (tileProc == Process.B || tileProc == Process.BOTH) {
                            if (firstByteB == -1) {
                                tileChunkCoordsB = in2.read();
                            } else {
                                System.out.println("does this even trigger");
                                tileChunkCoordsB = firstByteB;
                            }
                        }
                        if (tileChunkCoordsA == -1 && tileChunkCoordsB == -1) {
                            tileProc = Process.NONE;
//                            out.write(-1);
                        } else if (tileChunkCoordsA == tileChunkCoordsB) {
                            tileProc = Process.BOTH;
                            out.write(tileChunkCoordsA);
                        } else if (tileChunkCoordsB == -1) {
                            tileProc = Process.A;
                            out.write(tileChunkCoordsA);
                        } else if (tileChunkCoordsA == -1){
                            tileProc = Process.B;
                            out.write(tileChunkCoordsB);
                        } else if (tileChunkCoordsA < tileChunkCoordsB) {
                            tileProc = Process.A;
                            out.write(tileChunkCoordsA);
                        } else {
                            tileProc = Process.B;
                            out.write(tileChunkCoordsB);
                        }

                        System.out.println("Getting chunk coords for " + tileProc);
                        System.out.println("ChunkCoords: " + tileChunkCoordsA + ", " + tileChunkCoordsB);
                        if (tileProc == Process.NONE) {  // -1 as chunk coord means its over
                            System.out.println("closing");
                            zipIn1.closeEntry();
                            zipIn2.closeEntry();
                            zipOut.closeEntry();
                            break;
                        }

                        if (tileProc == Process.A || tileProc == Process.BOTH) {
                            firstByteA = -1;
                            System.out.println("Chunk A: " + (tileChunkCoordsA >> 4) + "," + (tileChunkCoordsA & 15));
                        }
                        if (tileProc == Process.B || tileProc == Process.BOTH) {
                            firstByteB = -1;
                            System.out.println("Chunk B: " + (tileChunkCoordsB >> 4) + "," + (tileChunkCoordsB & 15));
                        }

                        for (int i = 0; i < 4; ++i) {
                            for (int j = 0; j < 4; ++j) {
                                if (tileProc == Process.A) {
                                    Integer nextTile = in1.readInt();
                                    passChunk(nextTile, in1, out, true);  // passChunk handles writing the int
                                } else if (tileProc == Process.B) {
                                    Integer nextTile = in2.readInt();
                                    passChunk(nextTile, in2, out, true);
                                } else {
                                    Integer nextTileA = in1.readInt();
                                    Integer nextTileB = in2.readInt();
                                    passChunk(nextTileA, nextTileB, in1, in2, out);  // Deep merge
                                }
                            }
                        }
                    }
                } finally {
                    if (in1 != null) {
                        in1.close();
                    }
                    if (in2 != null) {
                        in2.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void passChunk(Integer nextTile, DataInputStream in, DataOutputStream out, boolean write) throws IOException {
        System.out.println("Passing chunk");
        if (write) {
            out.writeInt(nextTile);
        }
        if (nextTile != -1) {  // Skip empty chunk
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    passPixel(nextTile, in, out, write);
                    nextTile = null;
                }
            }
            int worldInterVersion = in.read();
            if (write) {
                out.write(worldInterVersion);
            }
        }
    }

    private static void passChunk(Integer nextTileA, Integer nextTileB, DataInputStream in1, DataInputStream in2, DataOutputStream out) throws IOException {
        if (nextTileA != -1) {  // A gets priority
            passChunk(nextTileA, in1, out, true);
            if (nextTileB != -1) {
                passChunk(nextTileB, in2, out, false);
            }
        } else if (nextTileB != -1) {
            passChunk(nextTileB, in2, out, true);
        } else {
            out.writeInt(-1);  // if both are empty, write -1
        }
    }


    private static void passPixel(Integer next, DataInputStream in, DataOutputStream out, boolean write) throws IOException {
        int parametres;
        if (next != null) {
            parametres = next;
        } else {
            parametres = in.readInt();
            if (write) {
                out.writeInt(parametres);
            }
        }

        if ((parametres & 1) != 0) {  // likely a grass check
            int state = in.readInt();
            System.out.println("counter: " + counter + " - write: " + write + " - params: " + parametres + " - state: " + state);
            if (write) {
                out.writeInt(state);
            }
        } else {
            System.out.println("grass");
        }
        counter++;

        if ((parametres & 64) != 0) {
            int height = in.read();
            System.out.println("height: " + height);
            if (write) {
                out.write(height);
            }
        }

        if ((parametres & 16777216) != 0) {
            int topHeight = in.read();
            if (write) {
                out.write(topHeight);
            }
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
                passOverlay(in, out, write);
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
            if (biomeByte >= 255) {
                biomeKey = in.readInt();
                if (write) {
                    out.writeInt(biomeKey);
                }
            }
        }
    }


    private static void passOverlay(DataInputStream in, DataOutputStream out, boolean write) throws IOException {
        int parametres = in.readInt();
        if (write) {
            out.writeInt(parametres);
        }
        int state;
        if ((parametres & 1) != 0) {
            state = in.readInt();
            if (write) {
                out.writeInt(state);
            }
        }
        int opacity = 1;
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

    enum Process {
        A,
        B,
        BOTH,
        NONE
    }
}
