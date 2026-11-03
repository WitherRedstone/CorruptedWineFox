package com.chinaex123.corrupted_wine_fox;

import com.chinaex123.corrupted_wine_fox.config.CWFConfig;
import com.chinaex123.corrupted_wine_fox.entity.CorruptedWineFoxBossEntity;
import com.chinaex123.corrupted_wine_fox.init.CWFEntities;
import com.chinaex123.corrupted_wine_fox.init.CWFItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(CorruptedWineFox.MODID)
public class CorruptedWineFox {
    public static final String MODID = "corrupted_wine_fox";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CorruptedWineFox(IEventBus modEventBus, ModContainer modContainer) {
        CWFEntities.register(modEventBus);
        CWFItems.register(modEventBus);

        modEventBus.addListener(this::onEntityAttributeCreation);
        modEventBus.addListener(this::onBuildCreativeTabs);
        modContainer.registerConfig(ModConfig.Type.COMMON, CWFConfig.SPEC);
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(CWFEntities.CORRUPTED_WINE_FOX.get(), CorruptedWineFoxBossEntity.createAttributes().build());
    }

    private void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(CWFItems.CORRUPTED_WINE_FOX_SPAWN_EGG.get());
        }
    }
}
