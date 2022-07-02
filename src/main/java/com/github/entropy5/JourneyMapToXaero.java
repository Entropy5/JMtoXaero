package com.github.entropy5;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converter for Journeymap data to Xaero format in MC version 1.12.2
 * Based mostly on the decompiled code of xaero.map.file.MapSaveLoad
 * Written by IronException, Entropy, lamp and Constructor
 * <a href="https://bananazon.com/books/IronException-And-How-He-Did-Most-Of-The-Work-On-This-Project">...</a>
 */


public class JourneyMapToXaero {
    static String blockToColorPath = "blockstateidtocolor.txt";
    public static final HashSet<Integer> LEAVES = new HashSet<>(Arrays.asList(161, 49170, 24594, 32929, 8210, 18, 57362, 53409, 4257, 16402, 20641, 49313, 32786, 37025, 16545, 40978));
    public static final HashMap<Integer, Integer> COLOR_TO_STATE = new HashMap<>(); // THIS HAS TO BE color -> state
    public static final HashMap<Integer, Integer> CLOSEST_COLOR = new HashMap<>();  // to cache results

    public static void main(final String[] args) {
        if (args.length < 3) {
            System.err.println("usage: <input folder> <output folder> <dimension> (-1, 0, 1, all)");
            System.exit(1);
        }

        // override block to color mapping
        if (args.length > 3 && args[3].startsWith("-m=")) {
            String mapping = args[3].split("-m=")[1];
            blockToColorPath = mapping;
            System.out.println("using " + mapping + " as block to color mapping.");
        }

        COLOR_TO_STATE.putAll(readMapping(blockToColorPath));

        String input = args[0];
        String output = args[1];
        if (!(args[2].equals("all"))) {
            int dimension = Integer.parseInt(args[2]);
            processDimension(input, output, dimension);
        } else {
            for (int i = -1; i < 2; i++) {
                processDimension(input, output, i);
            }
        }
    }

