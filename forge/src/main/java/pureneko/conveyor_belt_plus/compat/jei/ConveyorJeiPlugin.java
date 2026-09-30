package pureneko.conveyor_belt_plus.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import pureneko.conveyor_belt_plus.registry.ConveyorBeltPlus;
import pureneko.conveyor_belt_plus.client.screens.ChuteScreen;
import pureneko.conveyor_belt_plus.client.screens.ChuteScreen.GhostTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Loaded by JEI's discovery only; core screens and dedicated servers never reference JEI classes. */
@JeiPlugin
public final class ConveyorJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return ConveyorBeltPlus.id("chute_ghost_filters"); }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(ChuteScreen.class, new mezz.jei.api.gui.handlers.IGuiContainerHandler<ChuteScreen>() {
            @Override public List<Rect2i> getGuiExtraAreas(ChuteScreen screen) { return screen.extraAreas(); }
        });
        registration.addGhostIngredientHandler(ChuteScreen.class, new IGhostIngredientHandler<ChuteScreen>() {
            @Override public <I> List<Target<I>> getTargetsTyped(ChuteScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
                var stack = ingredient.getItemStack();
                var fluid = ingredient.getIngredient() instanceof net.minecraftforge.fluids.FluidStack value ? value.copy() : null;
                if (fluid != null && !screen.isFluidTab() || fluid == null && stack.isEmpty()) return List.of();
                if (screen.isFluidTab() && fluid == null && pureneko.conveyor_belt_plus.util.FluidPackets.marker(stack.get()).isEmpty()) return List.of();
                var targets = new ArrayList<Target<I>>();
                for (var target : screen.ghostTargets()) targets.add(new Target<>() {
                    @Override public Rect2i getArea() { return target.area(); }
                    @Override public void accept(I ignored) {
                        if (fluid != null) screen.acceptFluidGhost(target.slot(), fluid);
                        else screen.acceptGhost(target.slot(), stack.get().copyWithCount(1));
                    }
                });
                return targets;
            }
            @Override public void onComplete() {}
        });
    }
}
