package mrriegel.storagenetwork.legacy;

import java.util.List;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

public class NetworkItem extends Item {
    protected final String name;
    protected final int variants;
    private IIcon[] icons;
    public NetworkItem(String name, int variants) {
        this.name = name; this.variants = variants; setUnlocalizedName("storagenetwork:" + name);
        setCreativeTab(StorageNetwork.TAB); setHasSubtypes(variants > 1); setMaxStackSize(variants == 6 ? 64 : 1);
    }
    @Override public String getUnlocalizedName(ItemStack stack) { return super.getUnlocalizedName() + (variants > 1 ? "_" + stack.getItemDamage() : ""); }
    @Override @SideOnly(Side.CLIENT) public void registerIcons(IIconRegister registry) {
        icons = new IIcon[variants]; for (int i = 0; i < variants; i++) icons[i] = registry.registerIcon("storagenetwork:" + name + (variants > 1 ? "_" + i : ""));
    }
    @Override @SideOnly(Side.CLIENT) public IIcon getIconFromDamage(int metadata) { return icons[TileNode.bound(metadata, 0, variants - 1, 0)]; }
    @Override @SideOnly(Side.CLIENT) @SuppressWarnings("unchecked") public void getSubItems(Item item, CreativeTabs tab, List list) { for (int i = 0; i < variants; i++) list.add(new ItemStack(this, 1, i)); }
}
