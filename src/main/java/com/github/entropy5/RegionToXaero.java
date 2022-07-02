package com.github.entropy5;

import br.com.gamemods.nbtmanipulator.NbtCompound;
import br.com.gamemods.nbtmanipulator.NbtList;
import br.com.gamemods.regionmanipulator.*;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class RegionToXaero {

    public static HashSet<Byte> TRANSPARENT = new HashSet<>(Arrays.asList((byte) 0, (byte) 20, (byte) 102, (byte) 95, (byte) 160, (byte) 31, (byte) 31, (byte) 175, (byte) 166, (byte) 132));

    public static void main(String[] args) {
        Path folderIn = new File("C:\\Users\\mcmic\\Downloads\\in").toPath();
        Path folderOut = new File("D:\\Program Files\\MultiMC\\instances\\2b\\.minecraft\\XaeroWorldMap\\Multiplayer_masonic.wasteofti.me\\null\\mw$default").toPath();

        Arrays.stream(Objects.requireNonNull(folderIn.toFile().listFiles()))
                .forEach(fileIn -> {
                    System.out.println(fileIn.getName());
                    String[] parts = fileIn.getName().split("\\.");
                    int rx = Integer.parseInt(parts[1]);
                    int rz = Integer.parseInt(parts[2]);
                    String zipName = rx + "_" + rz + ".zip";
                    File zipFile = folderOut.resolve(zipName).toFile();
                    try {
                        saveRegion(fileIn, zipFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    public static void saveRegion(File fileIn, File zipFile) {
        try {

            Region region = RegionIO.readRegion(fileIn);
            RegionPos rPos = region.getPosition();

            DataOutputStream out = null;
            try {
                final ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipFile.toPath())));
                out = new DataOutputStream(zipOut);
                final ZipEntry e = new ZipEntry("region.xaero");
                zipOut.putNextEntry(e);
                out.write(255);  // mimicking logic from Xaero format
                out.writeInt(4);

                for (int o = 0; o < 8; ++o) {
                    for (int p = 0; p < 8; ++p) {
                        out.write(o << 4 | p);
                        for (int i = 0; i < 4; ++i) {
                            for (int j = 0; j < 4; ++j) {
                                int chunkX = 4 * o + i;  // 0 to 32
                                int chunkZ = 4 * p + j;  // 0 to 32

                                ChunkPos cPos = new ChunkPos((rPos.getXPos() << 5) + chunkX, (rPos.getZPos() << 5) + chunkZ);
                                Chunk chunk = region.get(cPos);
                                NbtList<NbtCompound> sectionList = null;
                                if (chunk == null) {
                                    out.writeInt(-1);  // Xaero format expects this to skip a chunk
                                } else {
                                    int[][] chunkBlocks = new int[16][16];
                                    int[][] chunkHeight = new int[16][16];
                                    int[][] chunkLight = new int[16][16];
                                    int[][] chunkBiome = new int[16][16]; // no clue how to pull biome tho

                                    byte[] byteBiomes = chunk.getLevel().getByteArray("Biomes");
                                    sectionList = chunk.getLevel().getCompoundList("Sections");
                                    for (int si = 0; si < sectionList.getSize(); si++) {  // upwards through sections
                                        NbtCompound section = sectionList.get(si);
                                        byte[] byteBlocks = section.getByteArray("Blocks");  // 16 x 16 x 16 block ids
                                        byte[] byteLight = section.getByteArray("BlockLight");  // half size array
                                        byte[] byteData = section.getByteArray("Data");  // half size array

                                        int counter = 0;
                                        for (int yy = 0; yy < 16; yy++) {  // upwards inside section
                                            for (int zz = 0; zz < 16; zz++) {
                                                for (int xx = 0; xx < 16; xx++) {
                                                    byte blockByte = byteBlocks[counter];
                                                    if (!TRANSPARENT.contains(blockByte)) {  // ignore air on top
                                                        int dataByte = counter % 2 == 0 ? (byteData[counter / 2] & 0x0F) : ((byteData[counter / 2] >> 4) & 0x0F);
                                                        int lightByte = counter % 2 == 0 ? (byteLight[counter / 2] & 0x0F) : ((byteLight[counter / 2] >> 4) & 0x0F);
                                                        int biomeByte = byteBiomes[16 * zz + xx];
                                                        chunkBlocks[xx][zz] = (blockByte & 0xFF) + (dataByte << 12);
                                                        chunkHeight[xx][zz] = 16 * si + yy;
                                                        chunkLight[xx][zz] = lightByte;
                                                        chunkBiome[xx][zz] = biomeByte;
                                                    }
                                                    counter++;
                                                }
                                            }
                                        }
                                    }

                                    for (int x = 0; x < 16; ++x) {
                                        for (int z = 0; z < 16; ++z) {
                                            savePixel(new IronBlock(chunkBlocks[x][z], chunkHeight[x][z], chunkLight[x][z], chunkBiome[x][z]), out);  // PLACEHOLDER
                                        }
                                    }
                                    out.write(0); // some version thing
                                }
                            }
                        }
                    }
                }
                zipOut.closeEntry();
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }


    private static class IronBlock {

        private final int colourType; // -1 will destruct whole chunks, 0 good
        private final int color;
        private final int light;
        private final int height;
        private final int state;
        private final int biome;


        public IronBlock(final int blockState, final int height, final int light, final int biome) {
            this.state = blockState;  // BLOCKSTATE ID
            this.height = height;
            this.light = light;
            this.colourType = 0;
            this.color = 0xFF000000;  // xaeros custom color
            this.biome = biome;  // plains
        }

        public int getParameters() {
            final int colourTypeToWrite = this.colourType & 3;
            int parameters = (0);
            parameters |= this.isNotGrass() ? 1 : 0;
            parameters |= this.getNumberOfOverlays() != 0 ? 2 : 0;
            parameters |= colourTypeToWrite << 2;
            parameters |= this.light << 8;
            parameters |= this.height << 12;
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
