package com.orbitalstrike;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class OrbitalStrikePlugin extends JavaPlugin {

    private StrikeManager strikeManager;
    private CannonListener cannonListener;

    // Cached configuration fields for zero-allocation hot-path reads
    private boolean strikesEnabled;
    private boolean terrainDamage;
    private boolean perPlayerPermissions;
    private double stabCooldownSec;
    private double nukeCooldownSec;
    private double nukeRadius;
    private Sound stabSound;
    private Sound nukeSound;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cacheConfigValues();

        this.strikeManager = new StrikeManager(this);
        this.cannonListener = new CannonListener(this, strikeManager);

        // Explicit event listener registrations
        getServer().getPluginManager().registerEvents(this.cannonListener, this);
        getServer().getPluginManager().registerEvents(this.strikeManager, this);

        // Native Brigadier Command Registration
        LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(CommandOrbital.createNode(this));
        });

        getLogger().info("OrbitalStrike system is online and ready.");
    }

    public void cacheConfigValues() {
        this.strikesEnabled = getConfig().getBoolean("settings.enabled", true);
        this.terrainDamage = getConfig().getBoolean("settings.terrain-damage", false);
        this.perPlayerPermissions = getConfig().getBoolean("settings.per-player-permissions", true);
        this.stabCooldownSec = getConfig().getDouble("cooldowns.stab", 5.0);
        this.nukeCooldownSec = getConfig().getDouble("cooldowns.nuke", 15.0);
        this.nukeRadius = Math.max(1.0, getConfig().getDouble("settings.nuke-radius", 12.0));
        this.stabSound = parseSound("sounds.stab-launch", Sound.BLOCK_BEACON_ACTIVATE);
        this.nukeSound = parseSound("sounds.nuke-launch", Sound.ENTITY_WITHER_SPAWN);
    }

    public void setStrikesEnabled(boolean enabled) {
        this.strikesEnabled = enabled;
        getConfig().set("settings.enabled", enabled);
        saveConfig();
    }

    public void setNukeRadius(double radius) {
        this.nukeRadius = radius;
        getConfig().set("settings.nuke-radius", radius);
        saveConfig();
    }

    private Sound parseSound(String path, Sound fallback) {
        String soundStr = getConfig().getString(path);
        if (soundStr != null && !soundStr.isBlank()) {
            String raw = soundStr.trim();

            // 1. Try Registry.SOUNDS lookup (standard across Paper 1.21.x and future releases)
            try {
                String keyStr = raw.toLowerCase().replace('_', '.').replace(':', '.');
                if (keyStr.startsWith("minecraft.")) {
                    keyStr = keyStr.substring("minecraft.".length());
                }
                NamespacedKey key = NamespacedKey.minecraft(keyStr);
                Sound regSound = Registry.SOUNDS.get(key);
                if (regSound != null) {
                    return regSound;
                }
            } catch (Throwable ignored) {
                // Fall through to reflection lookup
            }

            // 2. Try reflection valueOf to prevent LinkageError / IncompatibleClassChangeError
            // between Paper 1.21.1 (Sound as Enum class) and Paper 1.21.4 (Sound as OldEnum interface)
            try {
                String normalized = raw.toUpperCase().replace('.', '_').replace(':', '_');
                Method valueOfMethod = Sound.class.getMethod("valueOf", String.class);
                Object result = valueOfMethod.invoke(null, normalized);
                if (result instanceof Sound sound) {
                    return sound;
                }
            } catch (Throwable e) {
                getLogger().warning("Invalid sound identifier in config: " + soundStr + ". Defaulting to " + fallback);
            }
        }
        return fallback;
    }

    public boolean isStrikesEnabled() { return strikesEnabled; }
    public boolean isTerrainDamage() { return terrainDamage; }
    public boolean isPerPlayerPermissions() { return perPlayerPermissions; }
    public double getStabCooldownSec() { return stabCooldownSec; }
    public double getNukeCooldownSec() { return nukeCooldownSec; }
    public double getNukeRadius() { return nukeRadius; }
    public Sound getStabSound() { return stabSound; }
    public Sound getNukeSound() { return nukeSound; }
    public StrikeManager getStrikeManager() { return strikeManager; }
    public CannonListener getCannonListener() { return cannonListener; }

    @Override
    public void onDisable() {
        // Cancel all Paper native region and async schedulers
        try {
            getServer().getGlobalRegionScheduler().cancelTasks(this);
        } catch (Throwable ignored) {}
        try {
            getServer().getAsyncScheduler().cancelTasks(this);
        } catch (Throwable ignored) {}

        if (this.cannonListener != null) {
            this.cannonListener.clearCaches();
        }

        getLogger().info("OrbitalStrike system shut down safely.");
    }
}
