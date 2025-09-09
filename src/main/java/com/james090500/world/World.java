package com.james090500.world;

import com.james090500.BlockGameServer;
import com.james090500.Player;
import com.james090500.blocks.Block;
import com.james090500.network.packets.ChunkPacket;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class World {

    private final Map<String, Region> regions = new HashMap<>();

    private final HashMap<ChunkPos, Chunk> chunks = new HashMap<>();
    private final Map<ChunkPos, List<BlockPlacement>> deferredBlocks = new HashMap<>();
    
    public record ChunkPos(int x, int y) { }
    public record BlockPlacement(int x, int y, int z, byte blockId) {}
    public record ChunkOffset(int dx, int dz, int distSq) {}

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Getter
    private int worldSeed;
    private final int worldSize = 16;

    @Getter
    private String worldName;

    /**
     * Start a world instance
     * @param name The name, if exists it will load a world otherwise load a new one
     */
    public World(String name) {
        this.worldName = name;
        File worldPath = new File(worldName);
        File worldData = new File(worldPath + "/world.bg");
        if(!worldPath.exists()) {
            // Make world path
            worldPath.mkdirs();

            // Generate seed
            this.worldSeed = (int) Math.floor(Math.random() * Integer.MAX_VALUE);

            // Write to file
            try (RandomAccessFile raf = new RandomAccessFile(worldData, "rw")) {
                raf.writeInt(worldSeed);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try (RandomAccessFile raf = new RandomAccessFile(worldData, "rw")) {
                this.worldSeed = raf.readInt();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        scheduler.scheduleAtFixedRate(this::saveWorld, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Checks whether the player chunk is loaded
     * @return
     */
    public boolean isChunkLoaded(int x, int z) {
        ChunkPos chunkPos = new ChunkPos(x, z);

        if(this.chunks.containsKey(chunkPos)) {
            Chunk chunk = this.chunks.get(chunkPos);
            return chunk.generated;
        }

        return false;
    }

    /**
     * Get a chunk
     * @param x chunkX
     * @param z chunkZ
     * @return The chunk
     */
    public Chunk getChunk(int x, int z) {
        return this.chunks.get(new ChunkPos(x, z));
    }

    /**
     * Get a block via a Vector3f
     * @param pos Position to check
     * @return
     */
    public Block getBlock(Vector3f pos) {
        return getBlock(
                (int) Math.floor(pos.x),
                (int) Math.floor(pos.y),
                (int) Math.floor(pos.z)
        );
    }

    /**
     * Gets a block in the world
     * @param x The world x coord
     * @param y The world y coord
     * @param z The world z coord
     * @return The block or null if no block
     */
    public Block getBlock(int x, int y, int z) {
        return this.getChunkBlock(0, 0, x, y, z);
    }

    /**
     * Sets a block in the world
     * @param x The world x coord
     * @param y The world y coord
     * @param z The world z coord
     * @param block The block or null if no block
     */
    public void setBlock(int x, int y, int z, byte block) {
        this.setChunkBlock(0, 0, x, y, z, block);
    }

    /**
     * Get a block from a specific chunk
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param x The localised x coordinate
     * @param y The localised y coordinate
     * @param z The localised z coordinate
     * @return The block or null if no block
     */
    public Block getChunkBlock(int chunkX, int chunkZ, int x, int y, int z) {
        int offsetChunkX = Math.floorDiv(x, 16);
        chunkX += offsetChunkX;
        x = Math.floorMod(x, 16);

        int offsetChunkZ = Math.floorDiv(z, 16);
        chunkZ += offsetChunkZ;
        z = Math.floorMod(z, 16);

        Chunk target = this.chunks.get(new ChunkPos(chunkX, chunkZ));
        if (target == null) {
            return null;
        }

        return target.getBlock(x, y, z);
    }

    /**
     * Sets a block at a specific chunk
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param x The localised x coordinate
     * @param y The localised y coordinate
     * @param z The localised z coordinate
     * @param block The block
     */
    public void setChunkBlock(int chunkX, int chunkZ, int x, int y, int z, byte block) {
        // Adjust for cross-chunk placement
        int offsetChunkX = Math.floorDiv(x, 16);
        chunkX += offsetChunkX;
        x = Math.floorMod(x, 16);

        int offsetChunkZ = Math.floorDiv(z, 16);
        chunkZ += offsetChunkZ;
        z = Math.floorMod(z, 16);

        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        Chunk target = this.chunks.get(chunkPos);

        if (target == null) {
            synchronized (deferredBlocks) {
                deferredBlocks.computeIfAbsent(chunkPos, k -> new ArrayList<>())
                        .add(new BlockPlacement(x, y, z, block));
            }
            return;
        }

        // Update block and flag for meshing
        target.setBlock(x, y, z, block);
        target.needsSaving = true;
    }

    public List<Chunk> getPlayerChunks(Player player) {
        // Load/generate nearby chunks in render distance
        List<ChunkOffset> offsets = new ArrayList<>();
        for (int dx = -worldSize; dx <= worldSize; dx++) {
            for (int dz = -worldSize; dz <= worldSize; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > worldSize * worldSize) continue;
                offsets.add(new ChunkOffset(dx, dz, distSq));
            }
        }

        offsets.sort(Comparator.comparingInt(ChunkOffset::distSq)); // Closest first

        // Render chunks from players pos.
        List<Chunk> finalChunks = new ArrayList<>();
        for (ChunkOffset offset : offsets) {
            ChunkPos pos = new ChunkPos(player.getLastChunkX() + offset.dx(), player.getLastChunkZ() + offset.dz());
            if (chunks.containsKey(pos)) {
                finalChunks.add(chunks.get(pos));
            }
        }
        return finalChunks;
    }

    /**
     * update the world. This also loads and remove chunks as needed
     */
    public void update() {
        for(Player player : BlockGameServer.getInstance().getPlayers().values()) {

            int playerChunkX = (int) Math.floor(player.getPosition().x / 16);
            int playerChunkZ = (int) Math.floor(player.getPosition().z / 16);

            if (playerChunkX == player.getLastChunkX() && playerChunkZ == player.getLastChunkZ() && !chunks.isEmpty() && !player.getLoadedChunks().isEmpty()) {
                continue;
            }

            System.out.println("No chunks for " + player.getChannel().id());

            player.setLastChunkX(playerChunkX);
            player.setLastChunkZ(playerChunkZ);

            // Load/generate nearby chunks in render distance
            List<ChunkOffset> offsets = new ArrayList<>();
            for (int dx = -worldSize; dx <= worldSize; dx++) {
                for (int dz = -worldSize; dz <= worldSize; dz++) {
                    int distSq = dx * dx + dz * dz;
                    if (distSq > worldSize * worldSize) continue;
                    offsets.add(new ChunkOffset(dx, dz, distSq));
                }
            }

            offsets.sort(Comparator.comparingInt(ChunkOffset::distSq)); // Closest first

            // Render chunks from players pos.
            Set<ChunkPos> requiredChunks = new HashSet<>();
            for (ChunkOffset offset : offsets) {
                ChunkPos pos = new ChunkPos(playerChunkX + offset.dx(), playerChunkZ + offset.dz());
                requiredChunks.add(pos);

                if (!chunks.containsKey(pos)) {
                    List<BlockPlacement> blockPlacements = deferredBlocks.remove(pos);

                    // Try and load data from disk
                    //TODO remove this from main thread as its slow
                    byte[] chunkData = loadChunk(pos.x(), pos.y());

                    // Generate chunk from data or new terrain
                    Chunk newChunk;
                    if (chunkData == null) {
                        newChunk = new Chunk(pos.x(), pos.y(), blockPlacements);
                    } else {
                        newChunk = new Chunk(pos.x(), pos.y(), blockPlacements, chunkData);
                    }

                    chunks.put(pos, newChunk);
                }

                // Send player chunks
                if(!player.getLoadedChunks().contains(pos)) {
                    player.sendPacket(new ChunkPacket(pos.x, pos.y, chunks.get(pos).chunkData));
                    player.getLoadedChunks().add(pos);
                }
            }

            // Remove chunks from player
            player.getLoadedChunks().removeIf(pos -> !requiredChunks.contains(pos));
        }

        // Remove unused chunks if server empty
        if(BlockGameServer.getInstance().getPlayers().isEmpty()) {
            chunks.values().removeIf(chunk -> {
               chunk.saveChunk();
               return true;
            });
        }
    }

    /**
     *
     */
    public int getChunkCount() {
       return chunks.size();
    }

    public Region getRegion(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        String key = regionX + "," + regionZ;

        return regions.computeIfAbsent(key, k -> {
            try {
                return new Region(worldName, regionX, regionZ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void saveWorld() {
        BlockGameServer.getLogger().info("Saving World...");
        for(Chunk chunk : this.chunks.values()) {
            if(chunk.needsSaving) {
                BlockGameServer.getInstance().getWorld().saveChunk(chunk.chunkX, chunk.chunkZ, chunk.chunkData);
                chunk.needsSaving = false;
            }
        }
        BlockGameServer.getLogger().info("Save Complete!");
    }

    public void saveChunk(int chunkX, int chunkZ, byte[] data) {
        try {
            getRegion(chunkX, chunkZ).saveChunk(chunkX, chunkZ, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] loadChunk(int chunkX, int chunkZ) {
        try {
            return getRegion(chunkX, chunkZ).loadChunk(chunkX, chunkZ);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Exit the world
     */
    public void exitWorld() {
        this.scheduler.shutdownNow();
        this.saveWorld();
    }
}
