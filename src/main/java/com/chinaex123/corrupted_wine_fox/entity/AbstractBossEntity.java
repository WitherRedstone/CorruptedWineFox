package com.chinaex123.corrupted_wine_fox.entity;

import com.chinaex123.corrupted_wine_fox.config.CWFConfig;
import io.redspace.ironsspellbooks.entity.mobs.IAnimatedAttacker;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.*;

public abstract class AbstractBossEntity extends AbstractSpellCastingMob implements Enemy, IAnimatedAttacker {

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(AbstractBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SPAWN_TIMER = SynchedEntityData.defineId(AbstractBossEntity.class, EntityDataSerializers.INT);
    protected static final RawAnimation TRANSITION_ANIMATION = RawAnimation.begin().thenPlay("phase_transition");
    protected static final RawAnimation SPAWN_ANIMATION = RawAnimation.begin().thenPlay("spawn");
    protected static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    protected static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    protected static final RawAnimation RUN_ANIMATION = RawAnimation.begin().thenLoop("run");
    protected static final RawAnimation SWIM_ANIMATION = RawAnimation.begin().thenLoop("swim");
    protected static final RawAnimation SWIM_STAND_ANIMATION = RawAnimation.begin().thenLoop("swim_stand");
    protected static final RawAnimation ATTACKED_ANIMATION = RawAnimation.begin().thenPlay("attacked");
    protected static final RawAnimation DEATH_ANIMATION = RawAnimation.begin().thenPlayAndHold("death");

    /** 出场动画持续 tick */
    protected static final int SPAWN_TICKS = 100;

    public final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);

    protected final AnimationController<AbstractBossEntity> transitionController;
    protected final AnimationController<AbstractBossEntity> meleeController;
    protected final AnimationController<AbstractBossEntity> spawnController;
    protected final AnimationController<AbstractBossEntity> locomotionController;
    protected final AnimationController<AbstractBossEntity> hurtController;
    protected final AnimationController<AbstractBossEntity> deathController;
    protected int phaseTransitionTimer;
    private int spawnTimer;
    protected RawAnimation animationToPlay;
    private int clientAttackAnimationLength;
    private boolean stopClientAnimation;

    protected AbstractBossEntity(EntityType<? extends AbstractBossEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setPersistenceRequired();

        this.transitionController = new AnimationController<>(this, "phase_transition", 0, this::transitionPredicate);
        this.meleeController = new AnimationController<>(this, "melee_attack", 0, this::meleePredicate);
        this.spawnController = new AnimationController<>(this, "spawn", 0, this::spawnPredicate);
        this.spawnController.setAnimationSpeed(1.0D);
        this.locomotionController = new AnimationController<>(this, "locomotion", 5, this::locomotionPredicate);
        this.hurtController = new AnimationController<>(this, "hurt", 2, this::hurtPredicate);
        this.deathController = new AnimationController<>(this, "death", 0, this::deathPredicate);

        if (!level.isClientSide) {
            this.spawnTimer = SPAWN_TICKS;
            this.entityData.set(DATA_SPAWN_TIMER, SPAWN_TICKS);
            this.setInvulnerable(true);
            this.setNoAi(true);
            this.setNoGravity(true);
        } else {
            this.spawnTimer = 0;
            this.entityData.set(DATA_SPAWN_TIMER, 0);
        }

        this.bossEvent.setName(this.buildBossName());
        this.bossEvent.setDarkenScreen(false);
    }

    public int getSpawnTimer() {
        return this.entityData.get(DATA_SPAWN_TIMER);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(this.locomotionController, this.hurtController, this.meleeController,
                this.transitionController, this.spawnController, this.deathController);
    }

    private PlayState locomotionPredicate(AnimationState<AbstractBossEntity> state) {
        if (this.isDeadOrDying() || this.getSpawnTimer() > 0) {
            return PlayState.STOP;
        }
        if (this.getPhase() == Phases.Transitioning.ordinal()) {
            return PlayState.STOP;
        }
        if (this.isInWater()) {
            return state.setAndContinue(this.isUnderWater() ? SWIM_ANIMATION : SWIM_STAND_ANIMATION);
        }
        double horizontalSpeed = this.getDeltaMovement().horizontalDistanceSqr();
        if (horizontalSpeed > 0.02D) {
            return state.setAndContinue(RUN_ANIMATION);
        }
        if (horizontalSpeed > 0.002D) {
            return state.setAndContinue(WALK_ANIMATION);
        }
        return state.setAndContinue(IDLE_ANIMATION);
    }

