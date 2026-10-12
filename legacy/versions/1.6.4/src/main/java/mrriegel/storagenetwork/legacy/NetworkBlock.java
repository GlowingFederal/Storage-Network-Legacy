package mrriegel.storagenetwork.legacy;

import java.util.List;
import mrriegel.storagenetwork.legacy.core.Position;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class NetworkBlock extends BlockContainer {
    public final int kind;
    public NetworkBlock(int kind) {
        super(StorageNetwork.blockIds[kind], Material.rock); this.kind = kind;
        setUnlocalizedName("storagenetwork:" + StorageNetwork.NAMES[kind]);
        setTextureName("storagenetwork:" + StorageNetwork.NAMES[kind] + (kind >= 3 && kind <= 8 ? "_n" : ""));
        setHardness(kind >= 3 && kind <= 8 ? 1.4F : 2.0F); setCreativeTab(StorageNetwork.TAB);
        if (cable()) {
            // Block's constructor sees kind == 0 and caches full-cube lighting.
            opaqueCubeLookup[blockID] = false; setLightOpacity(0); useNeighborBrightness[blockID] = true;
            setBlockBounds(.3125F, .3125F, .3125F, .6875F, .6875F, .6875F);
        }
    }
    public boolean cable() { return kind >= 3 && kind <= 8; }
    @Override public TileEntity createNewTileEntity(World world) {
        TileNode tile = kind == 0 ? new TileMaster() : new TileNode();
        tile.whitelist = kind == 5 || kind == 6; return tile;
    }
    @Override public boolean isOpaqueCube() { return !cable(); }
    @Override public boolean renderAsNormalBlock() { return !cable(); }
    @Override public int getRenderType() { return cable() ? StorageNetwork.proxy.cableRenderId() : 0; }
    @Override public void onBlockAdded(World world, int x, int y, int z) { changed(world, x, y, z); }
    @Override public void onNeighborBlockChange(World world, int x, int y, int z, int neighbor) { changed(world, x, y, z); }
    private void changed(World world, int x, int y, int z) {
        if (world.isRemote) return; TileEntity tile = world.getBlockTileEntity(x, y, z);
        if (!(tile instanceof TileNode)) return; TileNode node = (TileNode) tile;
        node.clearInventoryCache(); if (kind >= 4 && kind <= 8) node.findDirection();
        TileMaster master = node instanceof TileMaster ? (TileMaster) node : node.master(); if (master != null) master.refresh();
        for (int side = 0; side < 6; side++) {
            TileEntity adjacent = Platform.tile(world, node.position().offset(side));
            if (adjacent instanceof TileNode) { TileMaster other = adjacent instanceof TileMaster ? (TileMaster) adjacent : ((TileNode) adjacent).master(); if (other != null) other.refresh(); }
        }
        Platform.sync(node);
    }
    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy, float hz) {
        if (world.isRemote) return true; TileEntity tile = world.getBlockTileEntity(x, y, z); if (!(tile instanceof TileNode)) return false;
        if (player.isSneaking() && cable()) { ((TileNode) tile).rotate(); return true; }
        if (kind == 0 || kind == 3 || kind == 8) return false;
        player.openGui(StorageNetwork.instance, 0, world, x, y, z); return true;
    }
    @Override public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {
        if (world.isRemote || !player.isSneaking() || !cable() || !player.canPlayerEdit(x, y, z, 1, player.getHeldItem())) return;
        TileEntity tile = world.getBlockTileEntity(x, y, z); if (!(tile instanceof TileNode)) return;
        TileNode node = (TileNode) tile; ItemStack held = player.getHeldItem();
        if (Stacks.INSTANCE.empty(held)) { node.facade = ""; Platform.sync(node); return; }
        if (!(held.getItem() instanceof ItemBlock) || !StorageNetwork.allowed(Platform.itemName(held.getItem()), StorageNetwork.facadeBlacklist)) return;
        Block facade = held.itemID >= 0 && held.itemID < Block.blocksList.length ? Block.blocksList[held.itemID] : null; if (facade == null || facade instanceof NetworkBlock || facade.getRenderType() < 0) return;
        node.facade = Platform.blockKey(facade); node.facadeMeta = held.getItemDamage() & 15; Platform.sync(node);
    }
    @Override public void breakBlock(World world, int x, int y, int z, int oldBlock, int metadata) {
        TileEntity tile = world.getBlockTileEntity(x, y, z); if (tile instanceof TileNode && !world.isRemote) ((TileNode) tile).dropContents();
        super.breakBlock(world, x, y, z, oldBlock, metadata);
    }
    @Override @SuppressWarnings("unchecked") public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List boxes, Entity entity) {
        if (!cable()) { super.addCollisionBoxesToList(world, x, y, z, mask, boxes, entity); return; }
        TileEntity tile = world.getBlockTileEntity(x, y, z);
        if (StorageNetwork.facadeCollision && tile instanceof TileNode && !((TileNode) tile).facade.isEmpty()) {
            Block facade = Platform.findBlock(((TileNode) tile).facade);
            if (facade != null) { facade.setBlockBoundsBasedOnState(new FacadeAccess(world, x, y, z, facade, ((TileNode) tile).facadeMeta), x, y, z);
                AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(x + facade.getBlockBoundsMinX(), y + facade.getBlockBoundsMinY(), z + facade.getBlockBoundsMinZ(), x + facade.getBlockBoundsMaxX(), y + facade.getBlockBoundsMaxY(), z + facade.getBlockBoundsMaxZ());
                if (bounds.intersectsWith(mask)) boxes.add(bounds); return; }
        }
        setBlockBounds(.3125F, .3125F, .3125F, .6875F, .6875F, .6875F); super.addCollisionBoxesToList(world, x, y, z, mask, boxes, entity);
        for (int side = 0; side < 6; side++) {
            Position next = new Position(Platform.dimension(world), x, y, z).offset(side);
            TileEntity adjacent = Platform.tile(world, next);
            if (!(adjacent instanceof TileNode) && (!(tile instanceof TileNode) || ((TileNode) tile).direction != side)) continue;
            float loX = side == 4 ? 0 : .3125F, hiX = side == 5 ? 1 : .6875F;
            float loY = side == 0 ? 0 : .3125F, hiY = side == 1 ? 1 : .6875F;
            float loZ = side == 2 ? 0 : .3125F, hiZ = side == 3 ? 1 : .6875F;
            setBlockBounds(loX, loY, loZ, hiX, hiY, hiZ); super.addCollisionBoxesToList(world, x, y, z, mask, boxes, entity);
        }
        setBlockBounds(.3125F, .3125F, .3125F, .6875F, .6875F, .6875F);
    }
}
