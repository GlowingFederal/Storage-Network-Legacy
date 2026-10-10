package mrriegel.storagenetwork.legacy;

import java.util.List;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class NetworkItem extends Item {
    protected final String name;
    protected final int variants;
    public NetworkItem(String name, int variants) {
        this.name = name; this.variants = variants; setUnlocalizedName("storagenetwork:" + name);
        setCreativeTab(StorageNetwork.TAB); setHasSubtypes(variants > 1); setMaxStackSize(variants == 6 ? 64 : 1);
    }
    @Override public String getUnlocalizedName(ItemStack stack) { return super.getUnlocalizedName() + (variants > 1 ? "_" + stack.getItemDamage() : ""); }
    @Override @SideOnly(Side.CLIENT) @SuppressWarnings("unchecked") public void getSubItems(Item item, CreativeTabs tab, List list) { for (int i = 0; i < variants; i++) list.add(new ItemStack(this, 1, i)); }
}
