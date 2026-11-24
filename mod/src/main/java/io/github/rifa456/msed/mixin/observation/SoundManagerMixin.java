package io.github.rifa456.msed.mixin.observation;

import io.github.rifa456.msed.observation.HearingCollector;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Passes every sound the game plays to the hearing collector, before the audio engine decides
 * whether it is audible.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void msed$onPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        HearingCollector.onDispatch((SoundManager) (Object) this, sound);
    }

    @Inject(method = "playDelayed", at = @At("HEAD"))
    private void msed$onPlayDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        HearingCollector.onDispatch((SoundManager) (Object) this, sound);
    }
}
