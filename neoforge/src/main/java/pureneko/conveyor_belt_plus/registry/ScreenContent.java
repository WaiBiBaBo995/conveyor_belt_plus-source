package pureneko.conveyor_belt_plus.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;

public final class ScreenContent {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<MenuType<ChuteScreenHandler>> CHUTE = MENUS.register(
            "chute", () -> IMenuTypeExtension.create(ChuteScreenHandler::new));

    private ScreenContent() {
    }
}
