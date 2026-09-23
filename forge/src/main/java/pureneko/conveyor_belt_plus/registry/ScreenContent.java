package pureneko.conveyor_belt_plus.registry;

import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.registry.RegistryKeys;
import pureneko.conveyor_belt_plus.screen.ChuteScreenHandler;

public final class ScreenContent {

    public static final DeferredRegister<ScreenHandlerType<?>> MENUS =
            DeferredRegister.create(RegistryKeys.SCREEN_HANDLER, ConveyorBeltPlus.MOD_ID);

    public static final Supplier<ScreenHandlerType<ChuteScreenHandler>> CHUTE = MENUS.register(
            "chute", () -> IForgeMenuType.create(ChuteScreenHandler::new));

    private ScreenContent() {
    }
}
