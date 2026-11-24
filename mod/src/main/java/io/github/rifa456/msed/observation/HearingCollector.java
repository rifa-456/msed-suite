package io.github.rifa456.msed.observation;

import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.proto.v1.HearingState;
import io.github.rifa456.msed.proto.v1.ObservationKey;
import io.github.rifa456.msed.proto.v1.SoundEvent;
import io.github.rifa456.msed.proto.v1.Vec3;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The hearing modality: the sounds the game asked to play since the previous tick, each recorded
 * exactly once, with its volume and position.
 *
 * <p>Sounds are captured where the game requests them ({@link #onDispatch}), before the audio
 * engine. The data therefore does not depend on audio hardware or on the engine being loaded.
 * The largest number of sounds in one tick of the session goes into the episode metadata.
 */
public final class HearingCollector extends TrackingCollector<HearingState> {
    private static final ConcurrentLinkedQueue<SoundEvent> DISPATCHED = new ConcurrentLinkedQueue<>();
    private static volatile boolean collecting;

    private final StatTracker soundTracker;

    /** Creates a collector that measures the number of sounds in each tick. */
    public HearingCollector() {
        this.soundTracker = registerTracker(ObservationKey.OBSERVATION_KEY_HEARING_MAX_SOUNDS);
    }

    /**
     * Queues a sound the game asked to play, for the next tick. Does nothing outside a recording
     * or for a sound that cannot play. The {@code SoundManager} hooks call this; any thread may.
     *
     * @param soundManager the sound manager that picks the sound's variant
     * @param sound the sound the game asked to play
     */
    public static void onDispatch(SoundManager soundManager, SoundInstance sound) {
        if (!collecting || !sound.canPlaySound()) return;
        // getVolume() needs the variant that resolving picks. The engine resolves the sound again.
        WeighedSoundEvents resolved = sound.resolve(soundManager);
        if (resolved == null || SoundManager.EMPTY_SOUND.equals(sound.getSound())) return;
        net.minecraft.sounds.SoundEvent registered = BuiltInRegistries.SOUND_EVENT.getValue(sound.getIdentifier());
        int soundId = registered != null ? Vocabulary.getInstance().getId(registered) : Vocabulary.NONE;
        DISPATCHED.add(SoundEvent.newBuilder()
                .setSoundId(soundId)
                .setIntensity(sound.getVolume())
                .setPosition(
                        Vec3.newBuilder().setX(sound.getX()).setY(sound.getY()).setZ(sound.getZ()))
                .setRelative(sound.isRelative())
                .build());
    }

    @Override
    public String getProfilerName() {
        return "msed:hearing";
    }

    @Override
    public void startSession(Minecraft client) {
        super.startSession(client);
        DISPATCHED.clear();
        collecting = true;
    }

    @Override
    public void endSession(Minecraft client) {
        collecting = false;
        super.endSession(client);
        DISPATCHED.clear();
    }

    @Override
    public HearingState collect(Minecraft client, ProfilerFiller profiler) {
        HearingState.Builder state = HearingState.newBuilder();
        SoundEvent sound;
        // Drain the queue even when sounds are not recorded, so that it does not grow.
        while ((sound = DISPATCHED.poll()) != null) {
            state.addSounds(sound);
        }
        if (!ConfigFile.getConfig().getRecording().getRecordHearing().getRecordSounds()) {
            return HearingState.getDefaultInstance();
        }
        soundTracker.observe(state.getSoundsCount());
        return state.build();
    }
}
