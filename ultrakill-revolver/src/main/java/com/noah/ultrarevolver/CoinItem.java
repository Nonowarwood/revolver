package com.noah.ultrarevolver;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Clic droit : lance une pièce en l'air. Tire dessus avec le revolver pour faire ricocher la balle. */
public class CoinItem extends Item {
    public CoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = player.getLookAngle();
            ItemEntity coin = new ItemEntity(serverLevel, player.getX(), player.getEyeY() - 0.2, player.getZ(),
                    stack.copyWithCount(1));
            coin.setDeltaMovement(look.x * 0.25, 0.32 + look.y * 0.15, look.z * 0.25);
            coin.setPickUpDelay(32767); // 32767 = ne peut jamais être ramassée ni fusionnée
            serverLevel.addFreshEntity(coin);
            CoinManager.register(coin);

            serverLevel.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.8f, 1.8f);
        }

        player.getCooldowns().addCooldown(stack, 5);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
