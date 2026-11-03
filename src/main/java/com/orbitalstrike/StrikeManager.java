package com.orbitalstrike;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class StrikeManager implements Listener {

    private final JavaPlugin plugin;
    private final String METADATA_KEY = "orbital_strike_payload";

    public StrikeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void fireStabStrike(Location target) {
        Location skyOrigin = target.clone();
        int maxHeight = target.getWorld().getMaxHeight() - 5;
        skyOrigin.setY(Math.min(maxHeight, target.getY() + 50));

        spawnLaserBeam(target.clone(), maxHeight, 40);

        String soundName = plugin.getConfig().getString("sounds.stab-launch", "BLOCK_BEACON_ACTIVATE");
        playSoundSafely(target, soundName, 4.0f, 0.5f);

        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count >= 25) {
                    cancel();
                    return;
                }

                TNTPrimed tnt = skyOrigin.getWorld().spawn(skyOrigin, TNTPrimed.class);
                tnt.setFuseTicks(30);
                tnt.setVelocity(new Vector(0, -2.5, 0));
                tnt.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, true));

                skyOrigin.getWorld().spawnParticle(Particle.SONIC_BOOM, skyOrigin, 3);
                count++;
            }
        }.runTaskTimer(plugin, 10L, 2L);
    }

    public void fireNukeStrike(Location target) {
        Location skyCenter = target.clone();
        int maxHeight = target.getWorld().getMaxHeight() - 10;
        skyCenter.setY(Math.min(maxHeight, target.getY() + 60));

        spawnLaserBeam(target.clone(), maxHeight, 60);

        String soundName = plugin.getConfig().getString("sounds.nuke-launch", "ENTITY_WITHER_SPAWN");
        playSoundSafely(target, soundName, 5.0f, 0.5f);
        target.getWorld().strikeLightningEffect(target);

        new BukkitRunnable() {
            @Override
            public void run() {
                spawnConcentricRing(skyCenter, 5, 2.5);
            }
        }.runTaskLater(plugin, 15L);

        new BukkitRunnable() {
            @Override
            public void run() {
                spawnConcentricRing(skyCenter, 12, 6.0);
            }
        }.runTaskLater(plugin, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                spawnConcentricRing(skyCenter, 20, 11.5);
            }
        }.runTaskLater(plugin, 25L);
    }

    private void spawnLaserBeam(Location targetFloor, int skyHeight, int lifespanTicks) {
        new BukkitRunnable() {
            int elapsed = 0;
            final Particle.DustOptions laserStyle = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f);

            @Override
            public void run() {
                if (elapsed >= lifespanTicks) {
                    cancel();
                    return;
                }

                double startY = targetFloor.getY();
                for (double y = startY; y <= skyHeight; y += 1.5) {
                    Location point = targetFloor.clone();
                    point.setY(y);
                    point.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0, 0.0, 0.0, 0.0, laserStyle, true);
                }

                for (int degree = 0; degree < 360; degree += 45) {
                    double rad = Math.toRadians(degree);
                    Location ringPoint = targetFloor.clone().add(Math.cos(rad) * 0.8, 0.2, Math.sin(rad) * 0.8);
                    ringPoint.getWorld().spawnParticle(Particle.DUST, ringPoint, 1, 0, 0, 0, 0, laserStyle, true);
                }

                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnConcentricRing(Location center, int tntCount, double radius) {
        for (int i = 0; i < tntCount; i++) {
            double angle = (2 * Math.PI / tntCount) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location spawnLoc = center.clone().add(x, 0, z);
            TNTPrimed tnt = spawnLoc.getWorld().spawn(spawnLoc, TNTPrimed.class);

            tnt.setFuseTicks(50);
            tnt.setVelocity(new Vector(x * 0.05, -1.0, z * 0.05));
            tnt.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity != null && entity.hasMetadata(METADATA_KEY)) {
            boolean allowTerrainDamage = plugin.getConfig().getBoolean("settings.terrain-damage", false);
            if (!allowTerrainDamage) {
                event.blockList().clear();
            }
        }
    }

    private void playSoundSafely(Location loc, String soundEnumString, float vol, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundEnumString.toUpperCase());
            loc.getWorld().playSound(loc, sound, vol, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound identifier specified inside config: " + soundEnumString);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, vol, pitch);
        }
    }
}
