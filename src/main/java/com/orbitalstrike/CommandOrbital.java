package com.orbitalstrike;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
                .then(Commands.literal("terraindamage")
                        .executes(context -> {
                            boolean newState = !plugin.isTerrainDamage();
                            plugin.setTerrainDamage(newState);

                            if (newState) {
                                context.getSource().getSender().sendMessage(
                                        Component.text("Orbital strike terrain damage is now ENABLED (Explosions will break blocks).", NamedTextColor.GREEN)
                                );
                            } else {
                                context.getSource().getSender().sendMessage(
                                        Component.text("Orbital strike terrain damage is now DISABLED (Explosions will only damage entities).", NamedTextColor.RED)
                                );
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean newState = BoolArgumentType.getBool(context, "enabled");
                                    plugin.setTerrainDamage(newState);

                                    if (newState) {
                                        context.getSource().getSender().sendMessage(
                                                Component.text("Orbital strike terrain damage is now ENABLED (Explosions will break blocks).", NamedTextColor.GREEN)
                                        );
                                    } else {
                                        context.getSource().getSender().sendMessage(
                                                Component.text("Orbital strike terrain damage is now DISABLED (Explosions will only damage entities).", NamedTextColor.RED)
                                        );
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("setnukeradius")
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 100.0))
                                .executes(context -> {
                                    double radius = DoubleArgumentType.getDouble(context, "radius");
                                    plugin.setNukeRadius(radius);
                                    context.getSource().getSender().sendMessage(
                                            Component.text("Orbital NUKE strike radius updated to " + radius + " blocks.", NamedTextColor.GREEN)
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .build();
    }
}
