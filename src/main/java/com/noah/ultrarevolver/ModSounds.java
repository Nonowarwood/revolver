package com.noah.ultrarevolver;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final SoundEvent REVOLVER_SHOT = register("revolver_shot");
    public static final SoundEvent COIN_TOSS = register("coin_toss");
    public static final SoundEvent COIN_RICOCHET = register("coin_ricochet");

    private static SoundEvent register(String name) {
        Identifier id = UltraRevolver.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
        // force le chargement de la classe
    }

    private ModSounds() {}
}
