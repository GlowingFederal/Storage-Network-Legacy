package mrriegel.storagenetwork.legacy;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

public final class FacadeRenderer extends TileEntitySpecialRenderer<TileNode> {
    @Override public void renderTileEntityAt(TileNode tile, double x, double y, double z, float partial, int destroy) {
        if (tile.facade.isEmpty()) return; Block block = Platform.findBlock(tile.facade); if (block == null || block instanceof NetworkBlock) return;
        bindTexture(TextureMap.locationBlocksTexture); GL11.glPushMatrix(); GL11.glTranslated(x, y, z);
        try { Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlockBrightness(block.getStateFromMeta(tile.facadeMeta), tile.getWorld().getLightBrightness(tile.getPos())); }
        finally { GL11.glPopMatrix(); }
    }
}
