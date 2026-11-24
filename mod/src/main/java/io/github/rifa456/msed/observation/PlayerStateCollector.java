package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.mixin.observation.FoodDataAccessor;
import io.github.rifa456.msed.proto.v1.ActiveEffect;
import io.github.rifa456.msed.proto.v1.AirSupplyState;
import io.github.rifa456.msed.proto.v1.CombatState;
import io.github.rifa456.msed.proto.v1.EnvironmentState;
import io.github.rifa456.msed.proto.v1.EquippedItems;
import io.github.rifa456.msed.proto.v1.HungerState;
import io.github.rifa456.msed.proto.v1.ObservationKey;
import io.github.rifa456.msed.proto.v1.PlayerState;
import io.github.rifa456.msed.proto.v1.RecordingSettings;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The player-state modality: position, motion, rotation, health, hunger, air, equipped items,
 * active effects, combat, light and time of day, mining, and nearby entities, each part as the
 * configuration selects. A tick with no player records an empty state.
 *
 * <p>The largest numbers of active effects and nearby entities in one tick of the session go into
 * the episode metadata.
 */
public final class PlayerStateCollector extends TrackingCollector<PlayerState> {

    private final StatTracker effectsTracker;
    private final StatTracker entitiesTracker;

    /** Creates a collector that measures the effect and nearby-entity counts of each tick. */
    public PlayerStateCollector() {
        this.effectsTracker = registerTracker(ObservationKey.OBSERVATION_KEY_PLAYER_MAX_EFFECTS);
        this.entitiesTracker = registerTracker(ObservationKey.OBSERVATION_KEY_PLAYER_MAX_NEARBY_ENTITIES);
    }

    @Override
    public String getProfilerName() {
        return "msed:player_state";
    }

    @Override
    public PlayerState collect(Minecraft client, ProfilerFiller profiler) {
        LocalPlayer player = client.player;
        if (player == null) {
            return PlayerState.getDefaultInstance();
        }
        RecordingSettings.Player config = ConfigFile.getConfig().getRecording().getRecordPlayer();
        Vocabulary vocab = Vocabulary.getInstance();
        PlayerState.Builder builder = PlayerState.newBuilder();
        if (config.getRecordCoreStats()) {
            builder.setPosition(Vectors.of(player.position()))
                    .setVelocity(Vectors.of(player.getDeltaMovement()))
                    .setOnGround(player.onGround())
                    .setSneaking(player.isShiftKeyDown())
                    .setSprinting(player.isSprinting())
                    .setFallDistance((float) player.fallDistance)
                    .setHealth(player.getHealth())
                    .setArmor(player.getArmorValue())
                    .setXpLevel(player.experienceLevel)
                    .setXpProgress(player.experienceProgress);
        }
        if (config.getRecordRotation()) {
            builder.setPitch(player.getXRot()).setYaw(player.getYRot());
        }
        if (config.getRecordHunger()) {
            var hungerManager = player.getFoodData();
            FoodDataAccessor accessor = (FoodDataAccessor) hungerManager;
            builder.setHunger(HungerState.newBuilder()
                    .setFoodLevel(hungerManager.getFoodLevel())
                    .setSaturationLevel(hungerManager.getSaturationLevel())
                    .setExhaustion(accessor.getExhaustionLevel())
                    .build());
        }
        if (config.getRecordAirSupply()) {
            builder.setAirSupply(AirSupplyState.newBuilder()
                    .setCurrent(player.getAirSupply())
                    .setMax(player.getMaxAirSupply())
                    .build());
        }
        if (config.getRecordEquipped()) {
            builder.setEquippedItems(EquippedItems.newBuilder()
                    .setMainHandItemId(vocab.getId(player.getMainHandItem().getItem()))
                    .setOffHandItemId(vocab.getId(player.getOffhandItem().getItem()))
                    .build());
        }
        if (config.getRecordActiveEffects()) {
            collectEffects(player, builder, vocab);
        }
        if (config.getRecordCombat()) {
            builder.setCombat(CombatState.newBuilder()
                    .setAttackCooldown(player.getAttackStrengthScale(0.5f))
                    // The attribute already includes the held item's modifiers.
                    .setAttackDamage((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE))
                    .build());
        }
        if (config.getRecordEnvironment() && client.level != null) {
            builder.setEnvironment(EnvironmentState.newBuilder()
                    .setLightLevel(client.level.getMaxLocalRawBrightness(player.blockPosition()))
                    .setTimeOfDay(client.level.getDayTime())
                    .build());
        }
        if (config.getRecordMining()) {
            builder.setMining(Mining.of(client, player));
        }
        if (config.getRecordNearbyEntities()) {
            collectEntities(player, builder, vocab);
        }
        return builder.build();
    }

    private void collectEffects(LocalPlayer player, PlayerState.Builder builder, Vocabulary vocab) {
        Collection<MobEffectInstance> effects = player.getActiveEffects();
        effectsTracker.observe(effects.size());
        for (MobEffectInstance effect : effects) {
            builder.addActiveEffects(ActiveEffect.newBuilder()
                    .setEffectId(vocab.getId(effect.getEffect().value()))
                    .setAmplifier(effect.getAmplifier())
                    .setDuration(effect.getDuration())
                    .build());
        }
    }

    private void collectEntities(LocalPlayer player, PlayerState.Builder builder, Vocabulary vocab) {
        List<Entity> entities = NearbyEntities.around(player);
        entitiesTracker.observe(entities.size());
        Vec3 origin = player.position();
        for (Entity entity : entities) {
            builder.addNearbyEntities(NearbyEntities.describe(entity, origin, vocab));
        }
    }
}
