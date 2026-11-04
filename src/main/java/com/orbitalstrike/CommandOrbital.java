package com.orbitalstrike;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class CommandOrbital {

    public static LiteralCommandNode<CommandSourceStack> createNode(OrbitalStrikePlugin plugin) {
        return Commands.literal("orbital")
                .requires(source -> source.getSender().hasPermission("orbital.admin"))
                .then(Commands.literal("reload")
                        .executes(context -> {
                            plugin.reloadConfig();
                            plugin.cacheConfigValues();
                            context.getSource().getSender().sendMessage(
                                    Component.text("OrbitalStrike configuration safely reloaded from local disk!", NamedTextColor.GREEN)
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("toggle")
                        .executes(context -> {
                            boolean newState = !plugin.isStrikesEnabled();
                            plugin.setStrikesEnabled(newState);

                            if (newState) {
                                context.getSource().getSender().sendMessage(
                                        Component.text("Orbital strike mechanism successfully ONLINE. Fishing rods upgraded.", NamedTextColor.GREEN)
                                );
                            } else {
                                context.getSource().getSender().sendMessage(
                                        Component.text("Orbital strike mechanism taken OFFLINE. Fishing rods reverted to normal.", NamedTextColor.RED)
                                );
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .build();
    }
}
