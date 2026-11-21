package com.abilitybooks.spikes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Активные зоны костяных шипов. */
public final class SpikeZones {
    private SpikeZones() {}

    /** Сколько тиков живут шипы (100 = 5 секунд). */
    public static final int DURATION_TICKS = 100;
    public static final float DAMAGE = 0.5f;

    private record Zone(ServerWorld world, Box box, UUID owner, long start, long end) {}

    private static final List<Zone> ZONES = new ArrayList<>();

    public static void add(ServerWorld world, Box box, UUID owner) {
        long t = world.getTime();
        ZONES.add(new Zone(world, box, owner, t, t + DURATION_TICKS));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZONES.clear());
    }

    private static void tick() {
        if (ZONES.isEmpty()) return;
        Iterator<Zone> it = ZONES.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            long now = z.world().getTime();
            if (now >= z.end()) {
                it.remove();
                continue;
            }
            long age = now - z.start();
            if (age % 2 == 0) particles(z);
            if (age % 20 == 0) damage(z);
        }
    }

    private static void particles(Zone z) {
        ServerWorld w = z.world();
        Box b = z.box();
        Random r = w.random;
        ItemStackParticleEffect bone = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BONE));
        for (int i = 0; i < 10; i++) {
            double x = b.minX + r.nextDouble() * (b.maxX - b.minX);
            double zz = b.minZ + r.nextDouble() * (b.maxZ - b.minZ);
            double y = b.minY + 1.0;
            // count = 0 -> частица летит вверх, как острый шип
            w.spawnParticles(bone, x, y, zz, 0, 0.0, 1.0, 0.0, 0.35);
            w.spawnParticles(ParticleTypes.CRIT, x, y + 0.4, zz, 1, 0.1, 0.3, 0.1, 0.02);
        }
    }

    private static void damage(Zone z) {
        List<LivingEntity> targets = z.world().getEntitiesByClass(LivingEntity.class, z.box(),
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(z.owner()));
        for (LivingEntity e : targets) {
            e.damage(z.world().getDamageSources().magic(), DAMAGE);
        }
    }
}
