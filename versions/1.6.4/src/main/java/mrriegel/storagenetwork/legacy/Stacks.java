package mrriegel.storagenetwork.legacy;

import mrriegel.storagenetwork.legacy.core.StackAccess;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public final class Stacks implements StackAccess<ItemStack> {
    public static final Stacks INSTANCE = new Stacks();
    public boolean empty(ItemStack stack) { return stack == null || stack.stackSize <= 0 || stack.getItem() == null; }
    public int count(ItemStack stack) { return empty(stack) ? 0 : stack.stackSize; }
    public int limit(ItemStack stack) { return empty(stack) ? 0 : stack.getMaxStackSize(); }
    public ItemStack copy(ItemStack stack, int count) {
        if (empty(stack) || count <= 0) return null;
        ItemStack result = stack.copy(); result.stackSize = count; return result;
    }
    public boolean same(ItemStack left, ItemStack right) {
        return !empty(left) && !empty(right) && left.getItem() == right.getItem()
            && left.getItemDamage() == right.getItemDamage() && ItemStack.areItemStackTagsEqual(left, right);
    }
    public static int[] oreIds(ItemStack stack) { java.util.List<Integer> ids = new java.util.ArrayList<Integer>(); for (String name : OreDictionary.getOreNames()) for (ItemStack ore : OreDictionary.getOres(name)) { if (ore.getItem() == stack.getItem() && (ore.getItemDamage() == OreDictionary.WILDCARD_VALUE || ore.getItemDamage() == stack.getItemDamage())) { ids.add(OreDictionary.getOreID(name)); break; } } int[] result = new int[ids.size()]; for (int i = 0; i < result.length; i++) result[i] = ids.get(i); return result; }
    public static boolean matches(ItemStack filter, ItemStack stack, boolean meta, boolean ore, boolean nbt) {
        if (INSTANCE.empty(filter) || INSTANCE.empty(stack)) return false;
        if (ore) {
            for (int left : oreIds(filter))
                for (int right : oreIds(stack)) if (left == right) return true;
        }
        return filter.getItem() == stack.getItem() && (!meta || filter.getItemDamage() == OreDictionary.WILDCARD_VALUE
            || filter.getItemDamage() == stack.getItemDamage()) && (!nbt || ItemStack.areItemStackTagsEqual(filter, stack));
    }
}
