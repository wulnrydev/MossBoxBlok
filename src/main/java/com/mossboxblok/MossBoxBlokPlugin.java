package com.mossboxblok;

import com.mossboxblok.command.MossBoxBlokCommand;
import com.mossboxblok.config.MossBoxBlokSettings;
import com.mossboxblok.hook.WorldGuardRegionHook;
import com.mossboxblok.listener.TemporaryBlockListener;
import com.mossboxblok.manager.TemporaryBlockManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MossBoxBlokPlugin extends JavaPlugin {

    private MossBoxBlokSettings settings;
    private TemporaryBlockManager temporaryBlockManager;
    private WorldGuardRegionHook worldGuardRegionHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            getLogger().severe("WorldGuard is required. Disabling MossBoxBlok.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reloadLocalSettings();

        this.worldGuardRegionHook = new WorldGuardRegionHook(this);
        this.temporaryBlockManager = new TemporaryBlockManager(this);

        getServer().getPluginManager().registerEvents(
                new TemporaryBlockListener(this, temporaryBlockManager, worldGuardRegionHook),
                this
        );

        PluginCommand command = getCommand("mossboxblok");
        if (command != null) {
            MossBoxBlokCommand executor = new MossBoxBlokCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("MossBoxBlok enabled.");
    }

    @Override
    public void onDisable() {
        if (temporaryBlockManager != null) {
            temporaryBlockManager.clearAndRemoveAll();
        }
        getLogger().info("MossBoxBlok disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        reloadLocalSettings();
    }

    public MossBoxBlokSettings settings() {
        return settings;
    }

    private void reloadLocalSettings() {
        this.settings = MossBoxBlokSettings.load(this);
        if (!settings.hasTemporaryBlocks()) {
            getLogger().warning("No valid temporary blocks are configured. Managed regions will reject block placement.");
        }
    }
}