    private PlayState hurtPredicate(AnimationState<AbstractBossEntity> state) {
        if (this.isDeadOrDying() || this.getSpawnTimer() > 0
                || this.getPhase() == Phases.Transitioning.ordinal()) {
            return PlayState.STOP;
        }
        if (this.hurtTime > 0) {
            return state.setAndContinue(ATTACKED_ANIMATION);
        }
        return PlayState.STOP;
    }

    private PlayState deathPredicate(AnimationState<AbstractBossEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DEATH_ANIMATION);
        }
        return PlayState.STOP;
    }

    private PlayState spawnPredicate(AnimationState<AbstractBossEntity> state) {
        if (this.getSpawnTimer() > 0) {
            return state.setAndContinue(SPAWN_ANIMATION);
        }
        return PlayState.STOP;
    }

    private PlayState transitionPredicate(AnimationState<AbstractBossEntity> state) {
        if (this.getSpawnTimer() > 0) {
            return PlayState.STOP;
        }
        if (this.getPhase() == Phases.Transitioning.ordinal()) {
            state.setAnimation(TRANSITION_ANIMATION);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    private PlayState meleePredicate(AnimationState<AbstractBossEntity> state) {
        if (this.getSpawnTimer() > 0) {
            return PlayState.STOP;
        }
        if (this.stopClientAnimation) {
            this.stopClientAnimation = false;
            this.animationToPlay = null;
            this.clientAttackAnimationLength = 0;
            return PlayState.STOP;
        }
        if (this.animationToPlay != null) {
            this.meleeController.forceAnimationReset();
            state.setAnimation(this.animationToPlay);
            this.animationToPlay = null;
            return PlayState.CONTINUE;
        }
        // 动作播完就停下，交还控制权给待机/走路动画，否则会一直僵在最后一帧
        if (this.clientAttackAnimationLength > 0) {
            if (state.getAnimationTick() >= this.clientAttackAnimationLength) {
                this.clientAttackAnimationLength = 0;
                return PlayState.STOP;
            }
            return PlayState.CONTINUE;
        }
        return this.transitionController.getAnimationState() == AnimationController.State.STOPPED ? PlayState.CONTINUE
                : PlayState.STOP;
    }

    @Override
    public void playAnimation(String animationName) {
        if (!this.level().isClientSide || this.getPhase() == Phases.Transitioning.ordinal()) {
            return;
        }

        int length = 0;
        int separator = animationName.lastIndexOf('|');
        if (separator > 0) {
            try {
                length = Integer.parseInt(animationName.substring(separator + 1));
            } catch (NumberFormatException ignored) {
                length = 0;
            }
            animationName = animationName.substring(0, separator);
        }
        if (animationName.isEmpty()) {
            // 空动画名 = 服务端要求中断当前动作（例如目标跑出攻击范围）
            this.animationToPlay = null;
            this.clientAttackAnimationLength = 0;
            this.stopClientAnimation = true;
            return;
        }

        this.animationToPlay = RawAnimation.begin().thenPlay(animationName);
        this.clientAttackAnimationLength = length;
    }

    public int getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public void setPhase(int phase) {
        int previous = this.getPhase();
        if (previous == phase) {
            return;
        }
        this.handleBossSpellBoundary();
        this.entityData.set(DATA_PHASE, phase);
        this.onPhaseChanged(previous, phase);
    }

    protected void handleBossSpellBoundary() {
        if (this.isCasting()) {
            this.cancelCast();
        }
    }

    protected void onPhaseChanged(int oldPhase, int newPhase) {
    }

    protected void clearGoals() {
        this.goalSelector.getAvailableGoals().forEach(WrappedGoal::stop);
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.getAvailableGoals().forEach(WrappedGoal::stop);
        this.targetSelector.removeAllGoals(goal -> true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
        builder.define(DATA_SPAWN_TIMER, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("phase", this.getPhase());
        tag.putInt("phase_transition_timer", this.phaseTransitionTimer);
        tag.putInt("spawn_timer", this.spawnTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE, tag.getInt("phase"));
        this.phaseTransitionTimer = tag.getInt("phase_transition_timer");
        if (tag.contains("spawn_timer")) {
            this.spawnTimer = tag.getInt("spawn_timer");
        }
        this.entityData.set(DATA_SPAWN_TIMER, this.spawnTimer);
        this.setInvulnerable(this.spawnTimer > 0);
        this.setNoAi(this.spawnTimer > 0);
        this.setNoGravity(this.spawnTimer > 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (this.spawnTimer > 0) {
            this.tickSpawning();
        }
        if (this.showsBossBar()) {
            this.bossEvent.setProgress(this.getBossBarProgress());
        }
    }

    private void tickSpawning() {
        if (this.spawnTimer == SPAWN_TICKS) {
            this.playSound(SoundEvents.WITHER_AMBIENT, 1.0F, 0.5F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.2D, this.getZ(),
                        30, 0.5D, 0.3D, 0.5D, 0.02D);
            }
        }
        this.spawnTimer--;
        this.entityData.set(DATA_SPAWN_TIMER, this.spawnTimer);
        if (this.spawnTimer <= 0) {
            this.finishSpawning();
        }
    }

    protected void finishSpawning() {
        this.spawnTimer = 0;
        this.entityData.set(DATA_SPAWN_TIMER, 0);
        this.setInvulnerable(false);
        this.setNoAi(false);
        this.setNoGravity(false);
    }

    protected void skipSpawnAnimation() {
        this.finishSpawning();
    }

    protected boolean showsBossBar() {
        return true;
    }

    protected float getBossBarProgress() {
        return this.getHealth() / this.getMaxHealth();
    }

    private static boolean ignoresBossProtection(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.getPhase() == Phases.Transitioning.ordinal() && !ignoresBossProtection(source)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) && !ignoresBossProtection(source)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !ignoresBossProtection(source) && super.isInvulnerableTo(source);
    }

    @Override
    protected void onBelowWorld() {
        if (!this.level().isClientSide) {
            this.kill();
        }
    }

    @Override
    protected float getDamageAfterArmorAbsorb(DamageSource source, float damage) {
        float result = super.getDamageAfterArmorAbsorb(source, damage);
        if (CWFConfig.ENABLE_DAMAGE_CAP.get() && !ignoresBossProtection(source)) {
            result = Math.min(result, CWFConfig.MAX_DAMAGE_PER_HIT.get().floatValue());
        }
        return result;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        Holder<MobEffect> type = effect.getEffect();
        if (type == MobEffects.POISON || type == MobEffects.WITHER
                || type == MobEffects.MOVEMENT_SLOWDOWN || type == MobEffects.WEAKNESS) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.showsBossBar()) {
            this.bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.buildBossName());
    }

    @Override
    public void die(DamageSource source) {
        this.bossEvent.setProgress(0.0F);
        this.bossEvent.removeAllPlayers();
        if (this.isCasting()) {
            this.cancelCast();
        }
        super.die(source);
    }

    @Override
    public boolean shouldAlwaysAnimateHead() {
        return this.getPhase() != Phases.Transitioning.ordinal();
    }

    @Override
    public boolean shouldAlwaysAnimateLegs() {
        return this.getPhase() == Phases.FirstPhase.ordinal();
    }

    protected void spawnRingOfLightning(ServerLevel level, int count, double radius) {
        Vec3 center = this.position();
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D / count) * i;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) {
                continue;
            }
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
            bolt.moveTo(x, y, z);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    protected Component buildBossName() {
        Component base = this.getCustomName() != null ? this.getCustomName() : this.getDisplayName();
        return Component.translatable("%s %s", base,
                Component.translatable("boss.corrupted_wine_fox_boss.phase." + this.getPhaseKey()));
    }

    protected String getPhaseKey() {
        return switch (this.getPhase()) {
            case 1 -> "second";
            case 2 -> "transitioning";
            case 3 -> "final";
            default -> "first";
        };
    }

    public enum Phases {
        FirstPhase,
        SecondPhase,
        Transitioning,
        ThirdPhase
    }
}
