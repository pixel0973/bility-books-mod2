package com.abilitybooks.ability;

import com.abilitybooks.item.ModItems;
import com.abilitybooks.spikes.SpikeZones;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class BoneKingActions {
    private BoneKingActions() {}

    private static ItemStackParticleEffect bone() {
        return new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BONE));
    }

    /** Костяное копьё: летит по прямой, бьёт первую цель, 2.5 урона. */
    public static boolean boneSpear(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double range = 20.0;
        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(range));

        HitResult blockHit = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        double maxDist = blockHit.getType() == HitResult.Type.MISS ? range : blockHit.getPos().distanceTo(start);
        Vec3d limitEnd = start.add(dir.multiply(maxDist));
        Box box = player.getBoundingBox().stretch(dir.multiply(maxDist)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(player, start, limitEnd, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player,
                maxDist * maxDist);

        double dist = hit != null ? hit.getPos().distanceTo(start) : maxDist;
        for (double d = 1.0; d < dist; d += 0.4) {
            Vec3d p = start.add(dir.multiply(d));
            world.spawnParticles(bone(), p.x, p.y - 0.2, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y - 0.2, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.7f);
        player.swingHand(Hand.MAIN_HAND, true);

        if (hit != null) {
            hit.getEntity().damage(world.getDamageSources().playerAttack(player), 2.5f);
        }
        return true;
    }

    /** Костяные шипы: зона 4x5 блоков перед игроком, 0.5 урона в секунду, владельца не бьёт. */
    public static boolean boneSpikes(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Direction f = player.getHorizontalFacing();
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double x1, x2, z1, z2;
        if (f.getAxis() == Direction.Axis.X) {
            double a = px + f.getOffsetX() * 1.0;
            double b = px + f.getOffsetX() * 6.0; // длина 5
            x1 = Math.min(a, b);
            x2 = Math.max(a, b);
            z1 = pz - 2.0;               // ширина 4
            z2 = pz + 2.0;
        } else {
            double a = pz + f.getOffsetZ() * 1.0;
            double b = pz + f.getOffsetZ() * 6.0;
            z1 = Math.min(a, b);
            z2 = Math.max(a, b);
            x1 = px - 2.0;
            x2 = px + 2.0;
        }
        Box box = new Box(x1, py - 1.0, z1, x2, py + 2.5, z2);
        SpikeZones.add(world, box, player.getUuid());
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_BONE_BLOCK_BREAK, SoundCategory.PLAYERS, 1.5f, 0.6f);
        player.swingHand(Hand.MAIN_HAND, true);
        return true;
    }

    /** Похититель трусов: игрок, на которого смотришь (до 10 блоков), даёт тебе «Трусы (ник)». */
    public static boolean pantsThief(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double range = 10.0;
        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f);
        Vec3d end = start.add(dir.multiply(range));
        Box box = player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, box,
                e -> e instanceof PlayerEntity && !e.isSpectator() && e != player,
                range * range);
        if (hit == null) {
            player.sendMessage(Text.literal("Нет цели: посмотри на игрока (до 10 блоков).")
                    .formatted(Formatting.RED), true);
            return false;
        }
        String nick = hit.getEntity().getName().getString();
        ItemStack stack = new ItemStack(ModItems.UNDERWEAR);
        stack.setCustomName(Text.literal("Трусы (" + nick + ")")
                .styled(s -> s.withItalic(false).withColor(Formatting.AQUA)));
        stack.getOrCreateNbt().putString("Victim", nick);
        player.getInventory().offerOrDrop(stack);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1.0f, 0.5f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, hit.getPos().x, hit.getPos().y, hit.getPos().z,
                8, 0.3, 0.3, 0.3, 0.0);
        player.sendMessage(Text.literal("Ты украл трусы у " + nick + "!").formatted(Formatting.AQUA), true);
        return true;
    }
}
