package com.orbitalstrike;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CommandOrbital implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public CommandOrbital(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("orbital.admin")) {
                sender.sendMessage(Component.text("You lack security clearance to reload this subsystem.", NamedTextColor.RED));
                return true;
            }

            plugin.reloadConfig();
            sender.sendMessage(Component.text("OrbitalStrike configuration safely reloaded from local disk!", NamedTextColor.GREEN));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            if (!sender.hasPermission("orbital.admin")) {
                sender.sendMessage(Component.text("You lack security clearance to configure this system.", NamedTextColor.RED));
                return true;
            }

            boolean currentState = plugin.getConfig().getBoolean("settings.enabled", true);
            boolean newState = !currentState;

            plugin.getConfig().set("settings.enabled", newState);
            plugin.saveConfig();

            if (newState) {
                sender.sendMessage(Component.text("Orbital strike mechanism successfully ONLINE. Fishing rods upgraded.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Orbital strike mechanism taken OFFLINE. Fishing rods reverted to normal.", NamedTextColor.RED));
            }
            return true;
        }

        sender.sendMessage(Component.text("Usage: /orbital <toggle|reload>", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("orbital.admin")) {
            String currentArg = args[0].toLowerCase();
            if ("toggle".startsWith(currentArg)) completions.add("toggle");
            if ("reload".startsWith(currentArg)) completions.add("reload");
        }
        return completions;
    }
}
