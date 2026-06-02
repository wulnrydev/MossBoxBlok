package com.mossboxblok.config;

import com.mossboxblok.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MossBoxBlokSettings {

    private final Set<String> enabledWorlds;
    private final Set<String> enabledRegions;
    private final Map<Material, Integer> blockRemovalSeconds;
    private final FeedbackChannel playerFeedbackChannel;
    private final String prefix;
    private final Map<String, String> messages;

    private MossBoxBlokSettings(
            Set<String> enabledWorlds,
            Set<String> enabledRegions,
            Map<Material, Integer> blockRemovalSeconds,
            FeedbackChannel playerFeedbackChannel,
            String prefix,
            Map<String, String> messages
    ) {
        this.enabledWorlds = Set.copyOf(enabledWorlds);
        this.enabledRegions = Set.copyOf(enabledRegions);
        this.blockRemovalSeconds = Collections.unmodifiableMap(new EnumMap<>(blockRemovalSeconds));
        this.playerFeedbackChannel = playerFeedbackChannel;
        this.prefix = prefix;
        this.messages = Map.copyOf(messages);
    }

    public static MossBoxBlokSettings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        Set<String> worlds = new HashSet<>();
        for (String world : config.getStringList("settings.enabled-worlds")) {
            String normalized = world.trim();
            if (!normalized.isEmpty()) {
                worlds.add(normalized);
            }
        }

        Set<String> regions = new HashSet<>();
        for (String region : config.getStringList("settings.enabled-regions")) {
            String normalized = region.trim();
            if (!normalized.isEmpty()) {
                regions.add(normalized.toLowerCase(Locale.ROOT));
            }
        }

        Map<Material, Integer> blocks = new EnumMap<>(Material.class);
        ConfigurationSection section = config.getConfigurationSection("blocks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null || material.isAir() || !material.isBlock()) {
                    plugin.getLogger().warning("Invalid block material in config: " + key);
                    continue;
                }

                long seconds = section.getLong(key + ".remove-after-seconds", -1L);
                if (seconds <= 0) {
                    plugin.getLogger().warning("remove-after-seconds must be greater than 0 for " + key);
                    continue;
                }
                if (seconds > Integer.MAX_VALUE) {
                    plugin.getLogger().warning("remove-after-seconds is too large for " + key + ": " + seconds);
                    continue;
                }

                blocks.put(material, (int) seconds);
            }
        }

        FeedbackChannel playerFeedbackChannel = FeedbackChannel.from(
                config.getString("settings.player-feedback", FeedbackChannel.ACTION_BAR.name())
        );
        if (playerFeedbackChannel == null) {
            playerFeedbackChannel = FeedbackChannel.ACTION_BAR;
            plugin.getLogger().warning("Invalid settings.player-feedback value. Using ACTION_BAR.");
        }

        String prefix = ColorUtil.color(config.getString("messages.prefix", "&#15FF08&lBoxPvP &8\u25B8 "));
        Map<String, String> messages = new HashMap<>();
        messages.put(
                "no-permission",
                ColorUtil.color(config.getString("messages.no-permission", "&7Bu i\u015flemi yapmak i\u00e7in &#00D420yetkiniz &7bulunmuyor."))
        );
        messages.put(
                "block-placed",
                ColorUtil.color(config.getString("messages.block-placed", "&7Blok ba\u015far\u0131yla yerle\u015ftirildi, &#ffd500%seconds% &7saniye sonra &#00D420silinecek&7."))
        );
        messages.put(
                "cannot-place-here",
                ColorUtil.color(config.getString("messages.cannot-place-here", "&7Bu blo\u011fu bu &#00D420b\u00f6lgede &7koyamazs\u0131n!"))
        );
        messages.put(
                "plugin-reloaded",
                ColorUtil.color(config.getString("messages.plugin-reloaded", "&7MossBoxBlok eklentisi &#00D420ba\u015far\u0131yla &7yenilendi."))
        );

        return new MossBoxBlokSettings(worlds, regions, blocks, playerFeedbackChannel, prefix, messages);
    }

    public boolean isEnabledWorld(World world) {
        return enabledWorlds.contains(world.getName());
    }

    public Set<String> enabledRegions() {
        return enabledRegions;
    }

    public int removalSeconds(Material material) {
        Integer seconds = blockRemovalSeconds.get(material);
        return seconds == null ? -1 : seconds;
    }

    public boolean hasTemporaryBlocks() {
        return !blockRemovalSeconds.isEmpty();
    }

    public FeedbackChannel playerFeedbackChannel() {
        return playerFeedbackChannel;
    }

    public String message(String key) {
        return prefix + messages.getOrDefault(key, "");
    }

    public String prefixed(String message) {
        return prefix + ColorUtil.color(message);
    }

    public enum FeedbackChannel {
        CHAT,
        ACTION_BAR;

        private static FeedbackChannel from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }

            return switch (value.trim().replace("-", "_").toUpperCase(Locale.ROOT)) {
                case "CHAT" -> CHAT;
                case "ACTION_BAR", "ACTIONBAR", "BAR" -> ACTION_BAR;
                default -> null;
            };
        }
    }
}
