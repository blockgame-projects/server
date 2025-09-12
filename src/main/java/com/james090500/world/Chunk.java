package com.james090500.world;

import com.james090500.BlockGameServer;
import com.james090500.blocks.Block;
import com.james090500.blocks.Blocks;
import com.james090500.blocks.GrassBlock;
import com.james090500.structure.Tree;
import com.james090500.utils.OpenSimplexNoise;
import com.james090500.utils.ThreadUtil;

public class Chunk {

    public final byte[] chunkData;
    
    public final int chunkSize = 16;
    public final int chunkHeight = 300;
    public final int chunkX;
    public final int chunkZ;

    private boolean queued = false;

    public boolean needsMeshing = false;
    public boolean needsSaving = false;

    public ChunkStatus chunkStatus = ChunkStatus.EMPTY;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;

        this.chunkData = new byte[chunkSize * chunkSize * chunkHeight];
    }

    public Chunk(int chunkX, int chunkZ, byte[] chunkData) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.chunkData = chunkData;

        this.chunkStatus = ChunkStatus.FINISHED;
        this.needsMeshing = true;
    }

    /**
     * Gets the index from the byte array
     *
     * @param x The x in the chunk
     * @param y The y in the chunk
     * @param z The z in the chunk
     * @return The index returned (null/-1 if invalid)
     */
    private int getIndex(int x, int y, int z) {
        if (
                x >= this.chunkSize || x < 0 ||
                        y >= this.chunkHeight || y < 0 ||
                        z >= this.chunkSize || z < 0
        ) {
            return -1;
        }

        return x + this.chunkSize * (y + this.chunkHeight * z);
    }

    /**
     * Gets a block in chunk coordinates
     *
     * @param x The x in the chunk
     * @param y The y in the chunk
     * @param z The z in the chunk
     */
    public Block getBlock(int x, int y, int z) {
        if (
                x >= this.chunkSize || x < 0 ||
                        y >= this.chunkHeight || y < 0 ||
                        z >= this.chunkSize || z < 0
        ) {
            return null;
        }

        int index = this.getIndex(x, y, z);
        int blockID = this.chunkData[index];
        return Blocks.ids[blockID];
    }

    /**
     * Sets a block in chunk coordinates
     *
     * @param x The x in the chunk
     * @param y The y in the chunk
     * @param z The z in the chunk
     */
    public void setBlock(int x, int y, int z, byte block) {
        if (
                x >= this.chunkSize || x < 0 ||
                        y >= this.chunkHeight || y < 0 ||
                        z >= this.chunkSize || z < 0
        ) {
            BlockGameServer.getInstance().getWorld().setChunkBlock(chunkX, chunkZ, x, y, z, block);
        } else {
            this.chunkData[this.getIndex(x, y, z)] = block;
        }
    }

    /**
     * Are our neighbours a minimum specific status?
     * @return
     */
    public boolean isNeighbors(ChunkStatus chunkStatus) {
        return BlockGameServer.getInstance().getWorld().isChunkStatus(chunkX + 1, chunkZ, chunkStatus) &&
                BlockGameServer.getInstance().getWorld().isChunkStatus(chunkX - 1,chunkZ, chunkStatus) &&
                BlockGameServer.getInstance().getWorld().isChunkStatus(chunkX, chunkZ + 1, chunkStatus) &&
                BlockGameServer.getInstance().getWorld().isChunkStatus(chunkX, chunkZ - 1, chunkStatus);
    }

    /**
     * Handle chunk generation
     */
    public void generate() {
        if (this.queued || this.chunkStatus == ChunkStatus.FINISHED) return;

        this.queued = true;
        ThreadUtil.getQueue("worldGen").submit(() -> {
            if (this.chunkStatus == ChunkStatus.EMPTY) {
                this.chunkStatus = ChunkStatus.TERRAIN;
                this.generateTerrain();
            } else if (this.chunkStatus == ChunkStatus.TERRAIN) {
                if (isNeighbors(ChunkStatus.EMPTY)) {
                    this.chunkStatus = ChunkStatus.DECORATIONS;
                    this.generateTrees();
                }
            } else if (this.chunkStatus == ChunkStatus.DECORATIONS) {
                // We have finished generation time to mesh
                this.chunkStatus = ChunkStatus.FINISHED;
                this.needsMeshing = true;
            }

            this.queued = false;
        });
    }
    /**
     * Generates the actual terrain
     */
    private void generateTerrain() {
        int waterLevel = 64;

        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                int nx = x + this.chunkX * this.chunkSize;
                int nz = z + this.chunkZ * this.chunkSize;

                boolean beach = false;
                int topSoilDepth = -1;

                for (int y = this.chunkHeight - 1; y >= 0; y--) {
                    // terrain shaping
                    double density = this.octaveNoise3D(nx, y, nz);

                    double heightFactor = (waterLevel - y) / (double) waterLevel;
                    if (y > waterLevel) {
                        if (density > 0.35) {
                            density += heightFactor;
                        } else {
                            density += heightFactor * 2;
                        }
                    } else {
                        density += heightFactor * 2;
                    }

                    byte nextBlock = 0;
                    if (density >= 0) {
                        if (topSoilDepth == -1) {
                            if (y < waterLevel + 2) {
                                nextBlock = Blocks.sandBlock.getId();
                                beach = true;
                            } else {
                                nextBlock = Blocks.grassBlock.getId();
                                beach = false;
                            }
                            topSoilDepth++;
                        } else if (topSoilDepth < 3) {
                            if (beach) {
                                nextBlock = Blocks.sandBlock.getId();
                            } else {
                                nextBlock = Blocks.dirtBlock.getId();
                            }
                            topSoilDepth++;
                        } else {
                            nextBlock = Blocks.stoneBlock.getId();
                        }
                    } else {
                        if (y <= waterLevel) {
                            nextBlock = Blocks.waterBlock.getId();
                        }
                        topSoilDepth = -1;
                    }

                    if (nextBlock != 0) {
                        this.setBlock(x, y, z, nextBlock);
                    }
                }
            }
        }
    }

    /**
     * Generate trees
     */
    private void generateTrees() {
        int treeSeed = BlockGameServer.getInstance().getWorld().getWorldSeed() + 2390; // Don't follow terrain otherwise it looks odd

        for (int x = 0; x < this.chunkSize; x++) {
            for (int z = 0; z < this.chunkSize; z++) {
            double nx = x + this.chunkX * this.chunkSize;
            double nz = z + this.chunkZ * this.chunkSize;

            double noise = OpenSimplexNoise.noise2(treeSeed, nx, nz);

                if (noise > 0.75) {
                    for (int y = this.chunkHeight - 1; y >= 0; y--) {
                    Block block = this.getBlock(x, y, z);
                        if (block instanceof GrassBlock) {
                            Tree tree = new Tree(noise,this);
                            tree.build(x, y, z);
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates 3D octave noise using OpenSimplexNoise (similar to JS octaveNoise3D).
     */
    private double octaveNoise3D(double x, double y, double z) {
        int octaves = 4;
        double persistence = 0.5;
        double lacunarity = 2.0;
        double total = 0;
        double frequency = 0.005;
        double amplitude = 5;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += OpenSimplexNoise.noise3_ImproveXY(
                    BlockGameServer.getInstance().getWorld().getWorldSeed(),
                    x * frequency,
                    y * frequency,
                    z * frequency
            ) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue;
    }

    public void saveChunk() {
        if(this.chunkData != null && this.needsSaving) {
            this.needsSaving = false;
            ThreadUtil.getQueue("worldDisk").submit(() -> BlockGameServer.getInstance().getWorld().saveChunk(this.chunkX, this.chunkZ, this.chunkData));
        }
    }
}