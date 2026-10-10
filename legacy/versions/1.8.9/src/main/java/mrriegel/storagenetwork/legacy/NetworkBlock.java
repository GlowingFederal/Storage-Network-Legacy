package mrriegel.storagenetwork.legacy;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class NetworkBlock extends BlockContainer {
    public static final PropertyBool[] CONNECTED = {PropertyBool.create("down"), PropertyBool.create("up"), PropertyBool.create("north"), PropertyBool.create("south"), PropertyBool.create("west"), PropertyBool.create("east")};
    public static final PropertyInteger TARGET = PropertyInteger.create("target", 0, 6);
    public static final PropertyBool COVERED = PropertyBool.create("covered");
    public final int kind;
    public NetworkBlock(int kind) {
        super(Material.rock); this.kind = kind; setUnlocalizedName("storagenetwork:" + StorageNetwork.NAMES[kind]); setCreativeTab(StorageNetwork.TAB); setHardness(cable() ? 1.4F : 2F);
        IBlockState state = blockState.getBaseState();
        if (cable()) { state = state.withProperty(TARGET, 6).withProperty(COVERED, false); for (PropertyBool face : CONNECTED) state = state.withProperty(face, false); }
        setDefaultState(state);
        if (cable()) setBlockBounds(.3125F, .3125F, .3125F, .6875F, .6875F, .6875F);
    }
    public boolean cable() { return kind >= 3 && kind <= 8; }
    @Override public TileEntity createNewTileEntity(World world, int metadata) { TileNode node = kind == 0 ? new TileMaster() : new TileNode(); node.whitelist = kind == 5 || kind == 6; return node; }
    public static final class CableBlock extends NetworkBlock { public CableBlock(int kind) { super(kind); } }
    @Override protected BlockState createBlockState() { return new BlockState(this, this instanceof CableBlock ? new IProperty[] {CONNECTED[0], CONNECTED[1], CONNECTED[2], CONNECTED[3], CONNECTED[4], CONNECTED[5], TARGET, COVERED} : new IProperty[0]); }
    @Override public int getMetaFromState(IBlockState state) { return 0; }
    @Override public IBlockState getStateFromMeta(int metadata) { return getDefaultState(); }
    @Override public boolean isOpaqueCube() { return !cable(); }
    @Override public boolean isFullCube() { return !cable(); }
    @Override public int getRenderType() { return 3; }
    @Override public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!cable()) return state;
        TileEntity tile = world.getTileEntity(pos); TileNode node = tile instanceof TileNode ? (TileNode) tile : null;
        boolean covered = node != null && !node.facade.isEmpty(); state = state.withProperty(COVERED, covered).withProperty(TARGET, node == null || node.direction < 0 || covered ? 6 : node.direction);
        for (EnumFacing side : EnumFacing.values()) state = state.withProperty(CONNECTED[side.ordinal()], !covered && world.getTileEntity(pos.offset(side)) instanceof TileNode);
        return state;
    }
    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) { changed(world, pos); }
    @Override public void onNeighborBlockChange(World world, BlockPos pos, IBlockState state, Block neighbor) { changed(world, pos); }
    private void changed(World world, BlockPos pos) {
        if (world.isRemote) return; TileEntity tile = world.getTileEntity(pos); if (!(tile instanceof TileNode)) return; TileNode node = (TileNode) tile;
        node.clearInventoryCache(); if (kind >= 4 && kind <= 8) node.findDirection();
        TileMaster master = node instanceof TileMaster ? (TileMaster) node : node.master(); if (master != null) master.refresh();
        for (EnumFacing side : EnumFacing.values()) { TileEntity neighbor = Platform.tile(world, node.position().offset(side.ordinal())); if (neighbor instanceof TileNode) { TileMaster other = neighbor instanceof TileMaster ? (TileMaster) neighbor : ((TileNode) neighbor).master(); if (other != null) other.refresh(); } }
        Platform.sync(node);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumFacing side, float hx, float hy, float hz) {
        if (world.isRemote) return true; TileEntity tile = world.getTileEntity(pos); if (!(tile instanceof TileNode)) return false;
        if (player.isSneaking() && cable()) { ((TileNode) tile).rotate(); return true; }
        if (kind == 0 || kind == 3 || kind == 8) return false; player.openGui(StorageNetwork.instance, 0, world, pos.getX(), pos.getY(), pos.getZ()); return true;
    }
    @Override public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote || !player.isSneaking() || !cable() || !player.canPlayerEdit(pos, EnumFacing.UP, player.getHeldItem())) return;
        TileEntity tile = world.getTileEntity(pos); if (!(tile instanceof TileNode)) return; TileNode node = (TileNode) tile; ItemStack held = player.getHeldItem();
        if (Stacks.INSTANCE.empty(held)) { node.facade = ""; Platform.sync(node); return; }
        if (!(held.getItem() instanceof ItemBlock) || !StorageNetwork.allowed(Platform.itemName(held.getItem()), StorageNetwork.facadeBlacklist)) return;
        Block facade = Block.getBlockFromItem(held.getItem()); if (facade == null || facade instanceof NetworkBlock || facade.getRenderType() != 3) return;
        node.facade = String.valueOf(Block.blockRegistry.getNameForObject(facade)); node.facadeMeta = held.getItemDamage() & 15; Platform.sync(node);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) { TileEntity tile = world.getTileEntity(pos); if (tile instanceof TileNode && !world.isRemote) ((TileNode) tile).dropContents(); super.breakBlock(world, pos, state); }
    @Override public void addCollisionBoxesToList(World world, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> boxes, Entity entity) {
        if (!cable()) { super.addCollisionBoxesToList(world, pos, state, mask, boxes, entity); return; }
        TileEntity tile = world.getTileEntity(pos); TileNode node = tile instanceof TileNode ? (TileNode) tile : null;
        if (StorageNetwork.facadeCollision && node != null && !node.facade.isEmpty()) {
            Block facade = Platform.findBlock(node.facade); if (facade != null && !(facade instanceof NetworkBlock)) { facade.addCollisionBoxesToList(world, pos, facade.getStateFromMeta(node.facadeMeta), mask, boxes, entity); return; }
        }
        setBlockBounds(.3125F, .3125F, .3125F, .6875F, .6875F, .6875F); super.addCollisionBoxesToList(world, pos, state, mask, boxes, entity);
        for (EnumFacing face : EnumFacing.values()) {
            if (!(Platform.tile(world, new mrriegel.storagenetwork.legacy.core.Position(Platform.dimension(world), pos.getX(), pos.getY(), pos.getZ()).offset(face.ordinal())) instanceof TileNode) && (node == null || node.direction != face.ordinal())) continue;
            int side = face.ordinal(); setBlockBounds(side == 4 ? 0 : .3125F, side == 0 ? 0 : .3125F, side == 2 ? 0 : .3125F, side == 5 ? 1 : .6875F, side == 1 ? 1 : .6875F, side == 3 ? 1 : .6875F);
            super.addCollisionBoxesToList(world, pos, state, mask, boxes, entity);
        }
        setBlockBounds(.3125F, .3125F, .3125F, .6875F, .6875F, .6875F);
    }
}
