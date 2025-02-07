package minetweaker.api.item;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import minetweaker.IUndoableAction;
import minetweaker.MineTweakerAPI;
import minetweaker.api.minecraft.MineTweakerMC;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.lang.reflect.Field;

@ZenClass("minetweaker.item.ToolMaterial")
@RequiredArgsConstructor
public class ToolMaterial {
    @ZenMethod
    public static void replaceMaterialRepairItem(String materialName, IIngredient itemStack) {
        for (val material: Item.ToolMaterial.values()) {
            if (materialName.equals(material.name())) {
                MineTweakerAPI.apply(new ApplyMaterialAction(material, MineTweakerMC.getItemStack(itemStack)));
                return;
            }
        }
        throw new IllegalArgumentException("Could not find tool material with name \"" + materialName + "\"!");
    }

    @ZenMethod
    public static void printMCMaterialsToLog() {
        for (val material: Item.ToolMaterial.values()) {
            MineTweakerAPI.logInfo("[TOOL MATERIAL] " + material.name());
        }
    }

    @RequiredArgsConstructor
    private static class ApplyMaterialAction implements IUndoableAction {
        private static final Field repairMaterialField;
        static {
            Field rmf;
            try {
                rmf = Item.ToolMaterial.class.getDeclaredField("repairMaterial");
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
            rmf.setAccessible(true);
            repairMaterialField = rmf;
        }
        private final Item.ToolMaterial theMaterial;
        private final ItemStack repairItem;
        private boolean applied;
        private Item oldItem;
        private ItemStack oldMaterial;

        @SneakyThrows
        @Override
        public void apply() {
            oldItem = theMaterial.customCraftingMaterial;
            theMaterial.customCraftingMaterial = null;
            oldMaterial = (ItemStack) repairMaterialField.get(theMaterial);
            repairMaterialField.set(theMaterial, null);
            theMaterial.setRepairItem(repairItem);
            applied = true;
        }

        @Override
        public boolean canUndo() {
            return applied;
        }

        @SneakyThrows
        @Override
        public void undo() {
            theMaterial.customCraftingMaterial = oldItem;
            repairMaterialField.set(theMaterial, oldMaterial);
            applied = false;
            oldItem = null;
            oldMaterial = null;
        }

        @Override
        public String describe() {
            return "Replacing repair item for tool material " + theMaterial.name();
        }

        @Override
        public String describeUndo() {
            return "Restoring old repair item for tool material " + theMaterial.name();
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }
}
