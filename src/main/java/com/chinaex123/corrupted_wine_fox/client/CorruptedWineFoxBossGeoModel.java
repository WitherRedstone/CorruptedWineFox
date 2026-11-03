package com.chinaex123.corrupted_wine_fox.client;

import com.chinaex123.corrupted_wine_fox.entity.CorruptedWineFoxBossEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CorruptedWineFoxBossGeoModel extends GeoModel<CorruptedWineFoxBossEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            "corrupted_wine_fox", "geo/corrupted_wine_fox.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "corrupted_wine_fox", "textures/entity/corrupted_wine_fox.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            "corrupted_wine_fox", "animations/corrupted_wine_fox.animation.json");

    @Override
    public ResourceLocation getModelResource(CorruptedWineFoxBossEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CorruptedWineFoxBossEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CorruptedWineFoxBossEntity animatable) {
        return ANIMATIONS;
    }
}
