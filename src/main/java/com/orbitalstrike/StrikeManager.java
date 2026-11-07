package com.orbitalstrike;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
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

    /**
     * Fires the STAB surgical strike using Paper's native RegionScheduler and EntityScheduler.
     * Charges maintain strict vertical alignment and only detonate when contacting the surface
     * or the bottom of the crater, boring a concentrated deep hole.
     */
    public void fireStabStrike(Location target) {
        World world = target.getWorld();
        if (world == null) return;

        // Center on the target block column
        Location skyOrigin = new Location(world, target.getBlockX() + 0.5, 0, target.getBlockZ() + 0.5);
        int maxHeight = world.getMaxHeight() - 5;
        skyOrigin.setY(Math.min(maxHeight, target.getY() + 45));

        spawnLaserBeam(target.clone(), maxHeight, 90);
        world.playSound(target, plugin.getStabSound(), 4.0f, 0.5f);

        int[] count = {0};
        plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, skyOrigin, spawnTask -> {
            if (count[0] >= 25) {
                spawnTask.cancel();
                return;
            }

            // Generous safety fuse so TNT never detonates in mid-air
            TNTPrimed tnt = world.spawn(skyOrigin, TNTPrimed.class);
            tnt.setFuseTicks(300);
            tnt.setVelocity(new Vector(0, -1.8, 0));
            tnt.getPersistentDataContainer().set(payloadKey, PersistentDataType.BYTE, (byte) 1);

            world.spawnParticle(Particle.SONIC_BOOM, skyOrigin, 2);

            // Native Paper EntityScheduler: tracks TNT per-tick on its regional thread,
            // keeping it strictly vertical and detonating instantly upon surface contact.
            tnt.getScheduler().runAtFixedRate(plugin, impactTask -> {
                if (!tnt.isValid() || tnt.isDead()) {
                    impactTask.cancel();
                    return;
                }

                // Override velocity to lock horizontal movement and maintain downward force
                Vector vel = tnt.getVelocity();
                tnt.setVelocity(new Vector(0, Math.min(vel.getY(), -1.4), 0));

                Location loc = tnt.getLocation();
                Block blockAt = loc.getBlock();
                Block blockBelow = loc.clone().subtract(0, 0.4, 0).getBlock();

                boolean hitSurface = tnt.isOnGround()
                        || !blockAt.isPassable()
                        || !blockBelow.isPassable()
                        || loc.getY() <= world.getMinHeight() + 2;

                if (hitSurface) {
                    tnt.setFuseTicks(0);
                    impactTask.cancel();
                }
            }, null, 1L, 1L);

            count[0]++;
        }, 10L, 3L);
    }

    /**
     * Fires the NUKE carpet bomb strike using Paper's native RegionScheduler.
     * The 3 concentric rings scale dynamically based on the configured nuke-radius.
     */
    public void fireNukeStrike(Location target) {
        World world = target.getWorld();
        if (world == null) return;

        Location skyCenter = target.clone();
        int maxHeight = world.getMaxHeight() - 10;
        skyCenter.setY(Math.min(maxHeight, target.getY() + 60));

        spawnLaserBeam(target.clone(), maxHeight, 70);

        world.playSound(target, plugin.getNukeSound(), 5.0f, 0.5f);
        world.strikeLightningEffect(target);

        double maxRadius = plugin.getNukeRadius();
        double innerRadius = Math.max(1.5, maxRadius * 0.22);
        double midRadius = Math.max(3.0, maxRadius * 0.52);
        double outerRadius = Math.max(5.0, maxRadius);

        plugin.getServer().getRegionScheduler().runDelayed(plugin, skyCenter,
                task -> spawnConcentricRing(skyCenter, 5, innerRadius), 15L);

        plugin.getServer().getRegionScheduler().runDelayed(plugin, skyCenter,
                task -> spawnConcentricRing(skyCenter, 12, midRadius), 20L);

        plugin.getServer().getRegionScheduler().runDelayed(plugin, skyCenter,
                task -> spawnConcentricRing(skyCenter, 20, outerRadius), 25L);
    }

    /**
     * Spawns targeting laser beam directly on Paper's RegionScheduler.
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

        int[] elapsed = {0};
        plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, targetFloor, task -> {
            if (elapsed[0] >= lifespanTicks) {
                task.cancel();
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

            elapsed[0]++;
        }, 1L, 1L);
    }

    /**
     * Uses Paper's AsyncScheduler to precompute circular ring trigonometry asynchronously,
     * then dispatches TNT entities safely to the regional chunk thread via RegionScheduler.
     */
    private void spawnConcentricRing(Location center, int tntCount, double radius) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            List<SpawnPayload> payloads = new ArrayList<>(tntCount);
            for (int i = 0; i < tntCount; i++) {
                double angle = (2 * Math.PI / tntCount) * i;
                payloads.add(new SpawnPayload(Math.cos(angle) * radius, Math.sin(angle) * radius));
            }

            // Return to the chunk's regional thread for world entity spawning
            plugin.getServer().getRegionScheduler().execute(plugin, center, () -> {
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTntDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TNTPrimed && entity.getPersistentDataContainer().has(payloadKey, PersistentDataType.BYTE)) {
            // Cancel explosion damage on payload TNT so earlier detonations don't prematurely shorten the fuse
            event.setCancelled(true);
        }
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
