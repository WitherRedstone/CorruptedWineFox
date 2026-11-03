package com.chinaex123.corrupted_wine_fox.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CWFConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_PER_HIT;
    public static final ModConfigSpec.DoubleValue MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue SECOND_PHASE_HEALTH;
    public static final ModConfigSpec.DoubleValue THIRD_PHASE_HEALTH;
    public static final ModConfigSpec.BooleanValue USE_PHASE_BARS;
    public static final ModConfigSpec.DoubleValue LOW_HEALTH_THRESHOLD;
    public static final ModConfigSpec.IntValue MAX_CLONES_PHASE_TWO;
    public static final ModConfigSpec.IntValue MAX_CLONES_PHASE_THREE;
    public static final ModConfigSpec.IntValue CLONE_LIFETIME;
    public static final ModConfigSpec.IntValue CLONE_COOLDOWN_PHASE_TWO;
    public static final ModConfigSpec.IntValue CLONE_COOLDOWN_PHASE_THREE;
    public static final ModConfigSpec.IntValue SHADOWSTEP_COOLDOWN;
    public static final ModConfigSpec.IntValue MIRROR_THEFT_COOLDOWN;
    public static final ModConfigSpec.IntValue LIFE_DRAIN_COOLDOWN;
    public static final ModConfigSpec.IntValue HEAL_COOLDOWN;
    public static final ModConfigSpec.DoubleValue CLONE_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue CLONE_ATTACK_DAMAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Boss 相关").push("Boss Config");
        ENABLE_DAMAGE_CAP = builder
                .comment("是否开启 Boss 的单次受击限伤")
                .comment("Whether the boss's per-hit damage cap is enabled")
                .define("enableDamageCap", true);
        MAX_DAMAGE_PER_HIT = builder
                .comment("单次受击最多造成的伤害（护甲减免之后）")
                .comment("Maximum damage the boss can take from a single hit (applied after armor)")
                .defineInRange("maxDamagePerHit", 40.0D, 1.0D, Integer.MAX_VALUE);
        USE_PHASE_BARS = builder
                .comment("血条是否按阶段分成三条（每个阶段一条完整血条）")
                .comment("true: one boss bar per phase; false: a single continuous bar that only changes colour")
                .define("usePhaseBars", true);
        LOW_HEALTH_THRESHOLD = builder
                .comment("血量低于这个值时优先使用治疗法术")
                .comment("Below this health the boss prioritises healing spells")
                .defineInRange("lowHealthThreshold", 100.0D, 0.0D, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Boss 生命值").push("Boss Health");
        MAX_HEALTH = builder
                .comment("Boss 的最大生命值（修改后重新召唤/重新进入世界生效）")
                .comment("Maximum health of the corrupted wine fox")
                .defineInRange("maxHealth", 600.0D, 20.0D, Integer.MAX_VALUE);
        SECOND_PHASE_HEALTH = builder
                .comment("进入二阶段的血量，必须小于最大生命值")
                .comment("Health at which the second phase begins (must be below maxHealth)")
                .defineInRange("secondPhaseHealth", 400.0D, 1.0D, Integer.MAX_VALUE);
        THIRD_PHASE_HEALTH = builder
                .comment("进入三阶段的血量，必须小于二阶段血量")
                .comment("Health at which the third phase begins (must be below secondPhaseHealth)")
                .defineInRange("thirdPhaseHealth", 200.0D, 1.0D, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Boss 分身").push("Boss Clone");
        CLONE_MAX_HEALTH = builder
                .comment("分身的最大生命值")
                .comment("Max health of a clone")
                .defineInRange("cloneMaxHealth", 60.0D, 1.0D, Integer.MAX_VALUE);
        CLONE_ATTACK_DAMAGE = builder
                .comment("分身的攻击力")
                .comment("Attack damage of a clone")
                .defineInRange("cloneAttackDamage", 4.0D, 0.0D, Integer.MAX_VALUE);
        CLONE_LIFETIME = builder
                .comment("分身存活时间（tick）")
                .comment("Clone lifetime in ticks")
                .defineInRange("cloneLifetime", 400, 20, Integer.MAX_VALUE);
        MAX_CLONES_PHASE_TWO = builder
                .comment("二阶段最多同时存在的分身数量")
                .comment("Max simultaneous clones in phase 2")
                .defineInRange("maxClonesPhaseTwo", 2, 0, Integer.MAX_VALUE);
        CLONE_COOLDOWN_PHASE_TWO = builder
                .comment("二阶段召唤分身的冷却（tick）")
                .comment("Clone summon cooldown in phase 2 (ticks)")
                .defineInRange("cloneCooldownPhaseTwo", 400, 0, Integer.MAX_VALUE);
        MAX_CLONES_PHASE_THREE = builder
                .comment("三阶段最多同时存在的分身数量")
                .comment("Max simultaneous clones in phase 3")
                .defineInRange("maxClonesPhaseThree", 4, 0, Integer.MAX_VALUE);
        CLONE_COOLDOWN_PHASE_THREE = builder
                .comment("三阶段召唤分身的冷却（tick）")
                .comment("Clone summon cooldown in phase 3 (ticks)")
                .defineInRange("cloneCooldownPhaseThree", 200, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Boss 技能").push("Boss Skill");
        HEAL_COOLDOWN = builder
                .comment("治疗法术（含强力治疗）的冷却（tick）")
                .comment("Cooldown of healing spells in ticks")
                .defineInRange("healCooldown", 240, 0, Integer.MAX_VALUE);
        SHADOWSTEP_COOLDOWN = builder
                .comment("三阶段瞬移到玩家身后的冷却（tick）")
                .comment("Shadowstep cooldown in phase 3 (ticks)")
                .defineInRange("shadowStepCooldown", 100, 0, Integer.MAX_VALUE);
        MIRROR_THEFT_COOLDOWN = builder
                .comment("偷玩家增益效果的冷却（tick）")
                .comment("Mirror theft cooldown (ticks)")
                .defineInRange("mirrorTheftCooldown", 320, 0, Integer.MAX_VALUE);
        LIFE_DRAIN_COOLDOWN = builder
                .comment("三阶段生命汲取的冷却（tick）")
                .comment("Life drain cooldown in phase 3 (ticks)")
                .defineInRange("lifeDrainCooldown", 120, 0, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private CWFConfig() {
    }
}
