package io.github.rifa456.msed.mixin.debug;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import io.github.rifa456.msed.debug.DebugKeys;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the world to black and skips its sky, terrain, particle, cloud, weather and late debug
 * passes while the minimal render (F10) is on.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true)
    private Vector4f onRenderLevelColor(Vector4f color) {
        if (DebugKeys.minimalRender()) {
            return new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
        }
        return color;
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void onAddSkyPass(
            FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (DebugKeys.minimalRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "addMainPass", at = @At("HEAD"), cancellable = true)
    private void onAddMainPass(
            FrameGraphBuilder frameGraphBuilder,
            Frustum frustum,
            Matrix4f matrix4f,
            GpuBufferSlice gpuBufferSlice,
            boolean bl,
            LevelRenderState levelRenderState,
            DeltaTracker deltaTracker,
            ProfilerFiller profilerFiller,
            CallbackInfo ci) {
        if (DebugKeys.minimalRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticlesPass", at = @At("HEAD"), cancellable = true)
    private void onAddParticlesPass(
            FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (DebugKeys.minimalRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void onAddCloudsPass(
            FrameGraphBuilder frameGraphBuilder,
            CloudStatus cloudStatus,
            Vec3 vec3,
            long l,
            float f,
            int i,
            float g,
            CallbackInfo ci) {
        if (DebugKeys.minimalRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "addWeatherPass", at = @At("HEAD"), cancellable = true)
    private void onAddWeatherPass(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
        if (DebugKeys.minimalRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "addLateDebugPass", at = @At("HEAD"), cancellable = true)
    private void onAddLateDebugPass(
            FrameGraphBuilder frameGraphBuilder,
            CameraRenderState cameraRenderState,
            GpuBufferSlice gpuBufferSlice,
            Matrix4f matrix4f,
            CallbackInfo ci) {
        if (DebugKeys.minimalRender()) {
            ci.cancel();
        }
    }
}
