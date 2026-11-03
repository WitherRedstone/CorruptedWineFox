package com.chinaex123.corrupted_wine_fox.client;

import com.chinaex123.corrupted_wine_fox.entity.CorruptedWineFoxBossEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CorruptedWineFoxBossRenderer extends GeoEntityRenderer<CorruptedWineFoxBossEntity> {

    public CorruptedWineFoxBossRenderer(EntityRendererProvider.Context context) {
        super(context, new CorruptedWineFoxBossGeoModel());
        this.withScale(0.7F);
        this.shadowRadius = 0.5F;
    }

    @Override
    protected float getDeathMaxRotation(CorruptedWineFoxBossEntity animatable) {
        // 死亡倒地由 GeckoLib 的 death 动画负责，关闭原版整体 90° 翻转。
        return 0.0F;
    }
}
