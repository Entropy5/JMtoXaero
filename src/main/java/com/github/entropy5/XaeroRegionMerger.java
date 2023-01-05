package com.github.entropy5;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class XaeroRegionMerger {

    public static final HashSet<Integer> GREENS = new HashSet<>(Arrays.asList(2, 161, 49170, 24594, 32929, 8210, 18, 57362, 53409, 4257, 16402, 20641, 49313, 32786, 37025, 16545, 40978));
//    static boolean dark = false;
    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Usage: java -jar XaeroRegionMerger.jar <top folder> <bottom folder> <out folder> <cores (int)> <darken (0/1)>");
            System.out.println("- This command will merge the zip files from two xaero folders and save them in a third folder");
            System.out.println("- The top folder input gets priority over the bottom folder");
            System.out.println("- Darkening makes the bottom folder less bright, so you can mark background map data as *undiscovered*");
            throw new RuntimeException("Incorrect number of arguments");
        }
        final Instant before = Instant.now();
        // First folder gets pixel priority, put the important stuff here
        Path firstFolderIn = new File(args[0]).toPath();
        Path secondFolderIn = new File(args[1]).toPath();
        Path folderOut = new File(args[2]).toPath();
        int parallelism = Integer.parseInt(args[3]);
        boolean dark = (Integer.parseInt(args[4]) == 1);  // stain the background darker

        System.out.println("First folder: " + firstFolderIn);
        System.out.println("Second folder: " + secondFolderIn);
        System.out.println("Folder out: " + folderOut);
        System.out.println("Threads: " + parallelism);
        System.out.println("Darkening: " + dark);

        HashSet<String> firstSet = getFileNames(firstFolderIn);
        HashSet<String> secondSet = getFileNames(secondFolderIn);

        HashSet<String> onlyFirst = new HashSet<>(firstSet);
        onlyFirst.removeAll(secondSet);  // Difference
        System.out.println("Only present in first: " + onlyFirst);
        copyFull(firstFolderIn, folderOut, onlyFirst);

        HashSet<String> onlySecond = new HashSet<>(secondSet);
        onlySecond.removeAll(firstSet);  // Difference
        System.out.println("Only present in second: " + onlySecond);
        if (dark) {
            deepMerge(secondFolderIn, secondFolderIn, folderOut, onlySecond, false, parallelism, true);
        } else {
            copyFull(secondFolderIn, folderOut, onlySecond);
        }
        Instant afterDarken = Instant.now();
        long secondsToDarken = afterDarken.getEpochSecond() - before.getEpochSecond();
        System.out.println("Completed darkening in: " + (secondsToDarken / 60) + " minutes");

        HashSet<String> inter = new HashSet<>(firstSet);
        inter.retainAll(secondSet);  // Intersection
        System.out.println("Need to deep merge: " + inter);
        deepMerge(firstFolderIn, secondFolderIn, folderOut, inter, true, parallelism, dark);
        Instant afterChunkMerge = Instant.now();
        long secondsToDeepMerge = afterChunkMerge.getEpochSecond() - before.getEpochSecond();
        System.out.println("Completed deep merge in: " + (secondsToDeepMerge / 60) + " minutes");
    }

    private static void deepMerge(Path inp1, Path inp2, Path outp, HashSet<String> rNames, boolean first, int parallelism, boolean dark) {
        final ExecutorService executorService = Executors.newFixedThreadPool(parallelism);
        List<Callable<Object>> tasks = rNames.stream()
                .map(rName -> Executors.callable(() -> mergeRegion(inp1, inp2, outp, rName, first, dark)))
                .collect(Collectors.toList());
        try {
            executorService.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdown();
        }
    }

    private static void mergeRegion(Path inp1, Path inp2, Path outp, String rName, boolean first, boolean dark) {
        if (first) { // todo: refactor this scuffed var
            System.out.println("Merging " + rName);
        } else {
            System.out.println("Darkening " + rName);
        }
        File fileIn1 = inp1.resolve(rName).toFile();
        File fileIn2 = inp2.resolve(rName).toFile();
        File fileOut = outp.resolve(rName).toFile();
        DataInputStream in1 = null;
        DataInputStream in2 = null;
        DataOutputStream out = null;
        int saveVersionA;
        int saveVersionB;

        // todo: migrate to try with resources sanely
        try {
            try {
                if (first) {
                    in1 = new DataInputStream(new ByteArrayInputStream(decompressZipToBytes(fileIn1.toPath())));
                }
                in2 = new DataInputStream(new ByteArrayInputStream(decompressZipToBytes(fileIn2.toPath())));

                final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(fileOut.toPath())));
                final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                out = new DataOutputStream(byteOut);
                final ZipEntry e = new ZipEntry("region.xaero");
                zipOut.putNextEntry(e);

                int firstByteA = first ? in1.read() : 255;
                int firstByteB = in2.read();
                if (firstByteA == 255) {
                    out.write(255);
                    saveVersionA = first ? in1.readInt() : 4;
                    saveVersionB = in2.readInt();
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
                            tileChunkCoordsA = first ? in1.read() : -1;  // if first is false then this will make it skip
                        } else {
                            tileChunkCoordsA = firstByteA;
                        }
                    }
                    if (tileProc == Process.B || tileProc == Process.BOTH) {
                        if (firstByteB == -1) {
                            tileChunkCoordsB = in2.read();
                        } else {
                            tileChunkCoordsB = firstByteB;
                        }
                    }
                    if (tileChunkCoordsA == -1 && tileChunkCoordsB == -1) {
                        tileProc = Process.NONE;
                    } else if (tileChunkCoordsA == tileChunkCoordsB) {
                        tileProc = Process.BOTH;
                        out.write(tileChunkCoordsA);
                    } else if (tileChunkCoordsB == -1) {
                        tileProc = Process.A;
                        out.write(tileChunkCoordsA);
                    } else if (tileChunkCoordsA == -1) {
                        tileProc = Process.B;
                        out.write(tileChunkCoordsB);
                    } else if (tileChunkCoordsA < tileChunkCoordsB) {
                        tileProc = Process.A;
                        out.write(tileChunkCoordsA);
                    } else {
                        tileProc = Process.B;
                        out.write(tileChunkCoordsB);
                    }

                    if (tileProc == Process.NONE) {  // -1 as chunk coord means its over
                        zipOut.write(byteOut.toByteArray());
                        zipOut.closeEntry();
                        zipOut.close();
                        break;
                    }

                    if (tileProc == Process.A || tileProc == Process.BOTH) {
                        firstByteA = -1;
                    }
                    if (tileProc == Process.B || tileProc == Process.BOTH) {
                        firstByteB = -1;
                    }

                    for (int i = 0; i < 4; ++i) {
                        for (int j = 0; j < 4; ++j) {
                            if (tileProc == Process.A) {
                                Integer nextTile = in1.readInt();
                                passChunk(nextTile, in1, out, true, false, dark);  // passChunk handles writing the int
                            } else if (tileProc == Process.B) {
                                Integer nextTile = in2.readInt();
                                passChunk(nextTile, in2, out, true, true, dark);
                            } else {
                                Integer nextTileA = in1.readInt();
                                Integer nextTileB = in2.readInt();
                                passChunkComplex(nextTileA, nextTileB, in1, in2, out, dark);  // Deep merge
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

    private static void passChunk(Integer nextTile, DataInputStream in, DataOutputStream out, boolean write, boolean darken, boolean dark) throws IOException {
        darken = dark && darken;
        if (write) {
            if (darken) {
                out.writeInt(nextTile | 3 << 2);  // darken needs color type 3
            } else {
                out.writeInt(nextTile);
            }
        }
        if (nextTile != -1) {  // Skip empty chunk
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    passPixel(nextTile, in, out, write, darken);
                    nextTile = null;
                }
            }
            int worldInterVersion = in.read();
            if (write) {
                out.write(worldInterVersion);
            }
        }
    }

    private static byte[] decompressZipToBytes(final Path input) {
        try {
            return toUnzippedByteArray(Files.readAllBytes(input));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toUnzippedByteArray(byte[] zippedBytes) throws IOException {
        final ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zippedBytes));
        final byte[] buff = new byte[1024];
        if (zipInputStream.getNextEntry() != null) {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int l;
            while ((l = zipInputStream.read(buff)) > 0) {
                outputStream.write(buff, 0, l);
            }
            return outputStream.toByteArray();
        }
        return new byte[0];
    }

    private static void passChunkComplex(Integer nextTileA, Integer nextTileB, DataInputStream in1, DataInputStream in2, DataOutputStream out, boolean dark) throws IOException {
        if (nextTileA != -1) {  // A gets priority
            passChunk(nextTileA, in1, out, true, false, dark);
            if (nextTileB != -1) {
                passChunk(nextTileB, in2, out, false, false, dark);
            }
        } else if (nextTileB != -1) {
            passChunk(nextTileB, in2, out, true, true, dark);
        } else {
            out.writeInt(-1);  // if both are empty, write -1
        }
    }

    private static void passPixel(Integer next, DataInputStream in, DataOutputStream out, boolean write, boolean darken) throws IOException {
        boolean green = false;
        int parametres;
        if (next != null) {
            parametres = next;
        } else {
            parametres = in.readInt();
            if (write) {
                if (darken) {
                    out.writeInt(parametres | 3 << 2);  // color type 3
                } else {
                    out.writeInt(parametres);
                }
            }
        }

        if ((parametres & 1) != 0) {  // likely a grass check
            int state = in.readInt();
            if (write) {
                out.writeInt(state);
                if (darken & GREENS.contains(state)) {
                    green = true;
                }
            }
        } else if (darken) {
            green = true;
        }

        if ((parametres & 64) != 0) {
            int height = in.read();
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
            if (write) {
                out.write(savedColourType);
            }

            for (biomeKey = 0; biomeKey < savedColourType; ++biomeKey) {
                passOverlay(in, out, write);
            }
        }
        savedColourType = parametres >> 2 & 3;
        if (savedColourType == 3) {
            int customColour = in.readInt();
            if (write) {
                if (darken) {
                    writeColor(out, green);
                } else {
                    out.writeInt(customColour);
                }
            }
        }
        if (darken && write) {
            writeColor(out, green);
        }

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

    private static void writeColor(DataOutputStream out, boolean green) throws IOException {
        if (green) {
            out.writeInt(5732666);  // green
        } else {
            out.writeInt(9868950);  // gray
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
        int opacity;
        byte savedColourType = (byte) (parametres >> 8 & 3);
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
        System.out.println("Copying " + regionSet.size() + " regions");
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