    public static HashMap<Integer, Integer> readMapping(String blockToColorPath) {
        HashMap<Integer, Integer> mapping = new HashMap<>();  // from color to blockstate id
        InputStream is = JourneyMapToXaero.class.getClassLoader().getResourceAsStream(blockToColorPath);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                int blockID = Integer.parseInt(values[0]);
                int color = Integer.parseInt(values[1]);
                if (color == -1 || blockID == 122) {  // 122 is dragon egg, color too different
                    continue;  // Corrupting property
                }
                if (blockID == 2) {
                    color = -10914762;  // Manual gray grass color fix
                }

                if (LEAVES.contains(blockID)) {
                    color = -14399980;  // Manual gray leaves color fix
                }

                if (!mapping.containsKey(color) || blockID < mapping.get(color)) {  // Ensure lowest blockID that matches
                    mapping.put(color, blockID);
                }
            }
//            System.out.println(mapping);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mapping;
    }


    public static int getClosestColor(int color) {
        if (CLOSEST_COLOR.containsKey(color)) {
            try {
                return CLOSEST_COLOR.get(color);
            } catch (NullPointerException e) {
                // oh well I guess this was a multithread issue
            }
        }
        int result = COLOR_TO_STATE.keySet().stream().min(Comparator.comparing(i -> colorDistance(new Color(i), new Color(color)))).orElse(0);
        CLOSEST_COLOR.put(color, result);
        return result;
    }

    private static double colorDistance(Color c1, Color c2)  // https://stackoverflow.com/a/6334454/14748354
    {
        int red1 = c1.getRed();
        int red2 = c2.getRed();
        int rMean = (red1 + red2) >> 1;
        int r = red1 - red2;
        int g = c1.getGreen() - c2.getGreen();
        int b = c1.getBlue() - c2.getBlue();
        return Math.sqrt((((512+rMean)*r*r)>>8) + 4*g*g + (((767-rMean)*b*b)>>8));
    }

    private static void processDimension(String input, String output, int dimension) {
        Path folderIn = new File(String.format("%s/DIM%d/", input, dimension)).toPath();
        Path folderOut = new File(String.format("%s/%s/mw$default/", output, (dimension == 0 ? "null" : "DIM" + dimension))).toPath();

        File folderCheck = new File(String.valueOf(folderOut.toFile()));
        File parentCheck = new File(String.valueOf(folderOut.toFile().getParentFile()));
        if (!parentCheck.exists()) {
            parentCheck.mkdir();
            folderCheck.mkdir();
        } else if (!folderCheck.exists()) {
            folderCheck.mkdir();
        }
        List<String> caveLayers = new ArrayList<>();
        for (int i = 0; i < 16; i++) caveLayers.add(i + "");

        //if nether (we will squash cave layers together and send that image to the colorconverter)
        if (dimension == -1) {
            HashMap<String, HashSet<File>> allCaveFiles = new HashMap<>();   // region file -> all cave file locations
            for (File folder : Objects.requireNonNull(folderIn.toFile().listFiles())) {
                Arrays.stream(Objects.requireNonNull(folder.listFiles()))
                        .forEach(file -> {
                            String layer = file.getParentFile().getName();  // 0, 1, 2, day, night
                            String region = file.getName();  // 1,1.png
                            if (caveLayers.contains(layer)) {  // if this folder is not day, night, or something weird
                                HashSet<File> regionFiles;
                                if (allCaveFiles.containsKey(region)) {
                                    regionFiles = allCaveFiles.get(region);
                                } else {
                                    regionFiles = new HashSet<>();
                                }
                                regionFiles.add(file);
                                allCaveFiles.put(region, regionFiles);
                            }
                        });
            }

            allCaveFiles.keySet().stream().parallel().forEach(region -> {
                List<File> fileList = new ArrayList<>(allCaveFiles.get(region));
                Collections.sort(fileList);

                BufferedImage combined = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                Graphics tempGraphics = combined.getGraphics();
                try {
                    for (File image : fileList) {
                        BufferedImage temp = ImageIO.read(image);
                        tempGraphics.drawImage(temp, 0, 0, null);
                    }
                    String[] parts = region.split("[.,]");
                    processRegion(true, folderOut, combined, parts, fileList.get(0));

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            return;
        }

        // OverWorld and End (normal conversion, caves are ignored)
        folderIn = folderIn.resolve("day");
        Arrays.stream(Objects.requireNonNull(folderIn.toFile().listFiles())).parallel()
                .filter(File::isFile)
                .forEach(file -> {
                    String[] parts = file.getName().split("[.,]");
                    BufferedImage image;
                    try {
                        image = ImageIO.read(file);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    processRegion(false, folderOut, image, parts, file);
                });
    }

    private static void processRegion(boolean nether, Path dimPathOut, BufferedImage file, String[] parts, File location) {
        if (parts.length == 3 && parts[2].equals("png")) {
            //TODO: this should be a thread worker instead. otherwise it will be very laggy (similar thing to mc multiplayer or tab)
            try {
                int rx = Integer.parseInt(parts[0]);
                int rz = Integer.parseInt(parts[1]);
                String zipName = rx + "_" + rz + ".zip";
                File zipFile = dimPathOut.resolve(zipName).toFile();
                new JourneyMapToXaero().saveRegion(file, zipFile, nether);
                System.out.println("Converted " + location.toString().split("journeymap")[1] + " to " + zipFile.toString().split("XaeroWorldMap")[1]);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private static class IronBlock {

        public final int jmColor;
        public final int closestColor;
        private final boolean nether;
        private final int colourType; // -1 will destruct whole chunks, 0 good
        private final int color;
        private final int state;
        private final int biome;


        public IronBlock(final int jmColor, boolean nether) {
            this.jmColor = jmColor;
            this.closestColor = getClosestColor(jmColor);
            this.nether = nether;
            this.state = COLOR_TO_STATE.get(this.closestColor);
            colourType = 0;
            this.color = 0xFF000000;  // xaeros custom color
            biome = 1;// plains
        }

        public int getParameters() {
            final int colourTypeToWrite = this.colourType & 3;
            int height = 64;
            int parameters = (0);
            int light = this.nether ? 15 : 0;
            parameters |= this.isNotGrass() ? 1 : 0;
            parameters |= this.getNumberOfOverlays() != 0 ? 2 : 0;
            parameters |= colourTypeToWrite << 2;
            parameters |= light << 8;
            parameters |= height << 12;
            parameters |= 1048576;
            // ignoring some properties here
            return parameters;
        }

        public boolean isNotGrass() {
            return (this.state & -65536) != 0 || (this.state & 4095) != 2;
        }

        public int getState() {
            return state;
        }

        public int getNumberOfOverlays() {
            return 0;
        }

        public int getColourType() {
            return this.colourType; // ig
        }

        public int getCustomColour() {
            return color;
        }

        public int getBiome() {
            return biome;
        }
    }


    public void saveRegion(final BufferedImage image, File zipFile, boolean nether) {
        try {

            DataOutputStream out = null;

            try {
                final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile.toPath())));
                out = new DataOutputStream(zipOut);
                final ZipEntry e = new ZipEntry("region.xaero");
                zipOut.putNextEntry(e);
                out.write(255);  // mimicking logic from Xaero format
                out.writeInt(4);
                int o = 0;

                while (true) {
                    if (o >= 8) {  // A Region consists of 8 x 8 TileChunks, each size 64
                        zipOut.closeEntry();
                        break;
                    }
                    for (int p = 0; p < 8; ++p) {
                        out.write(o << 4 | p);
                        for (int i = 0; i < 4; ++i) {
                            for (int j = 0; j < 4; ++j) {
                                int voidCount = 0;
                                int[][] rgbChunk = new int[16][16];
                                for (int x = 0; x < 16; ++x) {
                                    for (int z = 0; z < 16; ++z) {
                                        int relX = 64 * o + 16 * i + x;
                                        int relZ = 64 * p + 16 * j + z;
                                        int color = image.getRGB(relX, relZ);
                                        rgbChunk[x][z] = color;
                                        if (color == 0) {
                                            voidCount++;
                                        }
                                    }
                                }
                                if (voidCount == 16 * 16) {
                                    out.writeInt(-1);
                                    continue;
                                }
                                for (int x = 0; x < 16; ++x) {
                                    for (int z = 0; z < 16; ++z) {
                                        savePixel(new IronBlock(rgbChunk[x][z], nether), out);
                                    }
                                }
                                out.write(0); // some version thing
                            }
                        }
                    }
                    ++o;
                }
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private static void savePixel(final IronBlock pixel, final DataOutputStream out) throws IOException {
        final int parameters = pixel.getParameters();
        out.writeInt(parameters);
        if (pixel.isNotGrass()) {
            out.writeInt(pixel.getState());
        }

        int biome;

        if (pixel.getColourType() == 3) {
            out.writeInt(pixel.getCustomColour());
        }

        biome = pixel.getBiome();
        if (biome != -1) {
            if (biome < 255) {
                out.write(pixel.getBiome());
            } else {
                out.write(255);
                out.writeInt(biome);
            }
        }
    }
}
