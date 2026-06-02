package com.mossboxblok.listener;

import com.mossboxblok.MossBoxBlokPlugin;
import com.mossboxblok.config.MossBoxBlokSettings.FeedbackChannel;
import com.mossboxblok.hook.WorldGuardRegionHook;
import com.mossboxblok.manager.TemporaryBlockManager;
import com.mossboxblok.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.IdentityHashMap;
import java.util.Map;

public final class TemporaryBlockListener implements Listener {

    private final MossBoxBlokPlugin plugin;
    private final TemporaryBlockManager temporaryBlockManager;
    private final WorldGuardRegionHook worldGuardRegionHook;
    // Reuse the validation result at MONITOR without querying WorldGuard a second time.
    private final Map<BlockPlaceEvent, Integer> pendingPlacements = new IdentityHashMap<>();

    public TemporaryBlockListener(
            MossBoxBlokPlugin plugin,
            TemporaryBlockManager temporaryBlockManager,
            WorldGuardRegionHook worldGuardRegionHook
    ) {
        this.plugin = plugin;
        this.temporaryBlockManager = temporaryBlockManager;
        this.worldGuardRegionHook = worldGuardRegionHook;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceValidate(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isManagedLocation(block)) {
            return;
        }

        Material material = block.getType();
        int seconds = plugin.settings().removalSeconds(material);
        if (seconds <= 0) {
            event.setCancelled(true);
            sendPlayerFeedback(event.getPlayer(), plugin.settings().message("cannot-place-here"));
            return;
        }

        pendingPlacements.put(event, seconds);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlaceSchedule(BlockPlaceEvent event) {
        Integer seconds = pendingPlacements.remove(event);
        if (seconds == null || event.isCancelled()) {
            return;
        }

        Block block = event.getBlockPlaced();
        temporaryBlockManager.scheduleRemoval(block, seconds);
        sendPlayerFeedback(
                event.getPlayer(),
                plugin.settings().message("block-placed")
                        .replace("%seconds%", Integer.toString(seconds))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        temporaryBlockManager.cancelRemoval(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        temporaryBlockManager.removeTemporaryBlocksInChunk(event.getChunk());
    }

    private boolean isManagedLocation(Block block) {
        // Outside configured worlds/regions, WorldGuard and vanilla rules stay untouched.
        return plugin.settings().isEnabledWorld(block.getWorld())
                && worldGuardRegionHook.isInEnabledRegion(block.getLocation(), plugin.settings().enabledRegions());
    }

    private void sendPlayerFeedback(Player player, String message) {
        if (plugin.settings().playerFeedbackChannel() == FeedbackChannel.ACTION_BAR) {
            player.sendActionBar(ColorUtil.component(message));
            return;
        }

        player.sendMessage(message);
    }
}
