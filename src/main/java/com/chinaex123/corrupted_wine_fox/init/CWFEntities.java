package com.chinaex123.corrupted_wine_fox.init;

import com.chinaex123.corrupted_wine_fox.CorruptedWineFox;
import com.chinaex123.corrupted_wine_fox.entity.CorruptedWineFoxBossEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface CWFEntities {
    DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CorruptedWineFox.MODID);

    DeferredHolder<EntityType<?>, EntityType<CorruptedWineFoxBossEntity>> CORRUPTED_WINE_FOX = ENTITY_TYPES.register("corrupted_wine_fox",
            () -> EntityType.Builder.of(CorruptedWineFoxBossEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.0F)
                    .eyeHeight(1.8F)
                    .clientTrackingRange(16)
                    .build("corrupted_wine_fox"));

    static void register(IEventBus eventBus){
        ENTITY_TYPES.register(eventBus);
    }
}
