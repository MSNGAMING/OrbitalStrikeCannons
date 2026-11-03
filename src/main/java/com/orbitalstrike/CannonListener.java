package com.orbitalstrike;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

public class CannonListener implements Listener {

    private final JavaPlugin plugin;
    private final StrikeManager strikeManager;
    private final HashMap<UUID, Long> stabCooldowns = new HashMap<>();
    private final HashMap<UUID, Long> nukeCooldowns = new HashMap<>();

    public CannonListener(JavaPlugin plugin, StrikeManager strikeManager) {
        this.plugin = plugin;
        this.strikeManager = strikeManager;
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;

        if (event.getState() != PlayerFishEvent.State.IN_GROUND &&
                event.getState() != PlayerFishEvent.State.REEL_IN &&
                event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        if (handItem.getType() != Material.FISHING_ROD && offHandItem.getType() != Material.FISHING_ROD) return;

        FishHook hook = event.getHook();
        Location targetLocation = hook.getLocation();
        String typeLower = player.isSneaking() ? "nuke" : "stab";

        if (plugin.getConfig().getBoolean("settings.per-player-permissions", true)) {
            if (!player.hasPermission("orbital.use")) {
                player.sendMessage(Component.text("You lack security clearance to handle weaponized fishing equipment!", NamedTextColor.RED));
                return;
            }
            if (!player.hasPermission("orbital.strike." + typeLower)) {
                player.sendMessage(Component.text("You lack authorization for the " + typeLower.toUpperCase() + " payload tier!", NamedTextColor.RED));
                return;
            }
        }

        HashMap<UUID, Long> activeCooldownMap = typeLower.equals("stab") ? stabCooldowns : nukeCooldowns;
        double configCooldownSec = plugin.getConfig().getDouble("cooldowns." + typeLower, 5.0);
        long cooldownMs = (long) (configCooldownSec * 1000);

        if (activeCooldownMap.containsKey(player.getUniqueId())) {
            long timeLeft = activeCooldownMap.get(player.getUniqueId()) + cooldownMs - System.currentTimeMillis();
            if (timeLeft > 0) {
                sendActionBarCooldown(player, timeLeft);
                return;
            }
        }

        event.getHook().remove();
        long systemTriggerTime = System.currentTimeMillis();
        activeCooldownMap.put(player.getUniqueId(), systemTriggerTime);

        startCooldownActionBarTracker(player, systemTriggerTime, cooldownMs, typeLower.toUpperCase());
        broadcastStrikeAlert(player, typeLower, targetLocation);

        if (typeLower.equals("stab")) {
            strikeManager.fireStabStrike(targetLocation);
        } else if (typeLower.equals("nuke")) {
            strikeManager.fireNukeStrike(targetLocation);
        }
    }

    private void sendActionBarCooldown(Player player, long timeLeftMs) {
        double seconds = timeLeftMs / 1000.0;
        player.sendActionBar(Component.text("⚠️ System Recharging... Wait " + String.format("%.1f", seconds) + "s", NamedTextColor.RED));
    }

    private void startCooldownActionBarTracker(Player player, long startTime, long totalDuration, String weaponName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                long elapsedTime = System.currentTimeMillis() - startTime;
                long timeLeft = totalDuration - elapsedTime;

                if (timeLeft <= 0) {
                    player.sendActionBar(Component.text("⚡ " + weaponName + " MATRIX FULLY CHARGED ⚡", NamedTextColor.GREEN));
                    cancel();
                    return;
                }

                double secondsLeft = timeLeft / 1000.0;
                player.sendActionBar(Component.text(weaponName + " RECHARGE SYSTEM: " + String.format("%.1f", secondsLeft) + "s", NamedTextColor.GOLD));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void broadcastStrikeAlert(Player shooter, String strikeType, Location loc) {
        String coordsString = String.format("X: %d, Y: %d, Z: %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Component alertMessage;

        if (strikeType.equalsIgnoreCase("nuke")) {
            alertMessage = Component.text("[ORBITAL WARNING] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text(shooter.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" has authorized a massive ", NamedTextColor.RED))
                    .append(Component.text("NUKE CARPET BOMB", NamedTextColor.DARK_RED, TextDecoration.UNDERLINED))
                    .append(Component.text(" over ", NamedTextColor.RED))
                    .append(Component.text(coordsString, NamedTextColor.GOLD));
        } else {
            alertMessage = Component.text("[ORBITAL] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(shooter.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" launched a concentrated ", NamedTextColor.GRAY))
                    .append(Component.text("STAB LASER COLUMN", NamedTextColor.YELLOW))
                    .append(Component.text(" at ", NamedTextColor.GRAY))
                    .append(Component.text(coordsString, NamedTextColor.LIGHT_PURPLE));
        }

        Bukkit.getServer().sendMessage(alertMessage);
    }
}
