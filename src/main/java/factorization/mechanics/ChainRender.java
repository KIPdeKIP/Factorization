package factorization.mechanics;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import factorization.shared.Core;
import factorization.algos.FastBag;
import factorization.util.SpaceUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.lwjgl.opengl.GL11;

import java.lang.ref.WeakReference;

public class ChainRender {
    public static final ChainRender instance = new ChainRender();

    private ChainRender() {
        Core.loadBus(this);
    }

    private FastBag<WeakReference<ChainLink>> chains = new FastBag<WeakReference<ChainLink>>();
    boolean needsRebag = true;

    public ChainLink add() {
        ChainLink ret = new ChainLink();
        ret.bagIndex = chains.size();
        chains.add(new WeakReference<ChainLink>(ret));
        rebag();
        return ret;
    }

    void release(ChainLink link) {
        final int index = link.bagIndex;
        chains.remove(index);
        if (index >= chains.size()) return;
        ChainLink newEntry = chains.get(index).get();
        if (newEntry == null) {
            rebag();
            return;
        }
        newEntry.bagIndex = index;
    }

    void rebag() {
        if (!needsRebag) return;
        needsRebag = false;
        for (int i = 0; i < chains.size(); i++) {
            if (chains.get(i).get() != null) continue;
            chains.remove(i);
            i--;
        }
        for (int i = 0; i < chains.size(); i++) {
            WeakReference<ChainLink> ref = chains.get(i);
            ChainLink chain = ref.get();
            if (chain == null) {
                needsRebag = true; // That'd be pretty obnoxious! But would only happen if a GC happened to trigger while this function is running.
                continue;
            }
            chain.bagIndex = i;
        }
    }

    @SubscribeEvent
    public void drawChains(RenderWorldLastEvent event) {
        if (chains.isEmpty()) return;
        final float partial = event.partialTicks;
        final ICamera camera = getFrustum(partial);

        final WorldClient w = event.context.theWorld;
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityRenderer er = mc.entityRenderer;
        final TextureManager textureManager = mc.getTextureManager();
        final Tessellator tess = Tessellator.instance;

        boolean setup = false;
        final AxisAlignedBB workBox = SpaceUtil.newBox();
        final Vec3 workStart = SpaceUtil.newVec(), workEnd = SpaceUtil.newVec();
        for (WeakReference<ChainLink> ref : chains) {
            ChainLink chain = ref.get();
            if (chain == null) {
                needsRebag = true;
                continue;
            }
            if (!chain.cameraCheck(camera, partial, workBox, workStart, workEnd)) continue;
            if (!setup) {
                setup = true;
                tess.startDrawingQuads();
                tess.setColorRGBA(0xFF, 0xFF, 0xFF, 0xFF);

                EntityLivingBase eyePos = mc.renderViewEntity;
                double cx = eyePos.lastTickPosX + (eyePos.posX - eyePos.lastTickPosX) * (double) event.partialTicks;
                double cy = eyePos.lastTickPosY + (eyePos.posY - eyePos.lastTickPosY) * (double) event.partialTicks;
                double cz = eyePos.lastTickPosZ + (eyePos.posZ - eyePos.lastTickPosZ) * (double) event.partialTicks;
                tess.setTranslation(-cx, -cy, -cz);
            }
            chain.draw(w, tess, camera, partial, workBox, workStart, workEnd);
        }
        if (!setup) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT );
        textureManager.bindTexture(new ResourceLocation("factorization", "textures/chain.png"));
        er.enableLightmap(0);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        tess.draw();
        er.disableLightmap(0);
        GL11.glPopAttrib();
        tess.setTranslation(0, 0, 0);
        textureManager.bindTexture(Core.blockAtlas);
    }

    @SubscribeEvent
    public void reset(WorldEvent.Unload unload) {
        if (!unload.world.isRemote) return;
        for (WeakReference<ChainLink> ref : chains) {
            ChainLink chain = ref.get();
            if (chain == null) continue;
            chain.bagIndex = -1;
        }
        chains.clear();
    }

    ICamera getFrustum(float partial) {
        // Unfortunately we have to make our own Frustum.
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityLivingBase eye = mc.renderViewEntity;
        double eyeX = eye.lastTickPosX + (eye.posX - eye.lastTickPosX) * (double)partial;
        double eyeY = eye.lastTickPosY + (eye.posY - eye.lastTickPosY) * (double)partial;
        double eyeZ = eye.lastTickPosZ + (eye.posZ - eye.lastTickPosZ) * (double)partial;

        Frustrum frustrum = new Frustrum(); // Notch can't spell
        frustrum.setPosition(eyeX, eyeY, eyeZ);
        return frustrum;
    }


}
