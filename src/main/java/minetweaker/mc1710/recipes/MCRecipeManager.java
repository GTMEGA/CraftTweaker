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
import minetweaker.mc1710.util.ThreadSafeBitSet;
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

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

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
    public static final List<IRecipe> recipesToUndo = new ArrayList<>();

    public static List<IRecipe> recipes;
    public static final List<ICraftingRecipe> transformerRecipes = new ArrayList<>();

    public MCRecipeManager() {
        MineTweakerImplementationAPI.onPostReload(new HandleLateAdditionsAndRemovals());
        MineTweakerImplementationAPI.onRollbackEvent(new HandleLateAdditionsAndRemovals());
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
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients, IRecipeAction action) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, null, action, true));
    }

    @Override
    public void addShapedMirrored(IItemStack output, IIngredient[][] ingredients, IRecipeFunction function, IRecipeAction action) {
        recipesToAdd.add(new ActionAddShapedRecipe(output, ingredients, function, action, true));
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
    public int removeShaped(IIngredient output) {
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
        return removeShapeless(output, ingredients, false);
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
        public abstract boolean matches(IRecipe r);
        public abstract Set<IRecipe> find();
        public void removeRecipes(Set<IRecipe> toRemove ) {
            recipes.removeIf(toRemove::contains);
        }
    }

    public static class ActionRemoveRecipesNoIngredients extends ActionBaseRemoveRecipes implements IUndoableAction {
        // pair of output, nbtMatch
        private final List<Pair<IIngredient, Boolean>> outputs = new ArrayList<>();
        private Set<IRecipe> toRemove = new HashSet<>();

        public void addOutput(IIngredient output, @Optional boolean nbtMatch) {
            outputs.add(Pair.of(output, nbtMatch));
        }

        public void clearOutputs() {
            outputs.clear();
        }

        @Override
        public boolean matches(IRecipe r) {
            ItemStack recipeOutput = r.getRecipeOutput();
            return recipeOutput != null && matches(getIItemStack(recipeOutput));
        }

        @Override
        public Set<IRecipe> find() {
            return recipes.parallelStream()
                    .filter(r -> {
                        ItemStack recipeOutput = r.getRecipeOutput();
                        return recipeOutput != null && matches(getIItemStack(recipeOutput));
                    }).collect(Collectors.toSet());
        }

        @Override
        public void apply() {
            toRemove = find();

            removeRecipes(toRemove);
        }

        @Override
        public boolean canUndo() {
            return !toRemove.isEmpty();
        }

        @Override
        public void undo() {
            for(IRecipe recipe: toRemove) {
                recipesToAdd.add(new ActionBaseAddRecipe(recipe));
            }
        }

        @Override
        public String describe() {
            return "Removing recipes for various outputs";
        }

        @Override
        public String describeUndo() {
            return "Trying to restore " + toRemove.size() + " recipes.";
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
        Set<IRecipe> toRemove = new HashSet<>();

        public ActionRemoveShapelessRecipes(IIngredient output, IIngredient[] ingredients, boolean wildcard) {
            this.output = output;
            this.ingredients = ingredients;
            this.wildcard = wildcard;
        }

        @Override
        public boolean matches(IRecipe recipe) {
            if (recipe.getRecipeOutput() == null || !output.matches(new MCItemStack(recipe.getRecipeOutput()))) {
                return false;
            }
            if (recipe instanceof ShapedRecipes) {
                return false;
            }
            if (!(recipe instanceof ShapelessRecipes) && !(recipe instanceof ShapelessOreRecipe)) {
                return false;
            }
            if (ingredients != null) {
                if (recipe instanceof ShapelessRecipes) {
                    ShapelessRecipes srecipe = (ShapelessRecipes) recipe;

                    if (ingredients.length > srecipe.getRecipeSize()) {
                        return false;
                    } else if (!wildcard && ingredients.length < srecipe.getRecipeSize()) {
                        return false;
                    }

                    checkIngredient:
                    for (IIngredient ingredient : ingredients) {
                        for (int k = 0; k < srecipe.getRecipeSize(); k++) {
                            if (MCRecipeManager.matches(srecipe.recipeItems.get(k), ingredient)) {
                                continue checkIngredient;
                            }
                        }

                        return false;
                    }
                } else if (recipe instanceof ShapelessOreRecipe) {
                    ShapelessOreRecipe srecipe = (ShapelessOreRecipe) recipe;
                    ArrayList<Object> inputs = srecipe.getInput();

                    if (inputs.size() < ingredients.length) {
                        return false;
                    }
                    if (!wildcard && inputs.size() > ingredients.length) {
                        return false;
                    }

                    checkIngredient:
                    for (IIngredient ingredient : ingredients) {
                        for (int k = 0; k < srecipe.getRecipeSize(); k++) {
                            if (MCRecipeManager.matches(inputs.get(k), ingredient)) {
                                continue checkIngredient;
                            }
                        }

                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public Set<IRecipe> find() {
            Set<IRecipe> toRemove = new HashSet<>();
            for (IRecipe recipe : recipes) {
                if (matches(recipe))
                    toRemove.add(recipe);
            }
            return toRemove;
        }

        @Override
        public void apply() {
            toRemove = find();
            MineTweakerAPI.logInfo("Removing " + toRemove.size() + " Shapeless recipes.");
            super.removeRecipes(toRemove);
        }

        @Override
        public boolean canUndo() {
            return !toRemove.isEmpty();
        }

        @Override
        public void undo() {
            for(IRecipe recipe: toRemove) {
                recipesToAdd.add(new ActionBaseAddRecipe(recipe));
            }
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
            return "Trying to restore " + toRemove.size() + " shapeless recipes.";
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }

    public static class ActionRemoveShapedRecipes extends ActionBaseRemoveRecipes implements IUndoableAction {
        final IIngredient output;
        final IIngredient[][] ingredients;
        final int ingredientsWidth;
        final int ingredientsHeight;
        Set<IRecipe> toRemove = new HashSet<>();



        public ActionRemoveShapedRecipes(IIngredient output, IIngredient[][] ingredients) {
            this.output = output;
            this.ingredients = ingredients;
            int ingredientsWidth = 0;
            int ingredientsHeight = 0;

            if (ingredients != null) {
                ingredientsHeight = ingredients.length;

                for (IIngredient[] ingredient : ingredients) {
                    ingredientsWidth = Math.max(ingredientsWidth, ingredient.length);
                }
            }
            this.ingredientsWidth = ingredientsWidth;
            this.ingredientsHeight = ingredientsHeight;
        }

        @Override
        public boolean matches(IRecipe recipe) {
            final ItemStack output = recipe.getRecipeOutput();
            if (output == null || !this.output.matches(new MCItemStack(output))) {
                return false;
            }
            if (!(recipe instanceof ShapedRecipes || recipe instanceof ShapedOreRecipe))
                return false;

            if (ingredients != null) {
                boolean ore = recipe instanceof ShapedOreRecipe;
                final ShapedRecipes shapedRecipe = !ore ? (ShapedRecipes) recipe : null;
                final ShapedOreRecipe shapedOreRecipe = ore ? (ShapedOreRecipe) recipe : null;

                final int recipeWidth = ore ? MineTweakerHacks.getShapedOreRecipeWidth(shapedOreRecipe) : shapedRecipe.recipeWidth;
                final int recipeHeight = ore ? shapedOreRecipe.getRecipeSize() / recipeWidth : shapedRecipe.recipeHeight;

                if (ingredientsWidth != recipeWidth || ingredientsHeight != recipeHeight) {
                    return false;
                }

                for (int j = 0; j < ingredientsHeight; j++) {
                    final IIngredient[] row = ingredients[j];
                    for (int k = 0; k < ingredientsWidth; k++) {
                        final IIngredient ingredient = k > row.length ? null : row[k];
                        final Object input = (ore ? shapedOreRecipe.getInput() : shapedRecipe.recipeItems)[j * recipeWidth + k];

                        if (!MCRecipeManager.matches(input, ingredient)) {
                            return false;
                        }
                    }
                }
            }
            // If null ingredient list given, remove all ShapedRecipes with the given output
            return true;
        }

        @Override
        public Set<IRecipe> find() {
            Set<IRecipe> toRemove = new HashSet<>();

            for (final IRecipe recipe : recipes) {
                if (matches(recipe))
                    toRemove.add(recipe);
            }
            return toRemove;
        }

        @Override
        public void apply() {
            toRemove = find();

            MineTweakerAPI.logInfo(toRemove.size() + " removed");
            super.removeRecipes(toRemove);
        }

        @Override
        public boolean canUndo() {
            return !toRemove.isEmpty();
        }

        @Override
        public void undo() {
            for(IRecipe recipe: toRemove) {
                recipesToAdd.add(new ActionBaseAddRecipe(recipe));
            }
        }

        @Override
        public String describe() {
            if(output != null) {
                return "Removing Shaped recipes for " + output.toString();
            } else {
                return "Trying to remove recipes for invalid output";
            }
        }

        @Override
        public String describeUndo() {
            return "Trying to restore " + toRemove.size() + " shaped recipes.";
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
        protected final boolean isRestoring;

        private ActionBaseAddRecipe(IRecipe iRecipe) {
            this.iRecipe = iRecipe;
            this.craftingRecipe = null;
            this.isRestoring = true;

            this.isShaped = iRecipe instanceof ShapedRecipes;
            this.output = new MCItemStack(iRecipe.getRecipeOutput());
        }

        private ActionBaseAddRecipe(ICraftingRecipe craftingRecipe, IItemStack output, boolean isShaped) {
            this.iRecipe = RecipeConverter.convert(craftingRecipe);
            this.craftingRecipe = craftingRecipe;
            this.isRestoring = false;

            this.output = output;
            this.isShaped = isShaped;

        }

        @Override
        public void apply() {
            recipes.add(iRecipe);
            if (craftingRecipe != null && craftingRecipe.hasTransformers())
                transformerRecipes.add(craftingRecipe);
        }

        @Override
        public boolean canUndo() {
            return iRecipe != null;
        }

        @Override
        public void undo() {
            recipesToUndo.add(iRecipe);
        }

        @Override
        public String describe() {

            if(output != null) {
                return "" + (this.isRestoring ? "Restoring" : "Adding ") + (isShaped ? "shaped" : "shapeless") + " recipe for " + output.getDisplayName();
            } else {
                return "Trying to add " + (isShaped ? "shaped" : "shapeless") + "recipe without correct output";
            }
        }

        @Override
        public String describeUndo() {
            return "Undoing addition of " + (output != null ? output.getDisplayName() : "invalid output");
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

    private static class ActionBatchRemoveRecipe implements IUndoableAction {
        private final Set<IRecipe> recipes;

        private ActionBatchRemoveRecipe(Set<IRecipe> recipes) {
            this.recipes = recipes;
        }

        @Override
        public void apply() {
            System.out.println("Removing " + recipes.size() + " recipes");
            MCRecipeManager.recipes.removeIf(recipes::contains);
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public void undo() {
            recipesToAdd.addAll(recipes.parallelStream().map(ActionBaseAddRecipe::new).collect(Collectors.toList()));
        }

        @Override
        public String describe() {
            return "Batch Remove " + recipes.size() + " recipes";
        }

        @Override
        public String describeUndo() {
            return "Add back " + recipes.size() + "recipes";
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }

    public static void applyAdditionsAndRemovals() {
        System.out.println("MineTweaker: Applying additions and removals");
        MineTweakerAPI.apply(MCRecipeManager.actionRemoveRecipesNoIngredients);
        if(recipesToUndo.size() > 0) {
            recipes.removeIf(recipesToUndo::contains);
        }
        int commonPoolParallelism = getCommonPoolParallelism();
        if (commonPoolParallelism <= 1) {
            // if thread == 1, then we have parallel overhead but doesn't have parallel speedup.
            // never do this
            recipesToRemove.forEach(ActionBaseRemoveRecipes::apply);
        } else
            try {
                ThreadSafeBitSet removed = new ThreadSafeBitSet();
                new FindRecipeToRemoveTask(removed, recipesToRemove).fork().get();
                MineTweakerAPI.apply(new ActionBatchRemoveRecipe(removed.toBitSet().stream().mapToObj(recipes::get).collect(Collectors.toSet())));
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("MineTweaker: Parallel Removal failed. Falling back to sequential remove");
                e.printStackTrace();
                // try it sequentially
                recipesToRemove.forEach(ActionBaseRemoveRecipes::apply);
            }
        MCRecipeManager.recipesToAdd.forEach(MineTweakerAPI::apply);

        actionRemoveRecipesNoIngredients.clearOutputs();
        recipesToRemove.clear();;
        recipesToAdd.clear();
        recipesToUndo.clear();
    }

    private static int getCommonPoolParallelism() {
        return Integer.getInteger("java.util.concurrent.ForkJoinPool.common.parallelism", Runtime.getRuntime().availableProcessors() - 1);
    }

    public static class HandleLateAdditionsAndRemovals implements IEventHandler<MineTweakerImplementationAPI.ReloadEvent>
    {
        @Override
        public void handle(MineTweakerImplementationAPI.ReloadEvent event)
        {
            MCRecipeManager.applyAdditionsAndRemovals();
        }
    }

    private class ContainerVirtual extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer var1) {
            return false;
        }
    }

    /**
     * A ForkJoinTask to find recipes to remove. Slightly more performant than parallelStream().
     */
    private static class FindRecipeToRemoveTask extends RecursiveAction {

        private final ThreadSafeBitSet removed;
        private final List<ActionBaseRemoveRecipes> workList;

        public FindRecipeToRemoveTask(ThreadSafeBitSet removed, List<ActionBaseRemoveRecipes> workList) {
            this.removed = removed;
            this.workList = workList;
        }

        @Override
        protected void compute() {
            if (workList.isEmpty()) return;
            if (workList.size() < 50) {
                for (int i = 0; i < recipes.size(); i++) {
                    if (removed.get(i)) continue;
                    IRecipe recipe = recipes.get(i);
                    for (ActionBaseRemoveRecipes r : workList) {
                        if (r.matches(recipe)) {
                            removed.set(i);
                            break;
                        }
                    }
                }
            } else {
                FindRecipeToRemoveTask left = new FindRecipeToRemoveTask(removed, workList.subList(0, workList.size() / 2));
                left.fork();
                new FindRecipeToRemoveTask(removed, workList.subList(workList.size() / 2, workList.size())).fork().join();
                left.join();
            }
        }
    }
}
