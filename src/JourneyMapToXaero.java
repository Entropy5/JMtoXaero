import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converter for Journeymap data to Xaero format
 * Based mostly on the decompiled code of xaero.map.file.MapSaveLoad
 * Written by IronException, Entropy, lamp, P529 and Constructor
 * https://bananazon.com/books/IronException-And-How-He-Did-Most-Of-The-Work-On-This-Project
 * */


public class JourneyMapToXaero {

    public static void main(final String[] args) {
        File path = new File("C:\\Users\\mcmic\\Downloads");
        if (path.listFiles() == null) {
            return;
        }
        Arrays.stream(Objects.requireNonNull(path.listFiles())).parallel()
                .filter(File::isFile)
                .forEach(file -> {
                    String[] parts = file.getName().split("[.,]");
                    if (parts.length == 3 && parts[2].equals("png")) {
                        // T O D O this should be a thread worker instead. otherwise it will be very laggy (similar thing to mc multiplayer or tab)
//                        new Thread(() -> {
                        try {
                            int rx = Integer.parseInt(parts[0]);
                            int rz = Integer.parseInt(parts[1]);
                            String zipName = rx + "_" + rz + ".zip";
                            File zipFile = file.toPath().getParent().resolve(zipName).toFile();
                            System.out.println(zipFile);
                            BufferedImage image = ImageIO.read(file);  // JourneyMap image IN
                            new JourneyMapToXaero().saveRegion(image, zipFile);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
//                        }, file.getName()).start();
                    }
                });

    }


    private static class IronBlock {

        private final int journeymapColor;
        private final int state = 0;// needs to fit with isGrass... we want that to be false I think
        private final int colourType = 3; // 3 for no complexity
        private final int biome = 1;

        private final int light = 0;

        private final int height = 64;

        private final int signed_height = 64;

        private final int signed_topHeight = 64;

        private final boolean caveBlock = false;

        public IronBlock(final int jmColor) {
            this.journeymapColor = jmColor;
        }


        public int getParameters() {
            final int colourTypeToWrite = this.colourType < 0 ? 0 : this.colourType & 3;
            int parameters = (0);
            parameters |= this.getNumberOfOverlays() != 0 ? 2 : 0;
            parameters |= colourTypeToWrite << 2;
            parameters |= this.caveBlock ? 128 : 0;
            parameters |= this.light << 8;
            parameters |= this.height << 12;
            parameters |= this.biome != -1 ? 1048576 : 0;
            parameters |= this.signed_height != this.signed_topHeight ? 16777216 : 0;

            // ignoring properties grass (false), height (0), signed height (0)
            return parameters;
        }

        public boolean isGrass() {
            return true; // was: (this.state & -65536) == 0 && (this.state & 4095) == 2;
        }

        public int getState() {
            return this.state;
        }

        public int getNumberOfOverlays() {
            return 0; // we prly dont need that?
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
            return this.biome; // thats prly plains..
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
                out.write(255);  // Black box logic from Xaero format and code
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
                                        this.savePixel(new IronBlock(transform(image.getRGB(relX, relZ))), out);
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

    public int transform(final int in) {
        // Color correction to make JM & Xaero colors match
        Color c = new Color(in);
//        int r = c.getRed();
//        int g = c.getGreen();
//        int b = c.getBlue();
//        int brightness = (r + b + g) / 3 / 255;
//
//        Color out = new Color((int) (1. * r) & 255, (int) (1. * g) & 255, (int) (1. * b) & 255, c.getAlpha());
        return c.getRGB();
    }

    private void savePixel(final IronBlock pixel, final DataOutputStream out) throws IOException {
        final int parameters = pixel.getParameters();
        out.writeInt(parameters);
        if (!pixel.isGrass()) {
            out.writeInt(pixel.getState());
        }

        if ((parameters & 16777216) != 0) {
            out.write(pixel.height);
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
