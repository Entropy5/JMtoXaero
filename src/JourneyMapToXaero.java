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
 * Converter for Journeymap data to Xaero format in MC version 1.12.2
 * Based mostly on the decompiled code of xaero.map.file.MapSaveLoad
 * Written by IronException, Entropy, lamp, P529 and Constructor
 * <a href="https://bananazon.com/books/IronException-And-How-He-Did-Most-Of-The-Work-On-This-Project">...</a>
 */


public class JourneyMapToXaero {

    public static void main(final String[] args) {

        File path_in = new File("D:\\Program Files\\MultiMC\\instances\\2b\\.minecraft\\journeymap\\data\\mp\\p5\\DIM0\\day");
        File path_out = new File("D:\\Program Files\\MultiMC\\instances\\2b\\.minecraft\\XaeroWorldMap\\Multiplayer_masonic.wasteofti.me\\null\\mw$default");

        if (path_in.listFiles() == null) {
            return;
        }
        Arrays.stream(Objects.requireNonNull(path_in.listFiles())).parallel()
                .filter(File::isFile)
                .forEach(file -> {
                    String[] parts = file.getName().split("[.,]");
                    if (parts.length == 3 && parts[2].equals("png")) {
                        // T O D O this should be a thread worker instead. otherwise it will be very laggy (similar thing to mc multiplayer or tab)
                        try {
                            int rx = Integer.parseInt(parts[0]);
                            int rz = Integer.parseInt(parts[1]);
                            String zipName = rx + "_" + rz + ".zip";
                            File zipFile = path_out.toPath().resolve(zipName).toFile();
                            System.out.println(zipFile);
                            BufferedImage image = ImageIO.read(file);  // JourneyMap image IN
                            new JourneyMapToXaero().saveRegion(image, zipFile);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
    }


    private static class IronBlock {

        private final int journeymapColor;
        private final int colourType = 3; // 3 for no complexity

        public IronBlock(final int jmColor) {
            this.journeymapColor = jmColor;
        }


        public int getParameters() {
            final int colourTypeToWrite = this.colourType & 3;
            int height = 64;
            int parameters = (0);
            parameters |= this.getNumberOfOverlays() != 0 ? 2 : 0;
            parameters |= colourTypeToWrite << 2;
            parameters |= height << 12;
            parameters |= 1048576;
            // ignoring some properties here
            return parameters;
        }

        public boolean isGrass() {
            return true;
        }

        public int getState() {
            return 0;  // needs to fit with isGrass... we want that to be false I think
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
        // I was doing some editing here but pushing the values over 255 only made it worse
        return c.getRGB();
    }

    private void savePixel(final IronBlock pixel, final DataOutputStream out) throws IOException {
        final int parameters = pixel.getParameters();
        out.writeInt(parameters);
        if (!pixel.isGrass()) {
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
