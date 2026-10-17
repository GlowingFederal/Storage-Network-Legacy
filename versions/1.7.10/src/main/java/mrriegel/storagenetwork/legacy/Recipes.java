package mrriegel.storagenetwork.legacy;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class Recipes {
    private Recipes() {}
    public static void register() {
        String[] names = {"collector_remote", "controller", "ex_kabel", "im_kabel", "inventory", "kabel", "master", "picker_remote", "process_kabel", "remote0", "remote1", "remote2", "remote3", "request", "simple_kabel", "storage_kabel", "upgrade0", "upgrade1", "upgrade2", "upgrade3", "upgrade4", "upgrade5"};
        for (String name : names) {
            InputStreamReader reader = new InputStreamReader(Recipes.class.getResourceAsStream("/assets/storagenetwork/legacy-recipes/" + name + ".json"));
            try {
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject(); List<Object> recipe = new ArrayList<Object>();
                for (JsonElement row : json.getAsJsonArray("pattern")) recipe.add(row.getAsString());
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("key").entrySet()) {
                    recipe.add(entry.getKey().charAt(0)); recipe.add(stack(entry.getValue().getAsJsonObject()));
                }
                GameRegistry.addRecipe(stack(json.getAsJsonObject("result")), recipe.toArray());
            } finally { try { reader.close(); } catch (java.io.IOException error) { throw new IllegalStateException(error); } }
        }
    }
    private static ItemStack stack(JsonObject json) {
        String name = json.get("item").getAsString();
        // These four ingredients do not exist in the target era. All other recipes retain the canonical inputs/counts.
        if (name.equals("minecraft:iron_nugget")) name = "minecraft:iron_ingot";
        if (name.equals("minecraft:observer")) name = "minecraft:daylight_detector";
        if (name.equals("minecraft:purpur_block")) name = "minecraft:obsidian";
        if (name.equals("minecraft:magma")) name = "minecraft:netherrack";
        Item item = (Item) Item.itemRegistry.getObject(name);
        if (item == null) throw new IllegalStateException("Unavailable SSN recipe ingredient: " + name);
        int count = json.has("count") ? json.get("count").getAsInt() : 1, metadata = json.has("data") ? json.get("data").getAsInt() : 0;
        return new ItemStack(item, count, metadata);
    }
}
