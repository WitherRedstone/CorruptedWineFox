package com.chinaex123.corrupted_wine_fox.init;

import com.chinaex123.corrupted_wine_fox.CorruptedWineFox;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public interface CWFItems {
    DeferredRegister.Items ITEMS = DeferredRegister.createItems(CorruptedWineFox.MODID);

    DeferredItem<Item> CORRUPTED_WINE_FOX_SPAWN_EGG = ITEMS.register("corrupted_wine_fox_spawn_egg",
            () -> new DeferredSpawnEggItem(CWFEntities.CORRUPTED_WINE_FOX, 0x2B2B3D, 0x8A5CD6, new Item.Properties()));

    static void register(IEventBus eventBus){
        ITEMS.register(eventBus);
    }
}
