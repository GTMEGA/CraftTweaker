package minetweaker.mc1710.recipes;

import minetweaker.IUndoableAction;
import minetweaker.MineTweakerAPI;
import minetweaker.MineTweakerImplementationAPI;
import minetweaker.api.item.IIngredient;
import minetweaker.api.item.IItemStack;
import minetweaker.api.minecraft.MineTweakerMC;
import minetweaker.api.player.IPlayer;
import minetweaker.api.recipes.ICraftingInventory;
import minetweaker.api.recipes.ICraftingRecipe;
import minetweaker.api.recipes.IRecipeAction;
import minetweaker.api.recipes.IRecipeFunction;
import minetweaker.api.recipes.IRecipeManager;
import minetweaker.api.recipes.ShapedRecipe;
import minetweaker.api.recipes.ShapelessRecipe;
import minetweaker.mc1710.item.MCItemStack;
import minetweaker.mc1710.util.MineTweakerHacks;
import minetweaker.util.IEventHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import org.apache.commons.lang3.tuple.Pair;
import stanhebben.zenscript.annotations.Optional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static minetweaker.api.minecraft.MineTweakerMC.getIItemStack;
import static minetweaker.api.minecraft.MineTweakerMC.getItemStack;
import static minetweaker.api.minecraft.MineTweakerMC.getOreDict;

/**
 * @author Stan
 */
public final class MCRecipeManager implements IRecipeManager {
    public static final List<ActionBaseAddRecipe> recipesToAdd = new ArrayList<>();
    public static final List<ActionBaseRemoveRecipes> recipesToRemove = new ArrayList<>();
    public static final ActionRemoveRecipesNoIngredients actionRemoveRecipesNoIngredients = new ActionRemoveRecipesNoIngredients();

    public static List<IRecipe> recipes;
    public static final List<ICraftingRecipe> transformerRecipes = new ArrayList<>();

    public MCRecipeManager() {
        MineTweakerImplementationAPI.onPostReload(new HandlePostReload());
    }

    public static boolean hasTransformerRecipes() {
        return transformerRecipes.size() > 0;
    }

    public static void applyTransformations(ICraftingInventory inventory, IPlayer byPlayer) {
        for (ICraftingRecipe recipe : transformerRecipes) {
            if (recipe.matches(inventory)) {
                recipe.applyTransformers(inventory, byPlayer);
                return;
            }
        }
    }

    private static boolean matches(Object input, IIngredient ingredient) {
        if ((input == null) != (ingredient == null)) {
            return false;
        } else if (ingredient != null) {
            if (input instanceof ItemStack) {
                return ingredient.matches(getIItemStack((ItemStack) input));
            } else if (input instanceof String) {
                return ingredient.contains(getOreDict((String) input));
            }
        }

        return true;
    }

    @Override
    public List<ICraftingRecipe> getAll() {
        List<ICraftingRecipe> results = new ArrayList<>();

        for (IRecipe recipe : recipes) {
            ICraftingRecipe converted = RecipeConverter.toCraftingRecipe(recipe);
            results.add(converted);
        }

        return results;
    }

    @Override
    public List<ICraftingRecipe> getRecipesFor(IIngredient ingredient) {
        List<ICraftingRecipe> results = new ArrayList<>();

        for (IRecipe recipe : recipes) {
            if (ingredient.matches(MineTweakerMC.getIItemStack(recipe.getRecipeOutput()))) {
                ICraftingRecipe converted = RecipeConverter.toCraftingRecipe(recipe);
                results.add(converted);
            }
        }

        return results;
    }

    @Override
    public int remove(IIngredient output) {
        return remove(output, false);
    }

    @Override
    public int remove(IIngredient output, @Optional boolean nbtMatch) {
        if(output == null) {
            MineTweakerAPI.logError("Cannot remove recipes for a null item!");
            return 0;
        }
        actionRemoveRecipesNoIngredients.addOutput(output, nbtMatch);
        return 1;
    }

