package io.github.rifa456.msed.mixin.events;

import io.github.rifa456.msed.proto.v1.Event;
import io.github.rifa456.msed.proto.v1.PlayerAteEvent;
import io.github.rifa456.msed.recording.Recorder;
import io.github.rifa456.msed.vocabulary.Vocabulary;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records an eating event when the player finishes eating a food item. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void onCompleteUsingItem(CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        if (thisEntity instanceof LocalPlayer) {
            ItemStack stack = thisEntity.getUseItem();
            if (stack.has(DataComponents.FOOD)) {
                int itemId = Vocabulary.getInstance().getId(stack);
                Recorder.getInstance()
                        .logEvent(Event.newBuilder()
                                .setPlayerAte(PlayerAteEvent.newBuilder()
                                        .setItemId(itemId)
                                        .build()));
            }
        }
    }
}
