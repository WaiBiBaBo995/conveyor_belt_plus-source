package pureneko.conveyor_belt_plus.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade discovers plugins on both sides; keep all client bytecode in the delegate. */
@WailaPlugin
public final class ConveyorJadePlugin implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registration) {
        ConveyorJadeClient.register(registration);
    }
}
