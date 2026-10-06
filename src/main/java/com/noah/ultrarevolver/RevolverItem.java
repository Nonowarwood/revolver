package com.noah.ultrarevolver;

import java.util.function.Consumer;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Revolver animé (modèle Bedrock + animations via GeckoLib). Clic droit : tire. */
public class RevolverItem extends Item implements GeoItem {
    public static final int COOLDOWN_TICKS = 8;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("static_idle");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("shoot");

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
                    // assets : geckolib/models/item/revolver.geo.json, geckolib/animations/item/revolver.animation.json,
                    // textures/item/revolver.png
                    this.renderer = new GeoItemRenderer<>(RevolverItem.this);
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<RevolverItem>("gun", state -> state.setAndContinue(IDLE))
                .triggerableAnim("shoot", SHOOT));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Ballistics.fire(serverLevel, serverPlayer);
            triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "gun", "shoot");
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }
}
