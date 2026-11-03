package com.chinaex123.corrupted_wine_fox.entity;

import com.chinaex123.corrupted_wine_fox.CorruptedWineFox;
import com.chinaex123.corrupted_wine_fox.config.CWFConfig;
import com.chinaex123.corrupted_wine_fox.entity.ai.CorruptedWineFoxBossAttackGoal;
import com.chinaex123.corrupted_wine_fox.init.CWFEntities;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.entity.mobs.goals.PatrolNearLocationGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.melee.AttackAnimationData;
import io.redspace.ironsspellbooks.entity.mobs.wizards.fire_boss.FireBossMoveControl;
import io.redspace.ironsspellbooks.entity.mobs.wizards.fire_boss.NotIdioticNavigation;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;
import java.util.function.Supplier;

public class CorruptedWineFoxBossEntity extends AbstractBossEntity {

    public static final float MAX_BOSS_HEALTH = 600.0F;

    public static float getConfiguredMaxHealth() {
        return (float) Math.max(20.0D, CWFConfig.MAX_HEALTH.get());
    }

    public static float getConfiguredSecondPhaseHealth() {
        float max = getConfiguredMaxHealth();
        return Mth.clamp(CWFConfig.SECOND_PHASE_HEALTH.get().floatValue(), 1.0F, max - 1.0F);
    }

    public static float getConfiguredThirdPhaseHealth() {
        float second = getConfiguredSecondPhaseHealth();
        return Mth.clamp(CWFConfig.THIRD_PHASE_HEALTH.get().floatValue(), 1.0F, second - 1.0F);
    }

    private static final int TRANSITION_TIME = 200;

    private static final List<AttackAnimationData> FIRST_PHASE_MOVESET = List.of(
            new AttackAnimationData(9, "attack", 6),
            new AttackAnimationData(10, "attack", 7),
            new AttackAnimationData(21, "attack", 14));

    private static final List<AttackAnimationData> SECOND_PHASE_MOVESET = List.of(
            new AttackAnimationData(9, "attack", 6),
            new AttackAnimationData(8, "attack", 4),
            new AttackAnimationData(10, "attack", 7),
            new AttackAnimationData(21, "attack", 14));

    private static final List<AttackAnimationData> THIRD_PHASE_MOVESET = List.of(
            new AttackAnimationData(9, "attack", 6),
            new AttackAnimationData(8, "attack", 4),
            new AttackAnimationData(10, "attack", 7),
            new AttackAnimationData(26, "attack", 13),
            new AttackAnimationData(43, "attack", 12, 30),
            new AttackAnimationData(48, "attack", 30));

    private static final String BOSS_TEAM_NAME = "corrupted_wine_fox_boss_family";

    private CorruptedWineFoxBossAttackGoal attackGoal;

    private boolean isClone;
    private int cloneLifetime;
    private int cloneSummonCooldown;
    private int shadowstepCooldown;
    private int mirrorTheftCooldown;
    private int lifeDrainCooldown;
    private int adaptationTicks;
    private String adaptationSignature = "";
    private String lastDamageSignature = "";
    private int repeatedDamageCount;
    private int recentHits;
    private boolean transitionPlayed;
    private final Set<UUID> activeClones = new HashSet<>();

    public CorruptedWineFoxBossEntity(EntityType<? extends CorruptedWineFoxBossEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FireBossMoveControl(this);
        this.applyConfiguredHealth();
    }

