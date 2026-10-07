package com.noah.ultrarevolver;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;

public class UltraRevolver implements ModInitializer {
    public static final String MOD_ID = "ultrarevolver";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModItems.init();
        CoinManager.init();
        ServerTickEvents.END_SERVER_TICK.register(RevolverItem::tickPlayers);

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            output.accept(ModItems.REVOLVER);
            output.accept(ModItems.COIN);
        });
    }
}
