package com.chinaex123.corrupted_wine_fox.client;

import com.chinaex123.corrupted_wine_fox.entity.CorruptedWineFoxBossEntity;
import io.redspace.ironsspellbooks.entity.mobs.HumanoidRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class CorruptedWineFoxBossRenderer extends HumanoidRenderer<CorruptedWineFoxBossEntity> {

    public CorruptedWineFoxBossRenderer(EntityRendererProvider.Context context) {
        super(context, new CorruptedWineFoxBossGeoModel());
        this.withScale(0.7F);
        this.shadowRadius = 0.5F;
    }

    @Override
    protected float getDeathMaxRotation(CorruptedWineFoxBossEntity animatable) {
        return 0.0F;
    }
}
