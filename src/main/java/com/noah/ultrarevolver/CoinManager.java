package com.noah.ultrarevolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** Garde la trace des pièces en vol (de simples ItemEntity : pas de renderer custom nécessaire). */
public final class CoinManager {
    public static final int LIFETIME_TICKS = 80;
    public static final double GRAVITY = 0.022;   // gravité légère : la coin monte et redescend lentement
    public static final double AIR_DRAG = 0.97;
    public static final double HITBOX_INFLATE = 0.6; // hitbox généreuse pour toucher la pièce facilement

    private static final List<ItemEntity> COINS = new ArrayList<>();

    public record CoinHit(ItemEntity coin, Vec3 point, double distSq) {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> COINS.removeIf(coin -> {
            if (coin.isRemoved()) {
                return true;
            }
            if (coin.tickCount > LIFETIME_TICKS) {
                coin.discard();
                return true;
            }
            // trajectoire en cloche : gravité manuelle (l'entité est en noGravity), léger frottement horizontal
            Vec3 v = coin.getDeltaMovement();
            coin.setDeltaMovement(v.x * AIR_DRAG, v.y - GRAVITY, v.z * AIR_DRAG);
            // la pièce disparaît quand elle retombe au sol
            if (coin.tickCount > 5 && coin.onGround()) {
                coin.discard();
                return true;
            }
            return false;
        }));
    }

    public static void register(ItemEntity coin) {
        COINS.add(coin);
    }

    public static void consume(ItemEntity coin) {
        COINS.remove(coin);
        coin.discard();
    }

    /** Première pièce touchée par le rayon [from, to], plus proche que maxDistSq. */
    public static CoinHit raycast(ServerLevel level, Vec3 from, Vec3 to, double maxDistSq) {
        CoinHit best = null;
        for (ItemEntity coin : COINS) {
            if (coin.isRemoved() || coin.level() != level) {
                continue;
            }
            Optional<Vec3> hit = coin.getBoundingBox().inflate(HITBOX_INFLATE).clip(from, to);
            if (hit.isPresent()) {
                double d = from.distanceToSqr(hit.get());
                if (d < maxDistSq && (best == null || d < best.distSq())) {
                    best = new CoinHit(coin, hit.get(), d);
                }
            }
        }
        return best;
    }

    /** Pièce la plus proche de pos dans le rayon donné. */
    public static ItemEntity nearest(ServerLevel level, Vec3 pos, double range) {
        ItemEntity best = null;
        double bestSq = range * range;
        for (ItemEntity coin : COINS) {
            if (coin.isRemoved() || coin.level() != level) {
                continue;
            }
            double d = pos.distanceToSqr(coin.getBoundingBox().getCenter());
            if (d < bestSq) {
                bestSq = d;
                best = coin;
            }
        }
        return best;
    }

    private CoinManager() {}
}
