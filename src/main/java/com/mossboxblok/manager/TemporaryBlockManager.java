package com.mossboxblok.manager;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class TemporaryBlockManager {

    private static final long CLEANUP_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private final Map<BlockKey, ScheduledBlock> scheduledBlocks = new HashMap<>();
    private final Map<ChunkKey, Set<BlockKey>> blocksByChunk = new HashMap<>();
    private final PriorityQueue<ExpiryEntry> expiryQueue = new PriorityQueue<>(
            Comparator.comparingLong(ExpiryEntry::expiresAtTick).thenComparingLong(ExpiryEntry::id)
    );
    private BukkitTask cleanupTask;
    private long elapsedTicks;
    private long sequence;

    public TemporaryBlockManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void scheduleRemoval(Block block, int seconds) {
        BlockKey key = BlockKey.from(block);
        ChunkKey chunkKey = ChunkKey.from(block.getChunk());
        Material material = block.getType();
        long id = ++sequence;
        long expiresAtTick = elapsedTicks + seconds * 20L;

        ScheduledBlock previous = scheduledBlocks.put(key, new ScheduledBlock(id, material, expiresAtTick));
        if (previous != null) {
            removeFromChunkIndex(key);
        }

        blocksByChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(key);
        expiryQueue.add(new ExpiryEntry(key, id, expiresAtTick));
        ensureCleanupTask();
    }

    public void cancelRemoval(Block block) {
        BlockKey key = BlockKey.from(block);
        ScheduledBlock scheduledBlock = scheduledBlocks.remove(key);
        if (scheduledBlock == null) {
            return;
        }

        removeFromChunkIndex(key);
        stopCleanupTaskIfIdle();
    }

    public void removeTemporaryBlocksInChunk(Chunk chunk) {
        ChunkKey chunkKey = ChunkKey.from(chunk);
        Set<BlockKey> keys = blocksByChunk.remove(chunkKey);
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (BlockKey key : keys) {
            ScheduledBlock scheduledBlock = scheduledBlocks.remove(key);
            if (scheduledBlock == null) {
                continue;
            }

            setAirIfStillTemporary(key, scheduledBlock.material());
        }
        stopCleanupTaskIfIdle();
    }

    public void clearAndRemoveAll() {
        stopCleanupTask();
        for (var entry : scheduledBlocks.entrySet()) {
            setAirIfStillTemporary(entry.getKey(), entry.getValue().material());
        }

        scheduledBlocks.clear();
        blocksByChunk.clear();
        expiryQueue.clear();
    }

    private void ensureCleanupTask() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            return;
        }

        cleanupTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::removeExpiredBlocks,
                CLEANUP_INTERVAL_TICKS,
                CLEANUP_INTERVAL_TICKS
        );
    }

    private void removeExpiredBlocks() {
        elapsedTicks += CLEANUP_INTERVAL_TICKS;

        while (!expiryQueue.isEmpty() && expiryQueue.peek().expiresAtTick() <= elapsedTicks) {
            ExpiryEntry entry = expiryQueue.poll();
            ScheduledBlock scheduledBlock = scheduledBlocks.get(entry.key());
            if (scheduledBlock == null
                    || scheduledBlock.id() != entry.id()
                    || scheduledBlock.expiresAtTick() != entry.expiresAtTick()) {
                continue;
            }

            scheduledBlocks.remove(entry.key());
            removeFromChunkIndex(entry.key());
            setAirIfStillTemporary(entry.key(), scheduledBlock.material());
        }

        stopCleanupTaskIfIdle();
    }

    private void stopCleanupTaskIfIdle() {
        if (!scheduledBlocks.isEmpty()) {
            compactExpiryQueueIfNeeded();
            return;
        }

        stopCleanupTask();
        expiryQueue.clear();
    }

    private void stopCleanupTask() {
        if (cleanupTask == null) {
            return;
        }

        cleanupTask.cancel();
        cleanupTask = null;
    }

    private void compactExpiryQueueIfNeeded() {
        if (expiryQueue.size() <= scheduledBlocks.size() * 2 + 256) {
            return;
        }

        expiryQueue.clear();
        for (var entry : scheduledBlocks.entrySet()) {
            ScheduledBlock scheduledBlock = entry.getValue();
            expiryQueue.add(new ExpiryEntry(entry.getKey(), scheduledBlock.id(), scheduledBlock.expiresAtTick()));
        }
    }

    private void setAirIfStillTemporary(BlockKey key, Material expectedMaterial) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
            return;
        }

        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        // Avoid deleting a different block placed at the same location later.
        if (block.getType() == expectedMaterial) {
            block.setType(Material.AIR, false);
        }
    }

    private void removeFromChunkIndex(BlockKey key) {
        ChunkKey chunkKey = new ChunkKey(key.worldId(), key.chunkX(), key.chunkZ());
        Set<BlockKey> keys = blocksByChunk.get(chunkKey);
        if (keys == null) {
            return;
        }

        keys.remove(key);
        if (keys.isEmpty()) {
            blocksByChunk.remove(chunkKey, keys);
        }
    }

    private record ScheduledBlock(long id, Material material, long expiresAtTick) {
    }

    private record ExpiryEntry(BlockKey key, long id, long expiresAtTick) {
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {

        static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        int chunkX() {
            return x >> 4;
        }

        int chunkZ() {
            return z >> 4;
        }
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {

        static ChunkKey from(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }
}
