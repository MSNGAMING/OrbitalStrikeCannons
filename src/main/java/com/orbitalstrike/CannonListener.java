package com.orbitalstrike;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class CannonListener implements Listener {

    private final OrbitalStrikePlugin plugin;
    private final StrikeManager strikeManager;

    // Generous cache eviction to handle custom cooldown durations while preventing memory leaks from offline players
    private final Cache<UUID, Long> stabCooldowns;
    private final Cache<UUID, Long> nukeCooldowns;

    public CannonListener(OrbitalStrikePlugin plugin, StrikeManager strikeManager) {
        this.plugin = plugin;
        this.strikeManager = strikeManager;
        this.stabCooldowns = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
        this.nukeCooldowns = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
    }

    public void clearCaches() {
        this.stabCooldowns.invalidateAll();
        this.nukeCooldowns.invalidateAll();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!plugin.isStrikesEnabled()) return;

        // Only trigger on confirmed impacts (ground landing or entity caught)
        if (event.getState() != PlayerFishEvent.State.IN_GROUND &&
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

        if (plugin.isPerPlayerPermissions()) {
            if (!player.hasPermission("orbital.use")) {
                player.sendMessage(Component.text("You lack security clearance to handle weaponized fishing equipment!", NamedTextColor.RED));
                return;
            }
            if (!player.hasPermission("orbital.strike." + typeLower)) {
                player.sendMessage(Component.text("You lack authorization for the " + typeLower.toUpperCase() + " payload tier!", NamedTextColor.RED));
                return;
            }
        }

        Cache<UUID, Long> activeCooldownMap = typeLower.equals("stab") ? stabCooldowns : nukeCooldowns;
        double configCooldownSec = typeLower.equals("stab") ? plugin.getStabCooldownSec() : plugin.getNukeCooldownSec();
        long cooldownMs = (long) (configCooldownSec * 1000);

        Long lastTrigger = activeCooldownMap.getIfPresent(player.getUniqueId());

        if (lastTrigger != null) {
            long timeLeft = lastTrigger + cooldownMs - System.currentTimeMillis();
            if (timeLeft > 0) {
                sendActionBarCooldown(player, timeLeft);
                return;
            }
        }

        hook.remove();
        long systemTriggerTime = System.currentTimeMillis();
        activeCooldownMap.put(player.getUniqueId(), systemTriggerTime);

        startCooldownActionBarTracker(player, systemTriggerTime, cooldownMs, typeLower.toUpperCase());
        broadcastStrikeAlert(player, typeLower, targetLocation);

        if (typeLower.equals("stab")) {
            strikeManager.fireStabStrike(targetLocation);
        } else {
            strikeManager.fireNukeStrike(targetLocation);
        }
    }

    private void sendActionBarCooldown(Player player, long timeLeftMs) {
        double seconds = timeLeftMs / 1000.0;
        player.sendActionBar(Component.text("⚠️ System Recharging... Wait " + String.format("%.1f", seconds) + "s", NamedTextColor.RED));
    }

    /**
     * Dispatches action-bar updates offloaded to Paper's AsyncScheduler (thread-safe Adventure API),
     * avoiding any load on the main server tick loop.
     */
    private void startCooldownActionBarTracker(Player player, long startTime, long totalDuration, String weaponName) {
        Consumer<ScheduledTask> trackerTask = scheduledTask -> {
            if (!player.isOnline()) {
                scheduledTask.cancel();
                return;
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            long timeLeft = totalDuration - elapsedTime;

            if (timeLeft <= 0) {
                player.sendActionBar(Component.text("⚡ " + weaponName + " MATRIX FULLY CHARGED ⚡", NamedTextColor.GREEN));
                scheduledTask.cancel();
                return;
            }

            double secondsLeft = timeLeft / 1000.0;
            player.sendActionBar(Component.text(weaponName + " RECHARGE SYSTEM: " + String.format("%.1f", secondsLeft) + "s", NamedTextColor.GOLD));
        };

        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, trackerTask, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void broadcastStrikeAlert(Player shooter, String strikeType, Location loc) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        String coordsString = String.format("[%s] X: %d, Y: %d, Z: %d", worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
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
