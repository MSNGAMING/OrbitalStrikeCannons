package com.orbitalstrike;

import org.bukkit.plugin.java.JavaPlugin;

public final class OrbitalStrikePlugin extends JavaPlugin {

    private StrikeManager strikeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.strikeManager = new StrikeManager(this);

        getServer().getPluginManager().registerEvents(new CannonListener(this, strikeManager), this);

        if (getCommand("orbital") != null) {
            CommandOrbital commandOrbital = new CommandOrbital(this);
            getCommand("orbital").setExecutor(commandOrbital);
            getCommand("orbital").setTabCompleter(commandOrbital);
        }

        getLogger().info("OrbitalStrike system is online and ready.");
    }

    @Override
    public void onDisable() {
        getLogger().info("OrbitalStrike system shut down safely.");
    }
}
