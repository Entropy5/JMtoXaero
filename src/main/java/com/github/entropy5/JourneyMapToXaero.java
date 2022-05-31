package com.github.entropy5;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converter for Journeymap data to Xaero format in MC version 1.12.2
 * Based mostly on the decompiled code of xaero.map.file.MapSaveLoad
 * Written by IronException, Entropy, lamp, P529 and Constructor
 * <a href="https://bananazon.com/books/IronException-And-How-He-Did-Most-Of-The-Work-On-This-Project">...</a>
 */


public class JourneyMapToXaero {

    public static void main(final String[] args) {

        File folder_in = new File("D:\\Program Files\\MultiMC\\instances\\2b\\.minecraft\\journeymap\\data\\mp\\p5");
        File folder_out = new File("D:\\Program Files\\MultiMC\\instances\\2b\\.minecraft\\XaeroWorldMap\\Multiplayer_masonic.wasteofti.me");

        Path dim_path_in = folder_in.toPath().resolve("DIM0\\day");
        Path dim_path_out = folder_out.toPath().resolve("null\\mw$default");

        File [] files = dim_path_in.toFile().listFiles();
        assert files != null;
        for (File file : files) {
            if (file.isFile()) {
                String[] parts = file.getName().split("[.,]");
                if (parts.length == 3 && parts[2].equals("png")) {
                    // T O D O this should be a thread worker instead. otherwise it will be very laggy (similar thing to mc multiplayer or tab)
                    try {
                        int rx = Integer.parseInt(parts[0]);
                        int rz = Integer.parseInt(parts[1]);
                        String zipName = rx + "_" + rz + ".zip";
                        File zipFile = dim_path_out.resolve(zipName).toFile();
                        BufferedImage image = ImageIO.read(file);  // JourneyMap image IN
                        new JourneyMapToXaero().saveRegion(image, zipFile);
                        System.out.println("Converted " + file.toString().split("journeymap")[1] + " to " + zipFile.toString().split("XaeroWorldMap")[1]);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    private static class IronBlock {

        private final int journeymapColor;

        private final boolean caveBlock = false;
        private final int colourType = 3; // 3 for no complexity

        public IronBlock(final int jmColor) {
            this.journeymapColor = jmColor;
        }


        public int getParameters() {
            final int colourTypeToWrite = this.colourType & 3;
            int height = 64;
            int parameters = (0);
            parameters |= this.isNotGrass() ? 1 : 0;
            parameters |= this.getNumberOfOverlays() != 0 ? 2 : 0;
            parameters |= colourTypeToWrite << 2;
            parameters |= this.caveBlock ? 128 : 0;
            parameters |= height << 12;
            parameters |= 1048576;
            // ignoring some properties here
            return parameters;
        }

        public boolean isNotGrass() {
            return true;
        }

        public int getState() {
            return 42;  // iron block seems to be the best blank slate to map color on, also the solution to life
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
            return this.journeymapColor;
        }

        public int getBiome() {
            return 1; // plains
        }
    }


    public void saveRegion(final BufferedImage image, File zipFile) {
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
                                        this.savePixel(new IronBlock(image.getRGB(relX, relZ)), out);
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
            System.out.println("IO exception while trying to save " + " " + e);
        }
    }

    private void savePixel(final IronBlock pixel, final DataOutputStream out) throws IOException {
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
