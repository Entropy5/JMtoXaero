package com.github.entropy5;

import com.sun.jndi.toolkit.url.UrlUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converter for Journeymap data to Xaero format in MC version 1.12.2
 * Based mostly on the decompiled code of xaero.map.file.MapSaveLoad
 * Written by IronException, Entropy, lamp and Constructor
 * <a href="https://bananazon.com/books/IronException-And-How-He-Did-Most-Of-The-Work-On-This-Project">...</a>
 */


public class JourneyMapToXaero {
    static String blockToColorPath = "blockstateidtocolor_faithful.txt";  // "blockstateidtocolor.txt"
    public static final HashMap<Integer, Integer> COLOR_TO_STATE = readMapping(blockToColorPath); // THIS HAS TO BE color -> state

    public static void main(final String[] args) {
        if (args.length < 1 || args.length == 2) {
            System.err.println("usage: <input folder> <output folder> <dimension id>");
            System.exit(1);
        }

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
                if (color == -1) {
                    continue;  // Corrupting property
                }
                if (blockID == 2) {
                    color = -10914762;  // Manual grass color fix
//                    System.out.println(color + " is " + new Color(color));
                }
                if (!mapping.containsKey(color)) {
                    mapping.put(color, blockID);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mapping;
    }


    public static int getClosestColor(int color) {
        return COLOR_TO_STATE.keySet().stream().min(Comparator.comparing(i -> calcDistance(i, color >> 16 & 255, color >> 8 & 255, color & 255))).orElse(0);
    }

    private static float calcDistance(final int currentColor, final int desiredRed, final int desiredGreen, final int desiredBlue) {
        final float rDist = desiredRed - (currentColor >> 16 & 255);
        final float gDist = desiredGreen - (currentColor >> 8 & 255);
        final float bDist = desiredBlue - (currentColor & 255);
        return Math.abs(rDist) + Math.abs(gDist) + Math.abs(bDist);
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

        public Map<Object, Object> getOverlays() {
            return Collections.emptyMap();
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
                                for (int x = 0; x < 16; ++x) {
                                    for (int z = 0; z < 16; ++z) {
                                        int relX = 64 * o + 16 * i + x;
                                        int relZ = 64 * p + 16 * j + z;
                                        savePixel(new IronBlock(image.getRGB(relX, relZ), nether), out);
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
//        System.out.println("saving " + pixel.state + " with color " + pixel.jmColor + " converted to " + pixel.closestColor + "grass?" + !pixel.isNotGrass());
//        System.out.println(new Color(pixel.jmColor) + " -> " + new Color(pixel.closestColor));
        final int parameters = pixel.getParameters();
        out.writeInt(parameters);
        if (pixel.isNotGrass()) {
            out.writeInt(pixel.getState());
        }

        int biome;
        if (pixel.getNumberOfOverlays() != 0) {
            out.write(pixel.getOverlays().size());
        }

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
