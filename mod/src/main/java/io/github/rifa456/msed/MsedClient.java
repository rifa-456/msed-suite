package io.github.rifa456.msed;

import io.github.rifa456.msed.config.ConfigFile;
import io.github.rifa456.msed.debug.DebugKeys;
import io.github.rifa456.msed.debug.DebugOverlay;
import io.github.rifa456.msed.events.ClientDamageSource;
import io.github.rifa456.msed.events.ItemPickedUpS2CPayload;
import io.github.rifa456.msed.events.PlayerDamagedS2CPayload;
import io.github.rifa456.msed.proto.v1.EntityDamageEvent;
import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.ItemPickedUpEvent;
import io.github.rifa456.msed.recording.Recorder;
import io.github.rifa456.msed.video.VideoBackend;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * The mod's client entry point. It loads the config, probes the video backend and connects the
 * recorder to the game's ticks, HUD, network packets and disconnects.
 */
public final class MsedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigFile.load();
        VideoBackend.init();
        Recorder recorder = Recorder.getInstance();
        recorder.registerKeys();
        DebugKeys debugKeys = new DebugKeys();
        DebugOverlay debugOverlay = new DebugOverlay(debugKeys);
        registerPacketReceivers(recorder);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> recorder.shutdown());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            debugKeys.onClientTick(client);
            debugOverlay.onClientTick(client, recorder.isRecording());
            recorder.onClientTick(client);
        });
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(Msed.MOD_ID, "recording_status"),
                (graphics, deltaTracker) -> recorder.onHudRender(graphics));
    }

    /**
     * Turns the pickup and damage packets from the integrated server into recorded events. Packets
     * arrive on the network thread; each event is logged on the client thread.
     *
     * <p>A damage packet updates the last damage source even while not recording, so a death
     * event can name its cause.
     */
    private static void registerPacketReceivers(Recorder recorder) {
        ClientPlayNetworking.registerGlobalReceiver(ItemPickedUpS2CPayload.ID, (payload, context) -> {
            if (recorder.isRecording()) {
                context.client().execute(() -> {
                    int itemId = Vocabulary.getInstance().getId(BuiltInRegistries.ITEM.getValue(payload.itemId()));
                    recorder.logEvent(Event.newBuilder()
                            .setItemPickedUp(ItemPickedUpEvent.newBuilder()
                                    .setItemId(itemId)
                                    .setCount(payload.count())));
                });
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(PlayerDamagedS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                recorder.setLastDamageSource(new ClientDamageSource(payload.damageType(), payload.sourceEntityId()));
                if (recorder.isRecording()) {
                    Vocabulary vocabulary = Vocabulary.getInstance();
                    recorder.logEvent(Event.newBuilder()
                            .setDamage(EntityDamageEvent.newBuilder()
                                    .setAmount(payload.amount())
                                    .setSourceTypeId(vocabulary.getId(payload.damageType()))
                                    .setSourceEntityId(vocabulary.entityTypeId(payload.sourceEntityId()))));
                }
            });
        });
    }
}
