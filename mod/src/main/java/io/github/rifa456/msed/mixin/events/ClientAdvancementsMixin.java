package io.github.rifa456.msed.mixin.events;

import io.github.rifa456.msed.proto.v1.AdvancementEvent;
import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.recording.Recorder;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records an advancement event when the player completes an advancement the game displays. */
@Mixin(ClientAdvancements.class)
public abstract class ClientAdvancementsMixin {

    @Shadow
    @Final
    private AdvancementTree tree;

    @Shadow
    @Final
    private Map<AdvancementHolder, AdvancementProgress> progress;

    @Unique private final Map<Identifier, Boolean> progressBeforeUpdate = new HashMap<>();

    /** Notes which advancements in the packet were already done, before the update applies it. */
    @Inject(method = "update", at = @At("HEAD"))
    private void onAdvancementsHead(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        if (!Recorder.getInstance().isRecording()) return;
        progressBeforeUpdate.clear();
        for (Map.Entry<Identifier, AdvancementProgress> entry :
                packet.getProgress().entrySet()) {
            Identifier id = entry.getKey();
            AdvancementNode placedAdvancement = this.tree.get(id);
            if (placedAdvancement != null) {
                AdvancementHolder advancementEntry = placedAdvancement.holder();
                AdvancementProgress progress = this.progress.get(advancementEntry);
                progressBeforeUpdate.put(id, progress != null && progress.isDone());
            }
        }
    }

    /** Logs the advancements that the update completed. */
    @Inject(method = "update", at = @At("TAIL"))
    private void onAdvancementsTail(ClientboundUpdateAdvancementsPacket packet, CallbackInfo ci) {
        if (!Recorder.getInstance().isRecording()) return;

        for (Identifier id : progressBeforeUpdate.keySet()) {
            AdvancementNode placedAdvancement = this.tree.get(id);
            if (placedAdvancement == null) continue;
            AdvancementHolder advancementEntry = placedAdvancement.holder();
            AdvancementProgress progress = this.progress.get(advancementEntry);
            boolean wasDone = progressBeforeUpdate.getOrDefault(id, false);
            if (progress != null && progress.isDone() && !wasDone) {
                Optional<DisplayInfo> display = advancementEntry.value().display();
                if (display.isPresent()) {
                    int advancementId = Vocabulary.getInstance().getId(id.toString());
                    Recorder.getInstance()
                            .logEvent(Event.newBuilder()
                                    .setAdvancement(AdvancementEvent.newBuilder()
                                            .setId(advancementId)
                                            .build()));
                }
            }
        }
        progressBeforeUpdate.clear();
    }
}
