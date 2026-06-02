package com.mossboxblok.command;

import com.mossboxblok.MossBoxBlokPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class MossBoxBlokCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "mossboxblok.reload";

    private final MossBoxBlokPlugin plugin;

    public MossBoxBlokCommand(MossBoxBlokPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage(plugin.settings().message("no-permission"));
                return true;
            }

            plugin.reloadPlugin();
            sender.sendMessage(plugin.settings().message("plugin-reloaded"));
            return true;
        }

        sender.sendMessage(plugin.settings().prefixed("&7Kullanim: &#00D420/" + label + " reload"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission(RELOAD_PERMISSION)) {
            return "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
        }
        return List.of();
    }
}
