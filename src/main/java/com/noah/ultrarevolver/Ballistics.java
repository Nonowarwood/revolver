package com.noah.ultrarevolver;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Logique de tir : hitscan, détection des pièces, chaîne de ricochets redirigés vers les cibles. */
public final class Ballistics {
    public static final double RANGE = 64.0;
    public static final double RICOCHET_RANGE = 24.0;
    public static final float BASE_DAMAGE = 6.0f;      // 3 coeurs
    public static final float RICOCHET_BONUS = 0.75f;  // +75 % des dégâts de base par pièce
    public static final int MAX_CHAIN = 8;

    public static void fire(ServerLevel level, ServerPlayer shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 dir = shooter.getLookAngle();
        Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
        // le faisceau part de la bouche du canon (devant, un peu à droite et en dessous des yeux)
        Vec3 muzzle = eye.add(dir.scale(0.9)).add(right.scale(0.22)).subtract(0, 0.18, 0);

        float pitch = 0.95f + shooter.getRandom().nextFloat() * 0.1f;
        level.playSound(null, shooter.blockPosition(), ModSounds.REVOLVER_SHOT, SoundSource.PLAYERS, 3.0f, pitch);

        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.03, 0.03, 0.03, 0.02);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 5, 0.05, 0.05, 0.05, 0.015);

        trace(level, shooter, eye, muzzle, dir, BASE_DAMAGE, 0);
    }

    /** Lance un rayon depuis start dans la direction dir : pièce, créature ou bloc, le plus proche gagne. */
    private static void trace(ServerLevel level, ServerPlayer shooter, Vec3 start, Vec3 visualStart, Vec3 dir,
                              float damage, int chain) {
        Vec3 far = start.add(dir.scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(start, far, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, shooter));
        Vec3 end = blockHit.getLocation();
        double bestSq = start.distanceToSqr(end);

        LivingEntity hitLiving = null;
        Vec3 hitPoint = end;
        AABB sweep = new AABB(start, end).inflate(1.0);
        for (Entity e : level.getEntities(shooter, sweep,
                x -> x instanceof LivingEntity le && le.isAlive() && !le.isSpectator())) {
            Optional<Vec3> p = e.getBoundingBox().inflate(0.1).clip(start, end);
            if (p.isPresent()) {
                double d = start.distanceToSqr(p.get());
                if (d < bestSq) {
                    bestSq = d;
                    hitLiving = (LivingEntity) e;
                    hitPoint = p.get();
                }
            }
        }

        CoinManager.CoinHit coinHit = CoinManager.raycast(level, start, end, bestSq);
        if (coinHit != null) {
            beam(level, visualStart, coinHit.point(), chain);
            ricochet(level, shooter, coinHit.coin(), dir, damage, chain + 1);
        } else if (hitLiving != null) {
            beam(level, visualStart, hitPoint, chain);
            damage(level, shooter, hitLiving, damage);
        } else {
            beam(level, visualStart, end, chain);
            if (blockHit.getType() != HitResult.Type.MISS) {
                impact(level, blockHit, dir);
            }
        }
    }

    private static void ricochet(ServerLevel level, ServerPlayer shooter, ItemEntity coin, Vec3 incomingDir,
                                 float damage, int chain) {
        Vec3 pos = coin.getBoundingBox().getCenter();
        CoinManager.consume(coin);

        float pitch = Math.min(2.0f, 0.85f + 0.13f * chain);
        level.playSound(null, coin.blockPosition(), ModSounds.COIN_RICOCHET, SoundSource.PLAYERS, 1.5f, pitch);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 14, 0.15, 0.15, 0.15, 0.4);
        level.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.1);

        if (chain > MAX_CHAIN) {
            return;
        }

        float boosted = damage + BASE_DAMAGE * RICOCHET_BONUS;
        Vec3 aim = pickAim(level, shooter, pos, incomingDir);
        trace(level, shooter, pos, pos, aim, boosted, chain);
    }

    /** Direction du ricochet : autre pièce visible > créature la plus proche (hostile en priorité) > tout droit. */
    private static Vec3 pickAim(ServerLevel level, ServerPlayer shooter, Vec3 from, Vec3 fallback) {
        ItemEntity next = CoinManager.nearest(level, from, RICOCHET_RANGE);
        if (next != null) {
            Vec3 c = next.getBoundingBox().getCenter();
            if (hasLineOfSight(level, shooter, from, c)) {
                return c.subtract(from).normalize();
            }
        }

        LivingEntity target = nearestTarget(level, shooter, from);
        if (target != null) {
            return target.getBoundingBox().getCenter().subtract(from).normalize();
        }
        return fallback;
    }

    private static LivingEntity nearestTarget(ServerLevel level, ServerPlayer shooter, Vec3 from) {
        AABB area = new AABB(from, from).inflate(RICOCHET_RANGE);
        LivingEntity best = null;
        LivingEntity bestHostile = null;
        double bestSq = RICOCHET_RANGE * RICOCHET_RANGE;
        double bestHostileSq = bestSq;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != shooter && !e.isSpectator() && !(e instanceof Player))) {
            Vec3 center = le.getBoundingBox().getCenter();
            double d = from.distanceToSqr(center);
            if (d >= bestSq || !hasLineOfSight(level, shooter, from, center)) {
                continue;
            }
            if (le instanceof Enemy && d < bestHostileSq) {
                bestHostileSq = d;
                bestHostile = le;
            }
            if (d < bestSq) {
                bestSq = d;
                best = le;
            }
        }
        return bestHostile != null ? bestHostile : best;
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer shooter, Vec3 a, Vec3 b) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter))
                .getType() == HitResult.Type.MISS;
    }

    private static void damage(ServerLevel level, ServerPlayer shooter, LivingEntity target, float amount) {
        target.invulnerableTime = 0; // pas d'invulnérabilité entre deux tirs rapprochés
        target.hurtServer(level, level.damageSources().playerAttack(shooter), amount);

        Vec3 c = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.25, 0.25, 0.25, 0.3);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.1);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9f, 1.3f);
    }

    /** Impact sur un bloc : éclats du bloc, étincelles et fumée. */
    private static void impact(ServerLevel level, BlockHitResult hit, Vec3 dir) {
        Vec3 p = hit.getLocation().subtract(dir.scale(0.05));
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 10, 0.08, 0.08, 0.08, 0.06);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 6, 0.05, 0.05, 0.05, 0.25);
        level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.04, 0.04, 0.04, 0.01);
    }

    /** Faisceau : fine ligne d'étincelles (bleu-blanc pour le tir, doré pour les ricochets). */
    private static void beam(ServerLevel level, Vec3 from, Vec3 to, int chain) {
        ParticleOptions particle = chain == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.WAX_ON;
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0e-3) {
            return;
        }
        Vec3 step = delta.scale(0.3 / len);
        Vec3 p = from;
        for (double t = 0; t < len; t += 0.3) {
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            p = p.add(step);
        }
    }

    private Ballistics() {}
}
