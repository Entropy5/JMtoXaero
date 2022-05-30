import xaero.map.misc.Misc;
import xaero.map.region.BranchLeveledRegion;
import xaero.map.region.LeveledRegion;
import xaero.map.region.Overlay;

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
 * Converter for Journeymap data to Xaero format
 * Based mostly on the decompiled code of xaero.map.file.MapSaveLoad
 * Written by IronException and Negative_Entropy
 */

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
                        // TODO this should be a thread worker instead. otherwise it will be very laggy (similar thing to mc multiplayer or tab)
                        new Thread(() -> {
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
                        }, file.getName()).start();
                    }
                });

    }

    private static class IronChunk {

        public boolean includeInSave() {
            return true;
        }

        public boolean hasHighlightsIfUndiscovered() {
            return false; // TODO not sure about that
        }

        public LeveledRegion getLeafTexture() {
            return null;
        }

        public IronTile getTile(final int i, final int j) {
            return new IronTile();
        }

    }

    private static class IronTile {

        public boolean isLoaded() {
            return true;
        }

        public int getWorldInterpretationVersion() {
            return 0; // in code its that at least... :shrugging:
        }
    }

    private static class IronBlock {

        private final int journeymapColor;
        private final int state = 0;// TODO needs to fit with isGrass... we want that to be false I think
        private final int colourType = 3; // 3 for no complexity
        private final int biome = 1;

        private final int light = 7;  // min light default from xaero

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
            return true; // TODO not sure this is good? (this.state & -65536) == 0 && (this.state & 4095) == 2;
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
            return this.biome; // thats prly plaines..
        }
    }


    public boolean saveRegion(final IronMapRegion region, File zipFile, final int extraAttempts) {
        try {
            final File file = getTempFile(zipFile);
            if (!file.exists()) {
                file.createNewFile();
            }

            boolean regionIsEmpty = true;
            DataOutputStream out = null;

            try {
                final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
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
                        final IronChunk chunk = region.getChunk();
                        if (chunk != null) {
                            if (!chunk.includeInSave()) {
                                if (!chunk.hasHighlightsIfUndiscovered()) {
                                    region.setChunk(o, p);
                                    synchronized (chunk) {
                                        chunk.getLeafTexture().deleteTexturesAndBuffers();
                                    }
                                }

                                final BranchLeveledRegion parentRegion = region.getParent();
                                if (parentRegion != null) {
                                    parentRegion.setShouldCheckForUpdatesRecursive(true);
                                }
                            } else {
                                out.write(o << 4 | p);
                                boolean chunkIsEmpty = true;

                        for (int i = 0; i < 4; ++i) {
                            for (int j = 0; j < 4; ++j) {


                                            for (int x = 0; x < 16; ++x) {

                                                for (int z = 0; z < 16; ++z) {
                                                    int relX = 64 * o + 16 * i + x;
                                                    int relZ = 64 * p + 16 * j + z;
                                                    int color = region.image.getRGB(relX, relZ);
                                                    IronBlock b = new IronBlock(color);
                                                    this.savePixel(b, out);
                                                }
                                            }

                                            out.write(tile.getWorldInterpretationVersion());
                                        } else {
                                            out.writeInt(-1);
                                        }
                                    }
                                }

                                if (!chunkIsEmpty) {
                                    regionIsEmpty = false;
                                }
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

            if (regionIsEmpty) {
                this.safeDelete(zipFile.toPath(), ".zip");
                this.safeDelete(file.toPath(), ".temp");
                //    if (xaero.map.WorldMap.settings.debug) {
                System.out.println("Save cancelled because the region is empty: " + region /**+ " " + region.getWorldId() + " " + region.getDimId() + " " + region.getMwId()*/);
                //   }

                return false;
            } else {
                this.safeMoveAndReplace(file.toPath(), zipFile.toPath(), ".temp", ".zip");
                //      if (xaero.map.WorldMap.settings.debug) {
                System.out.println("Region saved: " + region + " " /**+ region.getWorldId() + " " + region.getDimId() + " " + region.getMwId() + ", " *+ this.mapProcessor.getMapWriter().getUpdateCounter()*/);
                // }

                return true;
            }
        } catch (final IOException var28) {
            System.out.println("IO exception while trying to save " + region
                    + " " + var28);
            if (extraAttempts > 0) {
                System.out.println("(World Map) Retrying...");

                try {
                    Thread.sleep(20L);
                } catch (final InterruptedException var25) {
                }

                return this.saveRegion(region, zipFile, extraAttempts - 1);
            } else {
                return true;
            }
        }
    }

    public void safeDelete(final Path filePath, final String extension) throws IOException {
        if (!filePath.getFileName().toString().endsWith(extension)) {
            throw new RuntimeException("Incorrect file extension: " + filePath);
        } else {
            Files.deleteIfExists(filePath);
        }
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

            for (biome = 0; biome < pixel.getOverlays().size(); ++biome) {
                this.saveOverlay((Overlay) pixel.getOverlays().get(biome), out);
            }
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

    private void saveOverlay(final Overlay o, final DataOutputStream out) throws IOException {
        out.writeInt(o.getParametres());
        if (!o.isWater()) {
            out.writeInt(o.getState());
        }

        if (o.getColourType() == 3) {
            out.writeInt(o.getCustomColour());
        }

        if (o.getOpacity() > 1) {
            out.writeInt(o.getOpacity());
        }

    }

}
