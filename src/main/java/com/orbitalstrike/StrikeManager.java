package com.orbitalstrike;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class StrikeManager implements Listener {

    private final OrbitalStrikePlugin plugin;
    private final NamespacedKey payloadKey;

    private record SpawnPayload(double x, double z) {}

    public StrikeManager(OrbitalStrikePlugin plugin) {
        this.plugin = plugin;
        this.payloadKey = new NamespacedKey(plugin, "orbital_strike_payload");
    }

    public void fireStabStrike(Location target) {
        World world = target.getWorld();
        if (world == null) return;

        Location skyOrigin = target.clone();
        int maxHeight = world.getMaxHeight() - 5;
        skyOrigin.setY(Math.min(maxHeight, target.getY() + 50));

        spawnLaserBeam(target.clone(), maxHeight, 40);
        world.playSound(target, plugin.getStabSound(), 4.0f, 0.5f);

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
                tnt.setVelocity(new Vector(0, -1.2, 0));
                tnt.getPersistentDataContainer().set(payloadKey, PersistentDataType.BYTE, (byte) 1);

                skyOrigin.getWorld().spawnParticle(Particle.SONIC_BOOM, skyOrigin, 3);
                count++;
            }
        }.runTaskTimer(plugin, 10L, 2L);
    }

    public void fireNukeStrike(Location target) {
        World world = target.getWorld();
        if (world == null) return;

        Location skyCenter = target.clone();
        int maxHeight = world.getMaxHeight() - 10;
        skyCenter.setY(Math.min(maxHeight, target.getY() + 60));

        spawnLaserBeam(target.clone(), maxHeight, 60);

        world.playSound(target, plugin.getNukeSound(), 5.0f, 0.5f);
        world.strikeLightningEffect(target);

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

    /**
     * Spawns targeting laser beam directly on the server tick thread.
     * All particle allocations and coordinate math are precalculated to eliminate tick overhead.
     */
    private void spawnLaserBeam(Location targetFloor, int skyHeight, int lifespanTicks) {
        final World world = targetFloor.getWorld();
        if (world == null) return;

        final Particle.DustOptions laserStyle = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f);
        final double floorX = targetFloor.getX();
        final double floorY = targetFloor.getY();
        final double floorZ = targetFloor.getZ();

        // Precompute ground circle offsets once
        final double[][] ringOffsets = new double[8][2];
        for (int i = 0; i < 8; i++) {
            double rad = Math.toRadians(i * 45);
            ringOffsets[i][0] = Math.cos(rad) * 0.8;
            ringOffsets[i][1] = Math.sin(rad) * 0.8;
        }

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= lifespanTicks) {
                    cancel();
                    return;
                }

                // Render vertical laser beam directly without object churn
                for (double y = floorY; y <= skyHeight; y += 1.5) {
                    world.spawnParticle(Particle.DUST, floorX, y, floorZ, 1, 0.0, 0.0, 0.0, 0.0, laserStyle, true);
                }

                // Render targeting circle at the base
                for (double[] offset : ringOffsets) {
                    world.spawnParticle(Particle.DUST, floorX + offset[0], floorY + 0.2, floorZ + offset[1], 1, 0.0, 0.0, 0.0, 0.0, laserStyle, true);
                }

                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Uses Paper's AsyncScheduler to precompute circular ring trigonometry asynchronously,
     * then dispatches TNT entities safely to the world on the main server thread.
     */
    private void spawnConcentricRing(Location center, int tntCount, double radius) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            List<SpawnPayload> payloads = new ArrayList<>(tntCount);
            for (int i = 0; i < tntCount; i++) {
                double angle = (2 * Math.PI / tntCount) * i;
                payloads.add(new SpawnPayload(Math.cos(angle) * radius, Math.sin(angle) * radius));
            }

            // Return to the main server thread for world entity spawning
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                World world = center.getWorld();
                if (world == null) return;

                for (SpawnPayload p : payloads) {
                    Location spawnLoc = center.clone().add(p.x(), 0, p.z());
                    TNTPrimed tnt = world.spawn(spawnLoc, TNTPrimed.class);
                    tnt.setFuseTicks(50);
                    tnt.setVelocity(new Vector(p.x() * 0.05, -0.6, p.z() * 0.05));
                    tnt.getPersistentDataContainer().set(payloadKey, PersistentDataType.BYTE, (byte) 1);
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (entity != null && entity.getPersistentDataContainer().has(payloadKey, PersistentDataType.BYTE)) {
            if (!plugin.isTerrainDamage()) {
                event.blockList().clear();
            }
        }
    }
}
