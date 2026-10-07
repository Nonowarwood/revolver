package com.noah.ultrarevolver;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Clic droit : lance une pièce qui monte puis retombe (trajectoire en cloche). Tire dessus avec le revolver pour faire ricocher la balle. */
public class CoinItem extends Item {
    public CoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = player.getLookAngle();
            ItemEntity coin = new ItemEntity(serverLevel, player.getX() + look.x * 0.4, player.getEyeY() - 0.1,
                    player.getZ() + look.z * 0.4, stack.copyWithCount(1));
            coin.setDeltaMovement(look.x * 0.12, 0.27, look.z * 0.12);
            coin.setPickUpDelay(32767); // jamais ramassable ni fusionnable
            coin.setNoGravity(true);    // gravité gérée à la main dans CoinManager (trajectoire en cloche)
            serverLevel.addFreshEntity(coin);
            CoinManager.register(coin);

            serverLevel.playSound(null, player.blockPosition(), ModSounds.COIN_TOSS, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        player.getCooldowns().addCooldown(stack, 5);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
