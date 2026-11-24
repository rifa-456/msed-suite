package io.github.rifa456.msed.observation.vision;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

/**
 * The GPU readback of the main render target into top-down RGB frames of a fixed size.
 * {@link #startReadback} scales the current frame and starts an asynchronous copy, and
 * {@link #finishOldest} collects it later, so the transfer overlaps with game work. Readbacks
 * finish in the order they started, and at most two are in flight. All methods except
 * {@link #destroy} must run on the render thread.
 *
 * <p>On OpenGL, the frame is blitted into a dedicated framebuffer and read into one of two pixel
 * buffer objects. Other render backends are not supported.
 */
public final class RgbCapture {
    private static final int PBO_COUNT = 2;

    private int fboId = -1;
    private int readFboId = -1;
    private int rboId = -1;
    private final int[] pboIds = new int[PBO_COUNT];
    private final ArrayDeque<Integer> pending = new ArrayDeque<>(PBO_COUNT);
    private int nextPbo = 0;
    private int width;
    private int height;
    private boolean initialized = false;
    private byte[] rgbaScratch = new byte[0];

    /**
     * Allocates the GPU resources for frames of the given size, replacing earlier ones and
     * dropping pending readbacks. Does nothing when the size is unchanged.
     *
     * @param w the frame width in pixels
     * @param h the frame height in pixels
     * @throws IllegalStateException if the capture framebuffer cannot be created
     */
    public void resize(int w, int h) {
        if (initialized && this.width == w && this.height == h) return;
        destroy();
        this.width = w;
        this.height = h;
        fboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        rboId = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rboId);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, rboId);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteRenderbuffers(rboId);
            GL30.glDeleteFramebuffers(fboId);
            throw new IllegalStateException("Failed to create the capture framebuffer");
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        readFboId = GL30.glGenFramebuffers();
        for (int i = 0; i < PBO_COUNT; i++) {
            pboIds[i] = GL21.glGenBuffers();
            GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[i]);
            GL21.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL21.GL_STREAM_READ);
        }
        GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        rgbaScratch = new byte[width * height * 4];
        pending.clear();
        initialized = true;
    }

    /**
     * Releases the GPU resources and drops pending readbacks. Called off the render thread, it
     * schedules the release on the render thread instead.
     */
    public void destroy() {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(this::destroy);
            return;
        }
        if (!initialized) return;
        GL30.glDeleteFramebuffers(fboId);
        GL30.glDeleteFramebuffers(readFboId);
        GL30.glDeleteRenderbuffers(rboId);
        for (int id : pboIds) GL21.glDeleteBuffers(id);
        pending.clear();
        initialized = false;
    }

    /**
     * Returns the number of readbacks started but not collected yet.
     *
     * @return the count, from 0 to 2
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * Starts reading the current contents of the main render target. When two readbacks are
     * pending, the caller must collect the oldest first.
     *
     * @param client the client whose main render target is read
     * @return {@code true} if the readback started; {@code false} if this capture has no GPU
     *     resources, two readbacks are pending, or the target has no color texture yet
     * @throws IllegalStateException if the render backend is not OpenGL
     */
    public boolean startReadback(Minecraft client) {
        RenderSystem.assertOnRenderThread();
        if (!initialized || pending.size() >= PBO_COUNT) return false;
        RenderTarget mainTarget = client.getMainRenderTarget();
        GpuTexture colorTexture = mainTarget.getColorTexture();
        if (colorTexture == null) return false;
        if (!(colorTexture instanceof GlTexture glTexture)) {
            throw new IllegalStateException("Video capture needs the OpenGL backend, got "
                    + colorTexture.getClass().getName());
        }
        int oldDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glFramebufferTexture2D(
                    GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glTexture.glId(), 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboId);
            // OpenGL rows start at the bottom. Flipping the destination makes the image top-down, as video expects.
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    mainTarget.width,
                    mainTarget.height,
                    0,
                    height,
                    width,
                    0,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int pbo = nextPbo;
            nextPbo = (nextPbo + 1) % PBO_COUNT;
            GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[pbo]);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
            pending.addLast(pbo);
            return true;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, oldPackAlignment);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDrawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldReadFbo);
            GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        }
    }

    /**
     * Copies the oldest pending readback into {@code rgb}, top row first, without the alpha
     * channel. The readback counts as collected even when the copy fails.
     *
     * @param rgb the destination, {@code width * height * 3} bytes
     * @return {@code false} if nothing was pending or the buffer could not be mapped, in which
     *     case {@code rgb} is unchanged
     */
    public boolean finishOldest(byte[] rgb) {
        RenderSystem.assertOnRenderThread();
        Integer pbo = pending.pollFirst();
        if (pbo == null) return false;
        int oldPbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pboIds[pbo]);
        try {
            ByteBuffer mapped = GL21.glMapBuffer(GL21.GL_PIXEL_PACK_BUFFER, GL21.GL_READ_ONLY);
            if (mapped == null) return false;
            try {
                mapped.get(rgbaScratch, 0, rgbaScratch.length);
                for (int src = 0, dst = 0; dst < rgb.length; src += 4, dst += 3) {
                    rgb[dst] = rgbaScratch[src];
                    rgb[dst + 1] = rgbaScratch[src + 1];
                    rgb[dst + 2] = rgbaScratch[src + 2];
                }
            } finally {
                GL21.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            }
        } finally {
            GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldPbo);
        }
        return true;
    }
}
