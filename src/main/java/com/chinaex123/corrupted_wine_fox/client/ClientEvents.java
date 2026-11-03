package com.chinaex123.corrupted_wine_fox.client;

import com.chinaex123.corrupted_wine_fox.CorruptedWineFox;
import com.chinaex123.corrupted_wine_fox.init.CWFEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CorruptedWineFox.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() { }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CWFEntities.CORRUPTED_WINE_FOX.get(), CorruptedWineFoxBossRenderer::new);
    }
}
