package com.noah.ultrarevolver;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item REVOLVER = register("revolver", RevolverItem::new, new Item.Properties().stacksTo(1));
    public static final Item COIN = register("coin", CoinItem::new, new Item.Properties());

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, UltraRevolver.id(name));
        T item = factory.apply(props.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void init() {
        // force le chargement de la classe (et donc l'enregistrement des items)
    }

    private ModItems() {}
}
