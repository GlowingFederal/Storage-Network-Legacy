package mrriegel.storagenetwork.legacy;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

public final class CableRenderer implements ISimpleBlockRenderingHandler {
    private final int id;
    public CableRenderer(int id) { this.id = id; }
    public int getRenderId() { return id; }
    public boolean shouldRender3DInInventory() { return true; }
    public void renderInventoryBlock(Block block, int metadata, int model, RenderBlocks renderer) {
        renderer.setRenderBounds(.3125, .3125, .3125, .6875, .6875, .6875);
        GL11.glPushMatrix(); GL11.glTranslatef(-.5F, -.5F, -.5F);
        Tessellator tessellator = Tessellator.instance;
        for (int side = 0; side < 6; side++) {
            tessellator.startDrawingQuads();
            switch (side) {
                case 0: tessellator.setNormal(0, -1, 0); renderer.renderFaceYNeg(block, 0, 0, 0, block.getIcon(side, metadata)); break;
                case 1: tessellator.setNormal(0, 1, 0); renderer.renderFaceYPos(block, 0, 0, 0, block.getIcon(side, metadata)); break;
                case 2: tessellator.setNormal(0, 0, -1); renderer.renderFaceZNeg(block, 0, 0, 0, block.getIcon(side, metadata)); break;
                case 3: tessellator.setNormal(0, 0, 1); renderer.renderFaceZPos(block, 0, 0, 0, block.getIcon(side, metadata)); break;
                case 4: tessellator.setNormal(-1, 0, 0); renderer.renderFaceXNeg(block, 0, 0, 0, block.getIcon(side, metadata)); break;
                case 5: tessellator.setNormal(1, 0, 0); renderer.renderFaceXPos(block, 0, 0, 0, block.getIcon(side, metadata)); break;
            }
            tessellator.draw();
        }
        GL11.glPopMatrix(); renderer.setRenderBounds(0, 0, 0, 1, 1, 1);
    }
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int model, RenderBlocks renderer) {
        TileEntity tile = world.getBlockTileEntity(x, y, z); TileNode node = tile instanceof TileNode ? (TileNode) tile : null;
        if (node != null && !node.facade.isEmpty()) {
            Block facade = Platform.findBlock(node.facade);
            if (facade != null && !(facade instanceof NetworkBlock)) {
                IBlockAccess previous = renderer.blockAccess;
                renderer.blockAccess = new FacadeAccess(world, x, y, z, facade, node.facadeMeta);
                try { return renderer.renderBlockByRenderType(facade, x, y, z); }
                finally { renderer.blockAccess = previous; renderer.setRenderBounds(0, 0, 0, 1, 1, 1); }
            }
        }
        boolean previousFaces = renderer.renderAllFaces; renderer.renderAllFaces = true;
        try {
            renderer.setRenderBounds(.3125, .3125, .3125, .6875, .6875, .6875); renderer.renderStandardBlock(block, x, y, z);
            for (int side = 0; side < 6; side++) {
                int nx = x + mrriegel.storagenetwork.legacy.core.Position.DX[side], ny = y + mrriegel.storagenetwork.legacy.core.Position.DY[side], nz = z + mrriegel.storagenetwork.legacy.core.Position.DZ[side];
                boolean target = node != null && node.direction == side;
                if (!target && !(world.getBlockTileEntity(nx, ny, nz) instanceof TileNode)) continue;
                renderer.setRenderBounds(side == 4 ? 0 : .3125, side == 0 ? 0 : .3125, side == 2 ? 0 : .3125, side == 5 ? 1 : .6875, side == 1 ? 1 : .6875, side == 3 ? 1 : .6875);
                renderer.renderStandardBlock(block, x, y, z);
                if (target) {
                    renderer.setRenderBounds(side == 4 ? 0 : side == 5 ? .9375 : .1875, side == 0 ? 0 : side == 1 ? .9375 : .1875, side == 2 ? 0 : side == 3 ? .9375 : .1875,
                        side == 4 ? .0625 : side == 5 ? 1 : .8125, side == 0 ? .0625 : side == 1 ? 1 : .8125, side == 2 ? .0625 : side == 3 ? 1 : .8125);
                    renderer.renderStandardBlock(block, x, y, z);
                }
            }
        } finally { renderer.renderAllFaces = previousFaces; renderer.setRenderBounds(0, 0, 0, 1, 1, 1); }
        return true;
    }
}
