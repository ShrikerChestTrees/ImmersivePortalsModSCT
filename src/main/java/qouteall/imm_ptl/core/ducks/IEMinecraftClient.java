package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;

public interface IEMinecraftClient {
    void ip_setFrameBuffer(RenderTarget buffer);
    
    Screen ip_getCurrentScreen();
    
    void ip_setWorldRenderer(LevelRenderer r);
    
    void ip_setRenderBuffers(RenderBuffers arg);
    
    Thread ip_getRunningThread();
}
