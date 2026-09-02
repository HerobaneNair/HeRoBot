package hero.bane.herobot.mod.client.screen.ai.starfield;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class PixelBatch implements GuiElementRenderState {
    private int[] coords = new int[4096];
    private int[] colors = new int[1024];
    private int count;

    private Matrix3x2f pose;
    private ScreenRectangle scissor;
    private ScreenRectangle bounds;
    private int minX, minY, maxX, maxY;

    public PixelBatch() {
    }

    public void begin(GuiGraphicsExtractor g, int left, int top, int right, int bottom) {
        count = 0;
        pose = new Matrix3x2f(g.pose());
        scissor = new ScreenRectangle(left, top, right - left, bottom - top).transformAxisAligned(pose);
        bounds = null;
        minX = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxY = Integer.MIN_VALUE;
    }

    void rect(int x0, int y0, int x1, int y1, int argb) {
        if ((argb >>> 24) == 0) return;
        if (count == colors.length) grow();
        int i = count * 4;
        coords[i] = x0;
        coords[i + 1] = y0;
        coords[i + 2] = x1;
        coords[i + 3] = y1;
        colors[count] = argb;
        count++;
        if (x0 < minX) minX = x0;
        if (y0 < minY) minY = y0;
        if (x1 > maxX) maxX = x1;
        if (y1 > maxY) maxY = y1;
    }

    public void pixel(int x, int y, int argb) {
        rect(x, y, x + 1, y + 1, argb);
    }

    public void submit(GuiGraphicsExtractor g) {
        if (count == 0) return;
        ScreenRectangle box = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
        bounds = scissor == null ? box : scissor.intersection(box);
        if (bounds == null) {
            count = 0;
            return;
        }
        g.guiRenderState.addGuiElement(this);
    }

    private void grow() {
        int newCap = colors.length * 2;
        coords = java.util.Arrays.copyOf(coords, newCap * 4);
        colors = java.util.Arrays.copyOf(colors, newCap);
    }

    @Override
    public void buildVertices(@NonNull VertexConsumer vc) {
        for (int i = 0; i < count; i++) {
            int j = i * 4;
            int x0 = coords[j], y0 = coords[j + 1], x1 = coords[j + 2], y1 = coords[j + 3];
            int col = colors[i];
            vc.addVertexWith2DPose(pose, x0, y0).setColor(col);
            vc.addVertexWith2DPose(pose, x0, y1).setColor(col);
            vc.addVertexWith2DPose(pose, x1, y1).setColor(col);
            vc.addVertexWith2DPose(pose, x1, y0).setColor(col);
        }
    }

    @Override
    public @NonNull RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public @NonNull TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return scissor;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return bounds;
    }
}