    @Override
    public void addShaped(IItemStack output, IIngredient[][] ingredients, IRecipeFunction function) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, function, null, false));
    }

    @Override
    public void addShaped(IItemStack output, IIngredient[][] ingredients, IRecipeAction action) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, null, action, false));
    }

    @Override
    public void addShaped(IItemStack output, IIngredient[][] ingredients) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, null, null, false));
    }

    @Override
    public void addShaped(IItemStack output, IIngredient[][] ingredients, IRecipeFunction function, IRecipeAction action) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, function, action, false));
    }

    @Override
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients, IRecipeFunction function) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, function, null, true));
    }

    @Override
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, null, null, true));
    }

    @Override
    public void addShaped(IItemStack output, IIngredient[][] ingredients, IRecipeAction action) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, null, action, false));
    }

    @Override
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients, IRecipeFunction function, IRecipeAction action) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, function, action, true));
    }

    @Override
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients, IRecipeAction action) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, null, action, true));
    }

    @Override
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients, IRecipeFunction function) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, function, null, true));
    }

    @Override
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, null, null, true));
    }

    @Override
    public void addShapeless(IItemStack output, IIngredient[] ingredients, IRecipeFunction function, IRecipeAction action) {
        if(checkShapelessNulls(output, ingredients))
            return;
        
        recipesToAdd.add(new ActionAddShapelessRecipe(output, ingredients, function, action));
    }

    @Override
    public void addShapeless(IItemStack output, IIngredient[] ingredients, IRecipeFunction function){
        addShapeless(output, ingredients, function, null);
    }

    @Override
    public void addShapeless(IItemStack output, IIngredient[] ingredients, IRecipeAction action){
        addShapeless(output, ingredients, null, action);
    }

    @Override
    public void addShapeless(IItemStack output, IIngredient[] ingredients){
        addShapeless(output, ingredients, null, null);
    }

    private boolean checkShapelessNulls(IItemStack output, IIngredient[] ingredients) {
        if(output != null && Arrays.stream(ingredients).allMatch(Objects::isNull)) {
            MineTweakerAPI.logError("Null not allowed in shapeless recipes! Recipe for: " + output + " not created!");
            return true;
        }
        return false;
    }

    @Override
    public int removeShaped(IIngredient output, IIngredient[][] ingredients) {
        if(output == null) {
            MineTweakerAPI.logError("Cannot remove recipes for a null item!");
            return 0;
        }
        recipesToRemove.add(new ActionRemoveShapedRecipes(output, ingredients));
        return 1;
    }

    @Override
    public int removeShaped(IIngredient output, IIngredient[][] ingredients) {
        return removeShaped(output, null);
    }

    @Override
    public int removeShapeless(IIngredient output, IIngredient[] ingredients, boolean wildcard) {
        if(output == null) {
            MineTweakerAPI.logError("Cannot remove recipes for a null item!");
            return 0;
        }
        recipesToRemove.add(new ActionRemoveShapelessRecipes(output, ingredients, wildcard));
        return 1;
    }

    @Override
    public int removeShapeless(IIngredient output, IIngredient[] ingredients) {
        return removeShapeless(output, ingredients, true);
    }

    @Override
    public int removeShapeless(IIngredient output, boolean wildcard) {
        return removeShapeless(output, null, wildcard);
    }

    @Override
    public int removeShapeless(IIngredient output) {
        return removeShapeless(output, null, true);
    }

    @Override
    public IItemStack craft(IItemStack[][] contents) {
        Container container = new ContainerVirtual();

        int width = 0;
        int height = contents.length;
        for (IItemStack[] row : contents) {
            width = Math.max(width, row.length);
        }

        ItemStack[] iContents = new ItemStack[width * height];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < contents[i].length; j++) {
                if (contents[i][j] != null) {
                    iContents[i * width + j] = getItemStack(contents[i][j]);
                }
            }
        }

        InventoryCrafting inventory = new InventoryCrafting(container, width, height);
        for (int i = 0; i < iContents.length; i++) {
            inventory.setInventorySlotContents(i, iContents[i]);
        }
        ItemStack result = CraftingManager.getInstance().findMatchingRecipe(inventory, null);
        if (result == null) {
            return null;
        } else {
            return getIItemStack(result);
        }
    }
    
    public abstract static class ActionBaseRemoveRecipes implements IUndoableAction {
        public void removeRecipes(Set<IRecipe> toRemove ) {
            recipes.removeIf(toRemove::contains);
        }
    }

    public static class ActionRemoveRecipesNoIngredients extends ActionBaseRemoveRecipes implements IUndoableAction {
        // pair of output, nbtMatch
        private final List<Pair<IIngredient, Boolean>> outputs = new ArrayList<>();

        public void addOutput(IIngredient output, @Optional boolean nbtMatch) {
            outputs.add(Pair.of(output, nbtMatch));
        }

        @Override
        public void apply() {
            Set<IRecipe> toRemove = new HashSet<>();

            for (final IRecipe recipe : recipes) {
                final ItemStack recipeOutput = recipe.getRecipeOutput();
                if (recipeOutput != null) {
                    final IItemStack stack = getIItemStack(recipeOutput);
                    if (matches(stack)) {
                        toRemove.add(recipe);
                    }
                }
            }
            super.removeRecipes(toRemove);
        }

        @Override
        public boolean canUndo() {
            return false;
        }

        @Override
        public void undo() {

        }

        @Override
        public String describe() {
            return "Removing recipes for various outputs";
        }

        @Override
        public String describeUndo() {
            return null;
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }

        private boolean matches(IItemStack stack) {
            for(Pair<IIngredient, Boolean> entry : outputs) {
                IIngredient output = entry.getKey();
                Boolean nbtMatch = entry.getValue();
                if(nbtMatch ? output.matchesExact(stack) : output.matches(stack)) {
                    return true;
                }
            }
            return false;
        }

    }
    
    public static class ActionRemoveShapelessRecipes extends ActionBaseRemoveRecipes implements IUndoableAction {
        final IIngredient output;
        final IIngredient[] ingredients;
        final boolean wildcard;

        public ActionRemoveShapelessRecipes(IIngredient output, IIngredient[] ingredients, boolean wildcard) {
            this.output = output;
            this.ingredients = ingredients;
            this.wildcard = wildcard;
        }
        
        @Override
        public void apply() {
            Set<IRecipe> toRemove = new HashSet<>();

            outer:
            for (IRecipe recipe : recipes) {
                if (recipe.getRecipeOutput() == null || !output.matches(new MCItemStack(recipe.getRecipeOutput()))) {
                    continue;
                }
                if (recipe instanceof ShapedRecipes) {
                    continue;
                }
                if (!(recipe instanceof ShapelessRecipes) && !(recipe instanceof ShapelessOreRecipe)) {
                    continue;
                }
                if (ingredients != null) {
                    if (recipe instanceof ShapelessRecipes) {
                        ShapelessRecipes srecipe = (ShapelessRecipes) recipe;

                        if (ingredients.length > srecipe.getRecipeSize()) {
                            continue;
                        } else if (!wildcard && ingredients.length < srecipe.getRecipeSize()) {
                            continue;
                        }

                        checkIngredient:
                        for (IIngredient ingredient : ingredients) {
                            for (int k = 0; k < srecipe.getRecipeSize(); k++) {
                                if (matches(srecipe.recipeItems.get(k), ingredient)) {
                                    continue checkIngredient;
                                }
                            }

                            continue outer;
                        }
                    } else if (recipe instanceof ShapelessOreRecipe) {
                        ShapelessOreRecipe srecipe = (ShapelessOreRecipe) recipe;
                        ArrayList<Object> inputs = srecipe.getInput();

                        if (inputs.size() < ingredients.length) {
                            continue;
                        }
                        if (!wildcard && inputs.size() > ingredients.length) {
                            continue;
                        }

                        checkIngredient:
                        for (IIngredient ingredient : ingredients) {
                            for (int k = 0; k < srecipe.getRecipeSize(); k++) {
                                if (matches(inputs.get(k), ingredient)) {
                                    continue checkIngredient;
                                }
                            }

                            continue outer;
                        }
                    }
                }

                toRemove.add(recipe);
            }
            MineTweakerAPI.logInfo("Removing " + toRemove.size() + " Shapeless recipes.");
            super.removeRecipes(toRemove);            
        }

        @Override
        public boolean canUndo() {
            return false;
        }

        @Override
        public void undo() {

        }

        @Override
        public String describe() {
            if(output != null) {
                return "Removing Shapeless recipes for " + output.toString();
            } else {
                return "Trying to remove recipes for invalid output";
            }
        }

        @Override
        public String describeUndo() {
            return null;
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }
    
    public static class ActionRemoveShapedRecipes extends ActionBaseRemoveRecipes implements IUndoableAction {
        final IIngredient output;
        final IIngredient[][] ingredients;


        public ActionRemoveShapedRecipes(IIngredient output, IIngredient[][] ingredients) {
            this.output = output;
            this.ingredients = ingredients;
        }

        @Override
        public void apply() {
            int ingredientsWidth = 0;
            int ingredientsHeight = 0;

            if (ingredients != null) {
                ingredientsHeight = ingredients.length;

                for (IIngredient[] ingredient : ingredients) {
                    ingredientsWidth = Math.max(ingredientsWidth, ingredient.length);
                }
            }

            final Set<IRecipe> toRemove = new HashSet<>();

            outer:
            for (final IRecipe recipe : recipes) {
                final ItemStack output = recipe.getRecipeOutput();
                if (output == null || !this.output.matches(new MCItemStack(output))) {
                    continue;
                }
                if (!(recipe instanceof ShapedRecipes || recipe instanceof ShapedOreRecipe))
                    continue;
                
                if (ingredients != null) {
                    boolean ore = recipe instanceof ShapedOreRecipe;
                    final ShapedRecipes shapedRecipe = !ore ? (ShapedRecipes) recipe : null;
                    final ShapedOreRecipe shapedOreRecipe = ore ? (ShapedOreRecipe) recipe : null;

                    final int recipeWidth = ore ? MineTweakerHacks.getShapedOreRecipeWidth(shapedOreRecipe) : shapedRecipe.recipeWidth;
                    final int recipeHeight = ore ? shapedOreRecipe.getRecipeSize() / recipeWidth : shapedRecipe.recipeHeight;
                    
                    if (ingredientsWidth != recipeWidth || ingredientsHeight != recipeHeight) {
                        continue;
                    }

                    for (int j = 0; j < ingredientsHeight; j++) {
                        final IIngredient[] row = ingredients[j];
                        for (int k = 0; k < ingredientsWidth; k++) {
                            final IIngredient ingredient = k > row.length ? null : row[k];
                            final Object input = (ore ? shapedOreRecipe.getInput() : shapedRecipe.recipeItems)[j * recipeWidth + k];
                            
                            if (!matches(input, ingredient)) {
                                continue outer;
                            }
                        }
                    }
                }
                // If null ingredient list given, remove all ShapedRecipes with the given output
                toRemove.add(recipe);
            }

            MineTweakerAPI.logInfo(toRemove.size() + " removed");
            super.removeRecipes(toRemove);            
        }

        @Override
        public boolean canUndo() {
            return false;
        }

        @Override
        public void undo() {

        }

        @Override
        public String describe() {
            return null;
        }

        @Override
        public String describeUndo() {
            return null;
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }
    
    public static class ActionBaseAddRecipe implements IUndoableAction {
        private final IRecipe iRecipe;
        private final ICraftingRecipe craftingRecipe;
        
        protected final IItemStack output;
        protected final boolean isShaped;

        private ActionBaseAddRecipe(ICraftingRecipe craftingRecipe, IItemStack output, boolean isShaped) {
            this.iRecipe = RecipeConverter.convert(craftingRecipe);
            this.craftingRecipe = craftingRecipe;
            
            this.output = output;
            this.isShaped = isShaped;
            
//            if(craftingRecipe.hasTransformers())
//                transformerRecipes.add(craftingRecipe);
        }

        @Override
        public void apply() {
            recipes.add(iRecipe);
            if (craftingRecipe.hasTransformers())
                transformerRecipes.add(craftingRecipe);
        }

        @Override
        public boolean canUndo() {
            return false;
        }

        @Override
        public void undo() {

        }

        @Override
        public String describe() {
            if(output != null) {
                return "Adding " + (isShaped ? "shaped" : "shapeless") + " recipe for " + output.getDisplayName();
            } else {
                return "Trying to add " + (isShaped ? "shaped" : "shapeless") + "recipe without correct output";
            }
        }

        @Override
        public String describeUndo() {
            return null;
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }

    private static class ActionAddShapedRecipe extends ActionBaseAddRecipe {
        public ActionAddShapedRecipe(IItemStack output, IIngredient[][] ingredients, IRecipeFunction function, IRecipeAction action, boolean mirrored) {
            super(new ShapedRecipe(output, ingredients, function, action, mirrored), output, true);
        }
    }
    
    private static class ActionAddShapelessRecipe extends ActionBaseAddRecipe {
        public ActionAddShapelessRecipe(IItemStack output, IIngredient[] ingredients, IRecipeFunction function, IRecipeAction action) {
            super(new ShapelessRecipe(output, ingredients, function, action), output, false);
        }
    }

    public static class HandlePostReload implements IEventHandler<MineTweakerImplementationAPI.ReloadEvent>
    {
        @Override
        public void handle(MineTweakerImplementationAPI.ReloadEvent event)
        {
            System.out.println("MineTweaker: Applying additions and removals");
            MineTweakerAPI.apply(MCRecipeManager.actionRemoveRecipesNoIngredients);
            MCRecipeManager.recipesToRemove.forEach(MineTweakerAPI::apply);
            MCRecipeManager.recipesToAdd.forEach(MineTweakerAPI::apply);
            
        }
    }

    private class ContainerVirtual extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer var1) {
            return false;
        }
    }
    
}