    private void applyConfiguredHealth() {
        AttributeInstance attribute = this.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(this.isClone ? CWFConfig.CLONE_MAX_HEALTH.get().floatValue() : getConfiguredMaxHealth());
        }
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_BOSS_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0D)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 3.0D)
                .add(AttributeRegistry.MAX_MANA, 600.0D)
                .add(AttributeRegistry.MANA_REGEN, 12.0D)
                .add(AttributeRegistry.SPELL_POWER, 1.0D);
    }

    @SafeVarargs
    private static List<AbstractSpell> spellsOf(Supplier<? extends AbstractSpell>... suppliers) {
        List<AbstractSpell> spells = new ArrayList<>();
        for (Supplier<? extends AbstractSpell> supplier : suppliers) {
            AbstractSpell spell = supplier == null ? null : supplier.get();
            if (spell == null || spell == SpellRegistry.none()) {
                CorruptedWineFox.LOGGER.warn("Missing Iron's Spells entry {}, skipped", supplier);
                continue;
            }
            spells.add(spell);
        }
        return spells;
    }

    @Override
    protected void registerGoals() {
        this.registerFirstPhaseGoals();
    }

    private void registerFirstPhaseGoals() {
        this.clearGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.attackGoal = this.createAttackGoal(FIRST_PHASE_MOVESET, 1.15D, 40, 70,
                spellsOf(
                        SpellRegistry.FLAMING_STRIKE_SPELL, // 烈焰斩击
                        SpellRegistry.MAGIC_MISSILE_SPELL,  // 魔法飞弹
                        SpellRegistry.MAGIC_ARROW_SPELL,    // 魔法箭
                        SpellRegistry.CHAIN_LIGHTNING_SPELL,    // 连锁闪电
                        SpellRegistry.BLOOD_NEEDLES_SPELL,  // 猩红之刺
                        SpellRegistry.FIREBOLT_SPELL,   // 火焰箭
                        SpellRegistry.FIREBALL_SPELL,   // 火球术
                        SpellRegistry.MAGMA_BOMB_SPELL, // 岩浆炸弹
                        SpellRegistry.POISON_ARROW_SPELL,   // 毒箭射击
                        SpellRegistry.POISON_SPLASH_SPELL   // 毒液飞溅
                ),
                spellsOf(
                        SpellRegistry.FROSTWAVE_SPELL   // 冰浪
                ),
                spellsOf(
                        SpellRegistry.COUNTERSPELL_SPELL,   // 法术反制
                        SpellRegistry.INVISIBILITY_SPELL,   // 隐身术
                        SpellRegistry.ARROW_VOLLEY_SPELL,   // 万箭齐发
                        SpellRegistry.THROW_SPELL,  // 投掷
                        SpellRegistry.THUNDERSTORM_SPELL,   // 雷暴
                        SpellRegistry.BLIGHT_SPELL, // 枯萎术
                        SpellRegistry.ROOT_SPELL    // 纠缠根须
                ),
                0.45F, 40, 70);
        this.goalSelector.addGoal(2, this.attackGoal);
        this.goalSelector.addGoal(6, new PatrolNearLocationGoal(this, 32.0F, 0.9D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private void registerSecondPhaseGoals() {
        this.clearGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.attackGoal = this.createAttackGoal(SECOND_PHASE_MOVESET, 1.25D, 30, 55,
                spellsOf(
                        SpellRegistry.BLOOD_SLASH_SPELL,   // 猩红斩击
                        SpellRegistry.SHADOW_SLASH,   // 暗影斩击
                        SpellRegistry.BLOOD_NEEDLES_SPELL,   // 猩红之刺
                        SpellRegistry.FIREBOLT_SPELL,   // 火焰箭
                        SpellRegistry.SNOWBALL_SPELL,   // 雪球术
                        SpellRegistry.LIGHTNING_BOLT_SPELL,   // 落雷
                        SpellRegistry.FIREBALL_SPELL,   // 火球术
                        SpellRegistry.MAGMA_BOMB_SPELL,   // 岩浆炸弹
                        SpellRegistry.POISON_SPLASH_SPELL   // 毒液飞溅
                ),
                spellsOf(
                        SpellRegistry.ARCANE_SHACKLE_SPELL,   // 奥术镣铐
                        SpellRegistry.SHIELD_SPELL,   // 护盾术
                        SpellRegistry.ICE_TOMB_SPELL,   // 冰霜之墓
                        SpellRegistry.OAKSKIN_SPELL   // 橡肤
                ),
                spellsOf(
                        SpellRegistry.COUNTERSPELL_SPELL,   // 法术反制
                        SpellRegistry.TELEPORT_SPELL,   // 传送术
                        SpellRegistry.ECHOING_STRIKES_SPELL,   // 回响打击
                        SpellRegistry.HEAL_SPELL,   // 治疗
                        SpellRegistry.BLOOD_STEP_SPELL,   // 血步
                        SpellRegistry.INVISIBILITY_SPELL,   // 隐身术
                        SpellRegistry.ARROW_VOLLEY_SPELL,   // 万箭齐发
                        SpellRegistry.THROW_SPELL,   // 投掷
                        SpellRegistry.FANG_SWIRL_SPELL,   // 尖牙旋涡
                        SpellRegistry.THUNDERSTORM_SPELL,   // 雷暴
                        SpellRegistry.BLIGHT_SPELL  // 枯萎术
                ),
                0.55F, 30, 55);
        this.goalSelector.addGoal(2, this.attackGoal);
        this.goalSelector.addGoal(6, new PatrolNearLocationGoal(this, 32.0F, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private void registerThirdPhaseGoals() {
        this.clearGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.attackGoal = this.createAttackGoal(THIRD_PHASE_MOVESET, 1.4D, 20, 40,
                spellsOf(
                        SpellRegistry.BLOOD_SLASH_SPELL, // 猩红斩击
                        SpellRegistry.SHADOW_SLASH, // 暗影斩击
                        SpellRegistry.FIREBOLT_SPELL, // 火焰箭
                        SpellRegistry.SNOWBALL_SPELL, // 雪球术
                        SpellRegistry.LIGHTNING_BOLT_SPELL, // 落雷
                        SpellRegistry.FIREBALL_SPELL, // 火球术
                        SpellRegistry.MAGMA_BOMB_SPELL, // 岩浆炸弹
                        SpellRegistry.POISON_SPLASH_SPELL // 毒液飞溅
                ),
                spellsOf(
                        SpellRegistry.ARCANE_SHACKLE_SPELL, // 奥术镣铐
                        SpellRegistry.ICE_TOMB_SPELL, // 冰霜之墓
                        SpellRegistry.OAKSKIN_SPELL // 橡肤
                ),
                spellsOf(
                        SpellRegistry.COUNTERSPELL_SPELL, // 法术反制
                        SpellRegistry.EVASION_SPELL, // 末影闪避
                        SpellRegistry.TELEPORT_SPELL, // 传送术
                        SpellRegistry.ECHOING_STRIKES_SPELL, // 回响打击
                        SpellRegistry.HEAL_SPELL, // 治疗
                        SpellRegistry.BLOOD_STEP_SPELL, // 血步
                        SpellRegistry.INVISIBILITY_SPELL, // 隐身术
                        SpellRegistry.ARROW_VOLLEY_SPELL, // 万箭齐发
                        SpellRegistry.THROW_SPELL, // 投掷
                        SpellRegistry.FANG_SWIRL_SPELL, // 尖牙旋涡
                        SpellRegistry.CHARGE_SPELL, // 超负荷
                        SpellRegistry.THUNDERSTORM_SPELL, // 雷暴
                        SpellRegistry.BLIGHT_SPELL // 枯萎术
                ),
                0.6F, 18, 34);
        this.goalSelector.addGoal(2, this.attackGoal);
        this.goalSelector.addGoal(6, new PatrolNearLocationGoal(this, 32.0F, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 20.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    private CorruptedWineFoxBossAttackGoal createAttackGoal(List<AttackAnimationData> moveset, double speed, int intervalMin,
                                                            int intervalMax, List<AbstractSpell> attack, List<AbstractSpell> defense, List<AbstractSpell> support,
                                                            float meleeChance, int spellCooldownMin, int spellCooldownMax) {
        CorruptedWineFoxBossAttackGoal goal = new CorruptedWineFoxBossAttackGoal(this, moveset, speed, intervalMin, intervalMax);
        goal.setMeleeAttackInverval(10, 30);
        goal.setMeleeMovespeedModifier(1.5F);
        goal.setSpellQuality(1.0F, 2.0F);
        goal.setSpells(attack, defense, List.of(), support);
        goal.setAttackSpells(attack);
        goal.setDefenseSpells(defense);
        goal.setHealSpells(spellsOf(SpellRegistry.HEAL_SPELL, SpellRegistry.GREATER_HEAL_SPELL));
        goal.setSpellCooldownRange(spellCooldownMin, spellCooldownMax);
        goal.setMeleeChance(meleeChance);
        return goal;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        this.tickCooldowns();

        if (this.isClone) {
            this.tickClone();
            return;
        }

        int phase = this.getPhase();
        if (phase != Phases.Transitioning.ordinal()) {
            int desired = this.getDesiredPhase();
            if (desired > phase) {
                this.changePhase(desired);
                phase = this.getPhase();
            }
        }

        switch (phase) {
            case 1 -> this.tickSecondPhase();
            case 2 -> this.tickTransition();
            case 3 -> this.tickThirdPhase();
            default -> {
            }
        }
    }

    private int getDesiredPhase() {
        if (this.getHealth() <= getConfiguredThirdPhaseHealth()) {
            return Phases.ThirdPhase.ordinal();
        }
        if (this.getHealth() <= getConfiguredSecondPhaseHealth()) {
            return Phases.SecondPhase.ordinal();
        }
        return Phases.FirstPhase.ordinal();
    }

    private void changePhase(int desired) {
        if (desired == Phases.ThirdPhase.ordinal() && !this.transitionPlayed) {
            this.transitionPlayed = true;
            this.setPhase(Phases.Transitioning.ordinal());
            return;
        }
        this.setPhase(desired);
    }

    private void tickCooldowns() {
        if (this.recentHits > 0) {
            this.recentHits--;
        }
        if (this.adaptationTicks > 0) {
            this.adaptationTicks--;
            if (this.adaptationTicks == 0) {
                this.adaptationSignature = "";
            }
        }
        if (this.cloneSummonCooldown > 0) {
            this.cloneSummonCooldown--;
        }
        if (this.shadowstepCooldown > 0) {
            this.shadowstepCooldown--;
        }
        if (this.mirrorTheftCooldown > 0) {
            this.mirrorTheftCooldown--;
        }
        if (this.lifeDrainCooldown > 0) {
            this.lifeDrainCooldown--;
        }
    }

    private void tickClone() {
        if (--this.cloneLifetime <= 0) {
            this.playSound(SoundEvents.WITHER_DEATH, 0.8F, 1.7F);
            this.discard();
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && this.distanceToSqr(target) < 3.0D * 3.0D) {
            Vec3 away = this.position().subtract(target.position()).normalize().scale(0.06D);
            this.push(away.x, 0.0D, away.z);
        }
    }

    private void tickSecondPhase() {
        if (this.cloneSummonCooldown <= 0 && this.countActiveClones() < CWFConfig.MAX_CLONES_PHASE_TWO.get()) {
            try {
                this.summonClones(1, CWFConfig.CLONE_LIFETIME.get());
            } catch (RuntimeException exception) {
                CorruptedWineFox.LOGGER.error("Failed to summon a corrupted wine fox clone", exception);
            }
            this.cloneSummonCooldown = CWFConfig.CLONE_COOLDOWN_PHASE_TWO.get();
        }
    }

    private void tickThirdPhase() {
        Player target = this.getTarget() instanceof Player player ? player
                : this.level().getNearestPlayer(this, 32.0D);
        if (target == null) {
            return;
        }

        if (this.shadowstepCooldown <= 0 && this.distanceToSqr(target) > 8.0D * 8.0D) {
            if (this.shadowstepBehind(target)) {
                this.shadowstepCooldown = CWFConfig.SHADOWSTEP_COOLDOWN.get();
            }
        }

        if (this.mirrorTheftCooldown <= 0 && this.distanceToSqr(target) < 18.0D * 18.0D) {
            if (this.tryMirrorTheft(target)) {
                this.mirrorTheftCooldown = CWFConfig.MIRROR_THEFT_COOLDOWN.get();
            }
        }

        if (this.lifeDrainCooldown <= 0) {
            this.lifeDrain();
            this.lifeDrainCooldown = CWFConfig.LIFE_DRAIN_COOLDOWN.get();
        }

        if (this.cloneSummonCooldown <= 0 && this.countActiveClones() < CWFConfig.MAX_CLONES_PHASE_THREE.get()) {
            try {
                this.summonClones(1, CWFConfig.CLONE_LIFETIME.get());
            } catch (RuntimeException exception) {
                CorruptedWineFox.LOGGER.error("Failed to summon a corrupted wine fox clone", exception);
            }
            this.cloneSummonCooldown = CWFConfig.CLONE_COOLDOWN_PHASE_THREE.get();
        }
    }

    private void lifeDrain() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        float healed = 0.0F;
        for (Player player : serverLevel.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(8.0D))) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (player.hurt(this.damageSources().magic(), 4.0F)) {
                healed += 4.0F;
            }
            serverLevel.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
                    10, 0.3D, 0.6D, 0.3D, 0.02D);
        }
        if (healed > 0.0F) {
            this.heal(Math.min(healed, 12.0F));
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.2D, this.getZ(),
                    16, 0.4D, 0.7D, 0.4D, 0.02D);
        }
    }

    public void onMeleeHit(LivingEntity target) {
        if (this.getPhase() == Phases.ThirdPhase.ordinal() && !this.level().isClientSide) {
            this.heal(2.0F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + 1.0D,
                        target.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.02D);
            }
        }
    }

    private int countActiveClones() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.activeClones.removeIf(uuid -> {
                Entity entity = serverLevel.getEntity(uuid);
                return !(entity instanceof CorruptedWineFoxBossEntity boss) || !boss.isClone || !boss.isAlive();
            });
        } else {
            this.activeClones.clear();
        }
        return this.activeClones.size();
    }

    private void summonClones(int count, int lifetime) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity currentTarget = this.getTarget();

        for (int index = 0; index < count; index++) {
            CorruptedWineFoxBossEntity clone = CWFEntities.CORRUPTED_WINE_FOX.get().create(serverLevel);
            if (clone == null) {
                continue;
            }

            clone.isClone = true;
            clone.cloneLifetime = lifetime;
            clone.skipSpawnAnimation();
            if (this.getCustomName() != null) {
                clone.setCustomName(this.getCustomName().copy());
            }
            clone.setCustomNameVisible(false);

            setBaseValue(clone, Attributes.MAX_HEALTH, CWFConfig.CLONE_MAX_HEALTH.get().floatValue());
            setBaseValue(clone, Attributes.ATTACK_DAMAGE, CWFConfig.CLONE_ATTACK_DAMAGE.get().floatValue());
            setBaseValue(clone, Attributes.MOVEMENT_SPEED, 0.3D);
            clone.setHealth(CWFConfig.CLONE_MAX_HEALTH.get().floatValue());

            double angle = (Math.PI * 2.0D / Math.max(1, count)) * index + this.random.nextDouble() * 0.7D;
            double radius = 3.5D + this.random.nextDouble() * 1.5D;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY();
            for (double dy : new double[]{0.0D, 1.0D, -1.0D, 2.0D}) {
                double candidateY = this.getY() + dy;
                if (serverLevel.noCollision(clone, clone.getBoundingBox()
                        .move(x - clone.getX(), candidateY - clone.getY(), z - clone.getZ()))) {
                    y = candidateY;
                    break;
                }
            }

            clone.moveTo(x, y, z, this.getYRot(), 0.0F);
            serverLevel.addFreshEntity(clone);
            clone.joinBossTeam();
            if (currentTarget != null) {
                clone.setTarget(currentTarget);
            }
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.0D, z, 24, 0.4D, 0.8D, 0.4D, 0.1D);
            this.activeClones.add(clone.getUUID());
        }

        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.6F);
    }

    private static void setBaseValue(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private boolean shadowstepBehind(Player target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Vec3 look = target.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            return false;
        }
        horizontal = horizontal.normalize();
        Vec3 behind = target.position().subtract(horizontal.scale(3.0D));

        for (double dy : new double[]{0.0D, 1.0D, -1.0D, 2.0D}) {
            double candidateY = target.getY() + dy;
            if (!serverLevel.noCollision(this, this.getBoundingBox()
                    .move(behind.x - this.getX(), candidateY - this.getY(), behind.z - this.getZ()))) {
                continue;
            }
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.0D, this.getZ(),
                    28, 0.4D, 0.8D, 0.4D, 0.12D);
            this.teleportTo(behind.x, candidateY, behind.z);
            serverLevel.sendParticles(ParticleTypes.PORTAL, behind.x, candidateY + 1.0D, behind.z,
                    32, 0.4D, 0.8D, 0.4D, 0.15D);
            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.7F);
            this.getNavigation().stop();
            this.getLookControl().setLookAt(target, 60.0F, 60.0F);
            return true;
        }
        return false;
    }

    private boolean tryMirrorTheft(Player target) {
        List<Holder<MobEffect>> candidates = new ArrayList<>(List.of(
                MobEffects.MOVEMENT_SPEED,
                MobEffects.DAMAGE_BOOST,
                MobEffects.DAMAGE_RESISTANCE,
                MobEffects.REGENERATION,
                MobEffects.DIG_SPEED,
                MobEffects.ABSORPTION,
                MobEffects.FIRE_RESISTANCE,
                MobEffects.JUMP));
        Collections.shuffle(candidates, new java.util.Random(this.random.nextLong()));

        for (Holder<MobEffect> holder : candidates) {
            MobEffectInstance stolen = target.getEffect(holder);
            if (stolen == null) {
                continue;
            }
            target.removeEffect(holder);
            int duration = Mth.randomBetweenInclusive(this.random, 600, 1200);
            int amplifier = Mth.randomBetweenInclusive(this.random, 0, Math.max(0, stolen.getAmplifier()));
            this.addEffect(new MobEffectInstance(holder, duration, amplifier, false, true, true));
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.2D, this.getZ(),
                        18, 0.4D, 0.7D, 0.4D, 0.02D);
            }
            this.playSound(SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.8F);
            return true;
        }
        return false;
    }

    private float applyDamageAdaptation(DamageSource source, float amount) {
        String signature = source.getMsgId();

        if (this.adaptationTicks > 0 && signature.equals(this.adaptationSignature)) {
            amount *= 0.65F;
        }

        if (signature.equals(this.lastDamageSignature)) {
            this.repeatedDamageCount++;
        } else {
            this.lastDamageSignature = signature;
            this.repeatedDamageCount = 1;
        }

        if (this.repeatedDamageCount >= 3 && !signature.equals(this.adaptationSignature)) {
            this.adaptationSignature = signature;
            this.adaptationTicks = this.getPhase() >= Phases.ThirdPhase.ordinal() ? 220 : 160;
            this.repeatedDamageCount = 0;
            this.playSound(SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
        }
        return amount;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean interrupted = !this.level().isClientSide && this.isCastingHealSpell();
        if (!this.level().isClientSide && !this.isClone) {
            this.recentHits = Math.min(this.recentHits + 2, 40);
            amount = this.applyDamageAdaptation(source, amount);
        }
        boolean damaged = super.hurt(source, amount);
        if (damaged && interrupted && this.isCasting()) {
            this.playSound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.7F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.2D, this.getZ(),
                        16, 0.4D, 0.6D, 0.4D, 0.02D);
            }
        }
        return damaged;
    }

    private boolean isCastingHealSpell() {
        if (!this.isCasting()) {
            return false;
        }
        SpellData casting = this.getMagicData().getCastingSpell();
        if (casting == null) {
            return false;
        }
        AbstractSpell spell = casting.getSpell();
        return spell == SpellRegistry.HEAL_SPELL.get() || spell == SpellRegistry.GREATER_HEAL_SPELL.get();
    }

    public boolean wantsDefensiveSpell() {
        return this.recentHits >= 6;
    }

    public boolean wantsHealSpell() {
        return this.getHealth() <= CWFConfig.LOW_HEALTH_THRESHOLD.get().floatValue();
    }

    @Override
    protected float getBossBarProgress() {
        float max = getConfiguredMaxHealth();
        if (!CWFConfig.USE_PHASE_BARS.get()) {
            return Mth.clamp(this.getHealth() / Math.max(1.0F, max), 0.0F, 1.0F);
        }
        float second = getConfiguredSecondPhaseHealth();
        float third = getConfiguredThirdPhaseHealth();
        float floor;
        float span;
        switch (this.getPhase()) {
            case 0 -> {
                floor = second;
                span = max - second;
            }
            case 1 -> {
                floor = third;
                span = second - third;
            }
            default -> {
                floor = 0.0F;
                span = third;
            }
        }
        return Mth.clamp((this.getHealth() - floor) / Math.max(1.0F, span), 0.0F, 1.0F);
    }

    @Override
    protected boolean showsBossBar() {
        return !this.isClone;
    }

    @Override
    protected LookControl createLookControl() {
        return new LookControl(this) {
            @Override
            protected float rotateTowards(float from, float to, float maxDelta) {
                return super.rotateTowards(from, to, maxDelta * 2.5F);
            }

            @Override
            protected boolean resetXRotOnTick() {
                return getTarget() == null;
            }
        };
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NotIdioticNavigation(this, level);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!this.level().isClientSide) {
            this.applyConfiguredHealth();
            this.joinBossTeam();
        }
    }

    private void joinBossTeam() {
        Scoreboard scoreboard = this.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(BOSS_TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(BOSS_TEAM_NAME);
            team.setAllowFriendlyFire(false);
            team.setSeeFriendlyInvisibles(true);
        }
        scoreboard.addPlayerToTeam(this.getScoreboardName(), team);
    }

    @Override
    public void die(DamageSource source) {
        if (this.isClone) {
            this.playSound(SoundEvents.WITHER_DEATH, 0.8F, 1.7F);
            this.discard();
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0D, this.getZ(),
                    80, 0.6D, 1.2D, 0.6D, 0.05D);
        }
        super.die(source);
    }

    private void discardAllClones() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.activeClones.clear();
            return;
        }
        for (UUID uuid : this.activeClones) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof CorruptedWineFoxBossEntity clone && clone.isClone) {
                serverLevel.sendParticles(ParticleTypes.SOUL, clone.getX(), clone.getY() + 1.0D, clone.getZ(),
                        20, 0.4D, 0.8D, 0.4D, 0.03D);
                clone.playSound(SoundEvents.WITHER_DEATH, 0.8F, 1.7F);
                clone.discard();
            }
        }
        this.activeClones.clear();
    }

    @Override
    protected void onPhaseChanged(int oldPhase, int newPhase) {
        if (this.level().isClientSide) {
            return;
        }

        this.bossEvent.setName(this.buildBossName());

        if (newPhase == Phases.FirstPhase.ordinal()) {
            this.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
            this.bossEvent.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            this.registerFirstPhaseGoals();
        } else if (newPhase == Phases.SecondPhase.ordinal()) {
            this.bossEvent.setColor(BossEvent.BossBarColor.PINK);
            this.bossEvent.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            this.registerSecondPhaseGoals();
            if (oldPhase == Phases.FirstPhase.ordinal()) {
                this.playSound(SoundEvents.WITHER_AMBIENT, 2.0F, 0.8F);
            }
        } else if (newPhase == Phases.Transitioning.ordinal()) {
            this.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
            this.bossEvent.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
            this.phaseTransitionTimer = TRANSITION_TIME;
            this.clearGoals();
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1.0D, this.getZ(),
                        3, 0.5D, 0.5D, 0.5D, 0.0D);
                this.spawnRingOfLightning(serverLevel, 8, 5.0D);
            }
            this.playSound(SoundEvents.WITHER_AMBIENT, 2.0F, 0.6F);
        } else if (newPhase == Phases.ThirdPhase.ordinal()) {
            this.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
            this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
            this.registerThirdPhaseGoals();
            this.serverTriggerAnimation("");
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 1, false, false, false));
            if (oldPhase == Phases.Transitioning.ordinal()) {
                this.playSound(SoundEvents.WITHER_SPAWN, 2.0F, 0.7F);
                if (this.level() instanceof ServerLevel serverLevel) {
                    this.spawnRingOfLightning(serverLevel, 12, 7.0D);
                }
            }
        }
    }

    private void tickTransition() {
        this.phaseTransitionTimer--;

        Player nearest = this.level().getNearestPlayer(this, 32.0D);
        if (nearest != null) {
            this.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
        }

        if (this.level() instanceof ServerLevel serverLevel
                && (this.phaseTransitionTimer == TRANSITION_TIME - 20 || this.phaseTransitionTimer == 60
                || this.phaseTransitionTimer == 1)) {
            this.spawnRingOfLightning(serverLevel, 8, 6.0D);
            this.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.8F);
        }

        if (this.phaseTransitionTimer <= 0) {
            this.setPhase(Phases.ThirdPhase.ordinal());
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        SpawnGroupData spawnGroupData) {
        this.setCustomName(Component.translatable("entity.corrupted_wine_fox.corrupted_wine_fox"));
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsClone", this.isClone);
        tag.putInt("CloneLifetime", this.cloneLifetime);
        tag.putBoolean("TransitionPlayed", this.transitionPlayed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.isClone = tag.getBoolean("IsClone");
        this.cloneLifetime = tag.getInt("CloneLifetime");
        this.transitionPlayed = tag.getBoolean("TransitionPlayed");

        if (this.isClone) {
            this.skipSpawnAnimation();
            if (!this.level().isClientSide) {
                setBaseValue(this, Attributes.MAX_HEALTH, CWFConfig.CLONE_MAX_HEALTH.get().floatValue());
                setBaseValue(this, Attributes.ATTACK_DAMAGE, CWFConfig.CLONE_ATTACK_DAMAGE.get().floatValue());
                setBaseValue(this, Attributes.MOVEMENT_SPEED, 0.3D);
                this.setHealth(Math.min(this.getHealth(), CWFConfig.CLONE_MAX_HEALTH.get().floatValue()));
            }
        } else if (!this.level().isClientSide) {
            this.restorePhaseSetup();
        }
    }

    private void restorePhaseSetup() {
        this.bossEvent.setName(this.buildBossName());
        switch (this.getPhase()) {
            case 1 -> {
                this.bossEvent.setColor(BossEvent.BossBarColor.PINK);
                this.bossEvent.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
                this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                this.registerSecondPhaseGoals();
            }
            case 2 -> {
                this.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
                this.bossEvent.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
                this.clearGoals();
                this.goalSelector.addGoal(0, new FloatGoal(this));
            }
            case 3 -> {
                this.bossEvent.setColor(BossEvent.BossBarColor.YELLOW);
                this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
                this.registerThirdPhaseGoals();
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 1, false, false, false));
            }
            default -> {
                this.bossEvent.setColor(BossEvent.BossBarColor.BLUE);
                this.bossEvent.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
                this.removeEffect(MobEffects.DAMAGE_RESISTANCE);
                this.registerFirstPhaseGoals();
            }
        }
    }

    public CorruptedWineFoxBossAttackGoal getAttackGoal() {
        return this.attackGoal;
    }
}
