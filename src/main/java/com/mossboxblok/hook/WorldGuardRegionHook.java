package com.mossboxblok.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class WorldGuardRegionHook {

    private static final long WARNING_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private final JavaPlugin plugin;
    private final RegionQuery regionQuery;
    private long lastWarningMillis;

    public WorldGuardRegionHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.regionQuery = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    }

    public boolean isInEnabledRegion(Location location, Set<String> enabledRegions) {
        if (location.getWorld() == null || enabledRegions.isEmpty()) {
            return false;
        }

        try {
            ApplicableRegionSet applicableRegions = regionQuery.getApplicableRegions(BukkitAdapter.adapt(location));
            for (ProtectedRegion region : applicableRegions) {
                if (matches(region, enabledRegions)) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            warnLookupFailure(location, exception);
        }
        return false;
    }

    private boolean matches(ProtectedRegion region, Set<String> enabledRegions) {
        ProtectedRegion current = region;
        while (current != null) {
            if (enabledRegions.contains(current.getId().toLowerCase(Locale.ROOT))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void warnLookupFailure(Location location, RuntimeException exception) {
        long now = System.currentTimeMillis();
        if (now - lastWarningMillis < WARNING_INTERVAL_MILLIS) {
            return;
        }

        lastWarningMillis = now;
        plugin.getLogger().warning(
                "WorldGuard region lookup failed at "
                        + location.getWorld().getName()
                        + " "
                        + location.getBlockX()
                        + ","
                        + location.getBlockY()
                        + ","
                        + location.getBlockZ()
                        + ". Skipping MossBoxBlok placement control for this event. "
                        + exception.getClass().getSimpleName()
                        + ": "
                        + exception.getMessage()
        );
    }
}
