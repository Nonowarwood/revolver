package com.noah.ultrarevolver;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Revolver animé (modèle Bedrock + animations via GeckoLib).
 * Clic droit : tire. Accroupi + clic droit : animation de rechargement (visuelle). Sortir l'arme : animation "draw".
 */
public class RevolverItem extends Item implements GeoItem {
    public static final int COOLDOWN_TICKS = 8;
    public static final int RELOAD_TICKS = 70;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("static_idle");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("shoot");
    private static final RawAnimation DRAW = RawAnimation.begin().thenPlay("draw");
    private static final RawAnimation RELOAD = RawAnimation.begin().thenPlay("reload_tactical");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public RevolverItem(Properties properties) {
        super(properties);
        GeoItem.registerSyncedAnimatable(this); // nécessaire pour déclencher des animations depuis le serveur
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<RevolverItem> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoItemRenderer<>(RevolverItem.this);
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<RevolverItem>("gun", state -> state.setAndContinue(IDLE))
                .triggerableAnim("shoot", SHOOT)
                .triggerableAnim("draw", DRAW)
                .triggerableAnim("reload", RELOAD));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean reload = player.isShiftKeyDown();

        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            long id = GeoItem.getOrAssignId(stack, serverLevel);
            if (reload) {
                triggerAnim(player, id, "gun", "reload");
            } else {
                Ballistics.fire(serverLevel, serverPlayer);
                triggerAnim(player, id, "gun", "shoot");
            }
        }
        player.getCooldowns().addCooldown(stack, reload ? RELOAD_TICKS : COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------------------------------------
    // Animation "draw" quand on sort le revolver. L'identifiant GeckoLib du stack est assigné dès que
    // l'arme est en main, puis l'animation est lancée quelques ticks plus tard (le temps que le client
    // reçoive l'identifiant, sinon le déclenchement est perdu).
    // ------------------------------------------------------------------------------------------------
    private static final class Held {
        long id;
        int ticks;
        boolean drawn;
    }

    private static final Map<UUID, Held> HELD = new HashMap<>();

    public static void tickPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof RevolverItem revolver && player.level() instanceof ServerLevel level) {
                long id = GeoItem.getOrAssignId(held, level);
                Held state = HELD.computeIfAbsent(player.getUUID(), u -> new Held());
                if (state.id != id) {
                    state.id = id;
                    state.ticks = 0;
                    state.drawn = false;
                }
                state.ticks++;
                if (!state.drawn && state.ticks >= 4) {
                    state.drawn = true;
                    revolver.triggerAnim(player, id, "gun", "draw");
                }
            } else {
                HELD.remove(player.getUUID());
            }
        }
    }
}
