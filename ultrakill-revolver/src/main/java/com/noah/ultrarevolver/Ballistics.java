package com.noah.ultrarevolver;

import java.util.Optional;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Toute la logique de tir : balle hitscan, détection des pièces, chaîne de ricochets. */
public final class Ballistics {
    public static final double RANGE = 64.0;
    public static final double RICOCHET_RANGE = 24.0;
    public static final float BASE_DAMAGE = 6.0f;      // 3 coeurs
    public static final float RICOCHET_BONUS = 0.75f;  // +75 % de dégâts par pièce dans la chaîne
    public static final int MAX_CHAIN = 8;
    public static final boolean ENEMIES_ONLY = true;   // false = les ricochets visent aussi les animaux

    public static void fire(ServerLevel level, ServerPlayer shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 dir = shooter.getLookAngle();
        Vec3 far = eye.add(dir.scale(RANGE));
        Vec3 muzzle = eye.add(dir.scale(0.6)).subtract(0, 0.12, 0);

        level.playSound(null, shooter.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.7f);

        // 1) le bloc le plus proche limite la portée
        HitResult blockHit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, shooter));
        Vec3 end = blockHit.getLocation();
        double bestSq = eye.distanceToSqr(end);

        // 2) l'entité vivante la plus proche sur le rayon
        LivingEntity hitLiving = null;
        Vec3 hitPoint = end;
        AABB sweep = shooter.getBoundingBox().expandTowards(dir.scale(RANGE)).inflate(1.0);
        for (Entity e : level.getEntities(shooter, sweep,
                x -> x instanceof LivingEntity le && le.isAlive() && !le.isSpectator())) {
            Optional<Vec3> p = e.getBoundingBox().inflate(0.1).clip(eye, end);
            if (p.isPresent()) {
                double d = eye.distanceToSqr(p.get());
                if (d < bestSq) {
                    bestSq = d;
                    hitLiving = (LivingEntity) e;
                    hitPoint = p.get();
                }
            }
        }

        // 3) une pièce plus proche que tout le reste ?
        CoinManager.CoinHit coinHit = CoinManager.raycast(level, eye, end, bestSq);
        if (coinHit != null) {
            tracer(level, muzzle, coinHit.point(), ParticleTypes.END_ROD);
            ricochet(level, shooter, coinHit.coin(), 1);
        } else if (hitLiving != null) {
            tracer(level, muzzle, hitPoint, ParticleTypes.END_ROD);
            damage(level, shooter, hitLiving, BASE_DAMAGE);
        } else {
            tracer(level, muzzle, end, ParticleTypes.END_ROD);
        }
    }

    private static void ricochet(ServerLevel level, ServerPlayer shooter, ItemEntity coin, int chain) {
        Vec3 pos = coin.getBoundingBox().getCenter();
        CoinManager.consume(coin);

        float pitch = Math.min(2.0f, 0.9f + 0.15f * chain);
        level.playSound(null, coin.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, pitch);
        level.sendParticles(ParticleTypes.WAX_ON, pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.1);

        // priorité aux autres pièces : c'est ça qui crée les chaînes
        if (chain < MAX_CHAIN) {
            ItemEntity next = nextVisibleCoin(level, shooter, pos);
            if (next != null) {
                tracer(level, pos, next.getBoundingBox().getCenter(), ParticleTypes.WAX_ON);
                ricochet(level, shooter, next, chain + 1);
                return;
            }
        }

        LivingEntity target = nearestTarget(level, shooter, pos);
        if (target != null) {
            Vec3 center = target.getBoundingBox().getCenter();
            tracer(level, pos, center, ParticleTypes.WAX_ON);
            damage(level, shooter, target, BASE_DAMAGE * (1.0f + RICOCHET_BONUS * chain));
        }
    }

    private static ItemEntity nextVisibleCoin(ServerLevel level, ServerPlayer shooter, Vec3 from) {
        ItemEntity c = CoinManager.nearest(level, from, RICOCHET_RANGE);
        return (c != null && hasLineOfSight(level, shooter, from, c.getBoundingBox().getCenter())) ? c : null;
    }

    private static LivingEntity nearestTarget(ServerLevel level, ServerPlayer shooter, Vec3 from) {
        AABB area = new AABB(from, from).inflate(RICOCHET_RANGE);
        LivingEntity best = null;
        double bestSq = RICOCHET_RANGE * RICOCHET_RANGE;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != shooter && !e.isSpectator() && !(e instanceof Player)
                        && (!ENEMIES_ONLY || e instanceof Enemy))) {
            Vec3 center = le.getBoundingBox().getCenter();
            double d = from.distanceToSqr(center);
            if (d < bestSq && hasLineOfSight(level, shooter, from, center)) {
                bestSq = d;
                best = le;
            }
        }
        return best;
    }

    private static boolean hasLineOfSight(ServerLevel level, ServerPlayer shooter, Vec3 a, Vec3 b) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter))
                .getType() == HitResult.Type.MISS;
    }

    private static void damage(ServerLevel level, ServerPlayer shooter, LivingEntity target, float amount) {
        target.invulnerableTime = 0; // pas de frames d'invulnérabilité entre deux tirs rapprochés
        target.hurtServer(level, level.damageSources().playerAttack(shooter), amount);

        Vec3 c = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.2);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    private static void tracer(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0e-3) {
            return;
        }
        Vec3 step = delta.scale(0.5 / len);
        Vec3 p = from;
        for (double t = 0; t < len; t += 0.5) {
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            p = p.add(step);
        }
    }

    private Ballistics() {}
}
