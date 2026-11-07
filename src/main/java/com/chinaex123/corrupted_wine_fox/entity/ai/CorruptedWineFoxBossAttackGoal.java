package com.chinaex123.corrupted_wine_fox.entity.ai;

import com.chinaex123.corrupted_wine_fox.config.CWFConfig;
import com.chinaex123.corrupted_wine_fox.entity.CorruptedWineFoxBossEntity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.entity.mobs.goals.WarlockAttackGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.melee.AttackAnimationData;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CorruptedWineFoxBossAttackGoal extends WarlockAttackGoal {

    /** 近战判定距离。比实体自身的交互距离大一些，手感上"够得着" */
    private static final float MELEE_RANGE = 4.0F;
    /** 贴脸距离：这个距离内不再让施法概率压过近战 */
    private static final float POINT_BLANK_RANGE = 2.5F;
    /** 命中帧发生时的前冲力度 */
    private static final float LUNGE_POWER = 0.45F;
    /** 命中判定的距离宽容倍率 */
    private static final float HIT_RANGE_MULTIPLIER = 1.35F;
    /** 玩家跑出这个倍率的距离就中途取消动作 */
    private static final float CANCEL_DISTANCE_MULTIPLIER = 1.6F;
    /** 单段命中后接下一段的概率 */
    private static final float COMBO_CHANCE = 0.45F;
    /** 两段近战之间的冷却（tick） */
    private static final int MELEE_COOLDOWN_MIN = 10;
    private static final int MELEE_COOLDOWN_MAX = 22;
    /** 默认的施法冷却（tick），各阶段可以覆盖 */
    private static final int DEFAULT_SPELL_COOLDOWN_MIN = 30;
    private static final int DEFAULT_SPELL_COOLDOWN_MAX = 60;
    /** 施放强力治疗的概率 */
    private static final float GREATER_HEAL_CHANCE = 0.05F;
    /** 被连续攻击后防御法术的冷却 */
    private static final int DEFENSE_COOLDOWN = 160;
    /** 想放法术时的最大距离 */
    private static final double CAST_RANGE = 12.0D;

    private final CorruptedWineFoxBossEntity boss;
    private final List<AttackAnimationData> moveset;
    /** 攻击法术池 */
    private final List<AbstractSpell> attackSpells = new ArrayList<>();
    /** 防御法术池 */
    private final List<AbstractSpell> defenseSpells = new ArrayList<>();
    /** 治疗法术池 */
    private final List<AbstractSpell> healSpells = new ArrayList<>();
    /** 本阶段的施法冷却区间 */
    private int spellCooldownMin = DEFAULT_SPELL_COOLDOWN_MIN;
    private int spellCooldownMax = DEFAULT_SPELL_COOLDOWN_MAX;
    private int healCooldown;
    private int defenseCooldown;

    private AttackAnimationData currentAttack;
    private AttackAnimationData nextAttack;
    private AttackAnimationData queueCombo;

    /** 0 表示"这一段刚播完"，-1 表示空闲，>0 表示正在出手 */
    private int meleeAnimTimer = -1;
    private boolean playedSwingSound;
    /** 偏好近战（而不是放法术）的概率，0 = 几乎只放法术，1 = 几乎只砍人 */
    private float meleeChance = 0.5F;
    /** 距离下次出手还差多少 tick */
    private int attackCooldown = 10;

    public CorruptedWineFoxBossAttackGoal(CorruptedWineFoxBossEntity boss, List<AttackAnimationData> moveset, double speed,
                                          int intervalMin, int intervalMax) {
        super(boss, speed, intervalMin, intervalMax);
        this.boss = boss;
        this.moveset = moveset;
    }

    /**
     * 设置本阶段的攻击法术池。
     */
    public void setAttackSpells(List<AbstractSpell> spells) {
        this.attackSpells.clear();
        this.attackSpells.addAll(spells);
    }

    /**
     * 设置防御法术池（被连续攻击时使用）。
     */
    public void setDefenseSpells(List<AbstractSpell> spells) {
        this.defenseSpells.clear();
        this.defenseSpells.addAll(spells);
    }

    /**
     * 设置治疗法术池（低血量时使用）。
     */
    public void setHealSpells(List<AbstractSpell> spells) {
        this.healSpells.clear();
        this.healSpells.addAll(spells);
    }

    /**
     * 设置施法冷却区间，三阶段会更频繁。
     */
    public void setSpellCooldownRange(int min, int max) {
        this.spellCooldownMin = Math.max(1, min);
        this.spellCooldownMax = Math.max(this.spellCooldownMin, max);
    }

    @Override
    public void start() {
        super.start();
        this.nextAttack = this.pickAttack();
        this.attackCooldown = Mth.randomBetweenInclusive(this.boss.getRandom(), 4, 12);
    }

    public void setMeleeChance(float chance) {
        this.meleeChance = Mth.clamp(chance, 0.0F, 1.0F);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isActing() {
        return super.isActing() || this.meleeAnimTimer > 0;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse() || this.meleeAnimTimer > 0;
    }

    @Override
    public float meleeRange() {
        return MELEE_RANGE;
    }

    @Override
    protected void handleAttackLogic(double distanceSqr) {
        LivingEntity target = this.target;
        if (target == null) {
            return;
        }

        if (this.healCooldown > 0) {
            this.healCooldown--;
        }
        if (this.defenseCooldown > 0) {
            this.defenseCooldown--;
        }
        this.boss.getLookControl().setLookAt(target);

        // 正在挥砍：推进动画
        if (this.meleeAnimTimer > 0 && this.currentAttack != null) {
            this.tickAttackAnimation(distanceSqr);
            return;
        }

        // 连招：上一刀命中时排进来的下一段
        if (this.queueCombo != null && !target.isDeadOrDying()) {
            this.nextAttack = this.queueCombo;
            this.queueCombo = null;
            this.doMeleeAction();
            return;
        }

        // 上一段刚播完：收尾并进入冷却
        if (this.meleeAnimTimer == 0) {
            this.nextAttack = this.pickAttack();
            this.meleeAnimTimer = -1;
            this.attackCooldown = Mth.randomBetweenInclusive(this.boss.getRandom(), MELEE_COOLDOWN_MIN,
                    MELEE_COOLDOWN_MAX);
            return;
        }

        // 吟唱中：等它唱完再说
        if (this.boss.isCasting()) {
            return;
        }

        if (this.attackCooldown > 0) {
            if (distanceSqr <= (double) (POINT_BLANK_RANGE * POINT_BLANK_RANGE) && this.attackCooldown > 5) {
                this.attackCooldown = 5;
            }
            --this.attackCooldown;
            return;
        }

        float range = this.meleeRange();
        boolean inMeleeRange = distanceSqr <= (double) (range * range)
                && Utils.hasLineOfSight(this.boss.level(), this.boss, target, true);
        boolean pointBlank = distanceSqr <= (double) (POINT_BLANK_RANGE * POINT_BLANK_RANGE);
        boolean preferMelee = pointBlank || this.boss.getRandom().nextFloat() < this.meleeChance;

        if (inMeleeRange && preferMelee) {
            this.doMeleeAction();
            return;
        }
        if (this.tryCastSpell(target, distanceSqr)) {
            return;
        }
        if (inMeleeRange) {
            // 法术都在冷却里，那就直接砍
            this.doMeleeAction();
            return;
        }
        // 既砍不到也放不出：下一 tick 立刻再试，交给移动逻辑去接近
        this.attackCooldown = 1;
    }

    private boolean tryCastSpell(LivingEntity target, double distanceSqr) {
        if (distanceSqr > CAST_RANGE * CAST_RANGE) {
            return false;
        }
        if (!Utils.hasLineOfSight(this.boss.level(), this.boss, target, true)) {
            return false;
        }

        // 低血量优先治疗；被连续攻击时优先开防御法术；否则攻击
        AbstractSpell spell = null;
        if (this.healCooldown <= 0 && this.boss.wantsHealSpell()) {
            spell = this.pickHealSpell();
            if (spell != null) {
                this.healCooldown = CWFConfig.HEAL_COOLDOWN.get();
            }
        }
        if (spell == null && this.defenseCooldown <= 0 && this.boss.wantsDefensiveSpell()) {
            spell = pickFrom(this.defenseSpells);
            if (spell != null) {
                this.defenseCooldown = DEFENSE_COOLDOWN;
            }
        }
        if (spell == null) {
            spell = pickFrom(this.attackSpells);
        }
        if (spell == null) {
            return false;
        }

        int level = Mth.randomBetweenInclusive(this.boss.getRandom(), 1, 3);
        this.boss.initiateCastSpell(spell, level);
        if (this.boss.isCasting()) {
            this.attackCooldown = Mth.randomBetweenInclusive(this.boss.getRandom(), this.spellCooldownMin,
                    this.spellCooldownMax);
        } else {
            // 蓝不够或还在这个法术的冷却里：很快换个选择，别傻站着
            this.attackCooldown = 4;
        }
        return true;
    }

    @Nullable
    private AbstractSpell pickHealSpell() {
        AbstractSpell greaterHeal = SpellRegistry.GREATER_HEAL_SPELL.get();
        AbstractSpell heal = SpellRegistry.HEAL_SPELL.get();
        if (this.boss.getRandom().nextFloat() < GREATER_HEAL_CHANCE
                && this.healSpells.contains(greaterHeal)) {
            return greaterHeal;
        }
        if (this.healSpells.contains(heal)) {
            return heal;
        }
        return pickFrom(this.healSpells);
    }

    @Nullable
    private AbstractSpell pickFrom(List<AbstractSpell> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(this.boss.getRandom().nextInt(pool.size()));
    }

    @Override
    protected void doMovement(double distanceSqr) {
        LivingEntity target = this.target;
        // 出手期间不许往前挪
        if (this.meleeAnimTimer > 0 || this.boss.isCasting()) {
            this.boss.getNavigation().stop();
            this.stopDrift();
            if (target != null) {
                this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            return;
        }
        if (target == null) {
            this.boss.getNavigation().stop();
            return;
        }

        float range = this.meleeRange() * 0.85F;
        if (distanceSqr > (double) (range * range)) {
            this.boss.getNavigation().moveTo(target, 1.0D);
            this.boss.getLookControl().setLookAt(target, 30.0F, 30.0F);
        } else {
            // 已经在攻击范围内：站住别绕圈
            this.boss.getNavigation().stop();
            this.stopDrift();
        }
    }

    private void stopDrift() {
        this.boss.getMoveControl().setWantedPosition(this.boss.getX(), this.boss.getY(), this.boss.getZ(), 0.0D);
    }

    @Override
    protected void doMeleeAction() {
        if (this.meleeAnimTimer > 0) {
            return;
        }
        if (this.moveset.isEmpty()) {
            super.doMeleeAction();
            return;
        }

        if (this.nextAttack == null) {
            this.nextAttack = this.pickAttack();
        }
        if (this.nextAttack == null) {
            return;
        }

        this.currentAttack = this.nextAttack;
        this.nextAttack = this.pickAttack();
        this.meleeAnimTimer = this.currentAttack.lengthInTicks;
        this.playedSwingSound = false;
        this.attackCooldown = Mth.randomBetweenInclusive(this.boss.getRandom(), MELEE_COOLDOWN_MIN, MELEE_COOLDOWN_MAX);

        this.boss.swing(InteractionHand.MAIN_HAND);
        this.boss.getNavigation().stop();
        this.stopDrift();
        this.faceTarget();

        // 服务端发包给附近客户端播放动作；带上时长，客户端播完自己停下
        this.boss.serverTriggerAnimation(this.currentAttack.animationId + "|" + this.currentAttack.lengthInTicks);
    }

    private void tickAttackAnimation(double distanceSqr) {
        AttackAnimationData data = this.currentAttack;
        LivingEntity target = this.target;
        if (data == null || target == null) {
            this.meleeAnimTimer = 0;
            this.currentAttack = null;
            return;
        }

        int elapsed = data.lengthInTicks - this.meleeAnimTimer;

        this.boss.getNavigation().stop();
        this.faceTarget();

        if (!this.playedSwingSound && data.isHitFrame(elapsed + 4)) {
            this.playedSwingSound = true;
            this.playSwingSound();
        }
        if (target.isAlive() && data.isHitFrame(elapsed)) {
            this.performHit(target);
        }

        // 玩家跑掉了就别把整套动作挥完
        if (this.canCancelAttack(data, elapsed)) {
            this.stopMeleeAction(distanceSqr);
            return;
        }

        if (--this.meleeAnimTimer <= 0) {
            this.meleeAnimTimer = 0;
            this.currentAttack = null;
            if (this.queueCombo == null && this.boss.getRandom().nextFloat() < COMBO_CHANCE) {
                this.queueCombo = this.pickAttack();
            }
        }
    }

    private boolean canCancelAttack(AttackAnimationData data, int elapsed) {
        if (!data.canCancel || this.target == null) {
            return false;
        }
        if (!data.isSingleHit() && elapsed <= this.firstHitFrame(data)) {
            return false;
        }
        Vec3 delta = this.boss.position().subtract(this.target.position());
        double weighted = delta.x * delta.x + delta.y * delta.y * 0.25D + delta.z * delta.z;
        float limit = this.meleeRange() * CANCEL_DISTANCE_MULTIPLIER;
        return weighted > (double) limit * limit;
    }

    private int firstHitFrame(AttackAnimationData data) {
        int first = Integer.MAX_VALUE;
        for (var key : data.attacks.keySet()) {
            first = Math.min(first, key);
        }
        return first == Integer.MAX_VALUE ? 0 : first;
    }

    private void performHit(LivingEntity target) {
        Vec3 delta = target.position().subtract(this.boss.position());
        if (delta.horizontalDistanceSqr() > 1.0E-4D) {
            Vec3 direction = delta.normalize();
            this.boss.push(direction.x * LUNGE_POWER, 0.08D, direction.z * LUNGE_POWER);
        }

        float range = this.meleeRange() * HIT_RANGE_MULTIPLIER;
        if (this.boss.distanceToSqr(target) > (double) range * range) {
            return;
        }
        if (!Utils.hasLineOfSight(this.boss.level(), this.boss, target, true)) {
            return;
        }

        if (this.boss.doHurtTarget(target)) {
            target.invulnerableTime = 0;
            this.boss.onMeleeHit(target);
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
            if (dataIsSingleHit(this.currentAttack)
                    && this.boss.getRandom().nextFloat() < COMBO_CHANCE * (target.isBlocking() ? 2.0F : 1.0F)) {
                this.queueCombo = this.pickAttack();
            }
        }
    }

    private static boolean dataIsSingleHit(@Nullable AttackAnimationData data) {
        return data != null && data.isSingleHit();
    }

    private void stopMeleeAction(double distanceSqr) {
        this.meleeAnimTimer = 0;
        this.currentAttack = null;
        this.queueCombo = null;
        this.boss.serverTriggerAnimation("");
    }

    @Nullable
    private AttackAnimationData pickAttack() {
        if (this.moveset.isEmpty()) {
            return null;
        }
        return this.moveset.get(this.boss.getRandom().nextInt(this.moveset.size()));
    }

    private void faceTarget() {
        LivingEntity target = this.target;
        if (target == null) {
            return;
        }
        double dx = target.getX() - this.boss.getX();
        double dz = target.getZ() - this.boss.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.boss.setYRot(yaw);
        this.boss.setYHeadRot(yaw);
        this.boss.setYBodyRot(yaw);
    }

    private void playSwingSound() {
        this.playSound(SoundRegistry.GENERIC_BLADE_SWING.get(), 1.0F,
                Mth.randomBetweenInclusive(this.boss.getRandom(), 12, 18) * 0.1F);
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        this.boss.playSound(sound, volume, pitch);
    }

    public void interruptAttack() {
        if (this.meleeAnimTimer > 0) {
            this.stopMeleeAction(this.boss.distanceToSqr(this.target == null ? this.boss : this.target));
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.currentAttack = null;
        this.nextAttack = null;
        this.queueCombo = null;
        this.meleeAnimTimer = -1;
        this.attackCooldown = 10;
        this.healCooldown = 0;
        this.defenseCooldown = 0;
    }

    public List<String> debugMoveset() {
        List<String> names = new ArrayList<>();
        for (AttackAnimationData data : this.moveset) {
            names.add(data.animationId);
        }
        return names;
    }
}
