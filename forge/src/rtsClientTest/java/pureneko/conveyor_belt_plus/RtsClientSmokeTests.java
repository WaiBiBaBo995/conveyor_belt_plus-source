package pureneko.conveyor_belt_plus;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.registry.*;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.client.renderers.*;
import pureneko.conveyor_belt_plus.compat.rts.RtsClient;
import pureneko.conveyor_belt_plus.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=ConveyorBeltPlus.MOD_ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RtsClientSmokeTests {
    private static final BlockPos SOURCE=new BlockPos(0,120,0);
    private static int phase=-1,ticks,total;private static Vec3 origin,direction;
    private static long rendered,preview;private static ItemStack selected=ItemStack.EMPTY;
    public static void begin(Minecraft client){
        phase=0;client.options.keyShift.setDown(false);
        client.getSingleplayerServer().execute(()->{
            var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            com.rtsbuilding.rtsbuilding.Config.ENABLE_SURVIVAL_PROGRESSION.set(false);
            var source=(ChuteBlockEntity)player.serverLevel().getBlockEntity(SOURCE);
            var queue=source.pickupAccess(Direction.UP).items();queue.clear();
            queue.add(new ChuteBlockEntity.BeltItem(.25f,201,new ItemStack(Items.DIAMOND,16)));
            source.setChanged();player.serverLevel().getChunkSource().blockChanged(SOURCE);
            player.teleportTo(player.serverLevel(),.5,125,8.5,180,65);
            RtsCameraManager.start(player,false);
        });
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(phase<0)return;
        var client=Minecraft.getInstance();if(++total>1600)throw new AssertionError("RTS client timeout phase="+phase+" view="+RtsClient.view());
        ticks++;
        if(client.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen){
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(),client.getWindow().getScreenWidth()*.4,client.getWindow().getScreenHeight()*.4);
        }
        if(phase==1)aim(client,201);else if(phase==2)aim(client,202);
        if(phase==3 && client.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen screen){
            // Shift also moves the real RTS camera. Freeze the cursor target, never an obsolete camera origin.
            RtsPlacementRayFreeze.clear();origin=screen.currentRayOrigin();
            var source=(ChuteBlockEntity)client.level.getBlockEntity(SOURCE);
            direction=SplineUtil.getPositionOnSpline(source.getBeltData(),.45).add(0,0,BeltGeometry.LIFT_SURFACE_OFFSET_Z).subtract(origin).normalize();
            RtsPlacementRayFreeze.freeze(origin,direction);
        }
        if(phase==3 && ticks%10==3 && !net.minecraft.client.gui.screens.Screen.hasShiftDown()){focus(client);nativeShift(true);}
        try {
            if(phase==0 && ticks>40 && client.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen){
                var controller=ClientRtsController.get();controller.setSmoothCamera(false);controller.setMode(BuilderMode.INTERACT);controller.selectEmptyHand();
                client.options.hideGui=true;
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getWindow(),client.getWindow().getScreenWidth()*.5,client.getWindow().getScreenHeight()*.35);
                aim(client,201);rendered=BeltCargoOutline.renderedFrames();next();
            } else if(phase==1 && ticks>25 && RtsClient.view()!=null && BeltPickupTarget.canHighlight() && BeltCargoOutline.renderedFrames()>rendered){
                require(RtsClient.view()!=null && BeltPickupTarget.canHighlight() && BeltCargoOutline.renderedFrames()>rendered,"RTS empty-hand cargo silhouette");
                screenshot(client,"11-rts-item-outline.png");
                selected=new ItemStack(Items.BUCKET);ClientRtsController.get().selectItemForPlacement("minecraft:bucket","Bucket",selected);
                client.getSingleplayerServer().execute(()->{
                    var world=client.getSingleplayerServer().overworld();var source=(ChuteBlockEntity)world.getBlockEntity(SOURCE);
                    var queue=source.pickupAccess(Direction.UP).items();queue.clear();
                    queue.add(new ChuteBlockEntity.BeltItem(.25f,202,FluidPackets.create(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000))));
                    source.setChanged();world.getChunkSource().blockChanged(SOURCE);
                });
                rendered=BeltCargoOutline.renderedFrames();next();
            } else if(phase==2 && ticks>25 && BeltPickupTarget.canHighlight() && BeltCargoOutline.renderedFrames()>rendered){
                require(BeltPickupTarget.canHighlight() && BeltCargoOutline.renderedFrames()>rendered,"RTS warehouse fluid-container outline");
                screenshot(client,"12-rts-fluid-outline.png");
                selected=new ItemStack(ItemContent.ULTIMATE_BELT.get());ClientRtsController.get().selectItemForPlacement("conveyor_belt_plus:ultimate_belt","Belt",selected);
                var source=(ChuteBlockEntity)client.level.getBlockEntity(SOURCE);
                origin=RtsClient.view().origin();var point=SplineUtil.getPositionOnSpline(source.getBeltData(),.45).add(0,0,BeltGeometry.LIFT_SURFACE_OFFSET_Z);
                direction=point.subtract(origin).normalize();RtsPlacementRayFreeze.freeze(origin,direction);
                preview=BeltOutlineRenderer.upgradeFrames();
                org.lwjgl.glfw.GLFW.glfwFocusWindow(client.getWindow().getWindow());
                focus(client);
                next();
            } else if(phase==3 && ticks>30 && net.minecraft.client.gui.screens.Screen.hasShiftDown() && BeltPickupTarget.currentUpgrade()!=null && BeltOutlineRenderer.upgradeFrames()>preview){
                var view=RtsClient.view();require(view!=null && view.stack().is(ItemContent.ULTIMATE_BELT.get()),"RTS selected belt available");
                require(net.minecraft.client.gui.screens.Screen.hasShiftDown() && BeltPickupTarget.currentUpgrade()!=null
                        && BeltOutlineRenderer.upgradeFrames()>preview,"RTS actual Shift and selected-material flashing projection");
                screenshot(client,"13-rts-upgrade-projection.png");
                var click=new net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre(client.screen,client.mouseHandler.xpos(),client.mouseHandler.ypos(),1);
                RtsClient.click(click);require(click.isCanceled(),"RTS right-click handler consumes body upgrade");
                nativeShift(false);
                next();
            } else if(phase==4 && ticks>35 && ((ChuteBlockEntity)client.level.getBlockEntity(SOURCE)).getBeltTier()==3){
                require(((ChuteBlockEntity)client.level.getBlockEntity(SOURCE)).getBeltTier()==3,"RTS selected-material body packet upgrades route");
                screenshot(client,"14-rts-upgraded-lift.png");
                java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("rts-smoke-success.txt"),"Actual RTS BuilderScreen cargo/fluid silhouette, selected material ray, native Shift upgrade projection and right-click body upgrade passed.\n");
                RtsPlacementRayFreeze.clear();client.options.hideGui=false;client.stop();phase=-1;
            }
        }catch(Throwable error){
            try{nativeShift(false);}catch(Exception ignored){}
            try{screenshot(client,"failure-rts-"+phase+".png");}catch(Exception ignored){}
            try{for(var name:new String[]{"eye","end","nearest","remote","frameTime","owner","insertionOwner","heldForInsertion"}){
                var field=BeltPickupTarget.class.getDeclaredField(name);field.setAccessible(true);System.err.println("RTS diagnostic "+name+"="+field.get(null));
            }}catch(Exception ignored){}
            System.err.println("RTS shift="+net.minecraft.client.gui.screens.Screen.hasShiftDown()+" upgrade="+BeltPickupTarget.currentUpgrade()+" frames="+BeltOutlineRenderer.upgradeFrames());
            System.err.println("RTS screen="+client.screen+" mouse="+client.mouseHandler.xpos()+","+client.mouseHandler.ypos()+" mode="+ClientRtsController.get().getMode());
            System.err.println("RTS hit="+(RtsClient.view()==null?null:RtsClient.view().hit().getBlockPos())+" type="+(RtsClient.view()==null?null:RtsClient.view().hit().getType()));
            throw new RuntimeException("RTS client phase="+phase+" view="+RtsClient.view()+" target="+BeltPickupTarget.current(),error);}
    }
@net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void pinCursor(net.minecraftforge.client.event.RenderLevelStageEvent event){
        if(phase<0)return;
        var client=Minecraft.getInstance();
        try{for(var name:new String[]{"xpos","ypos"}){
            var field=net.minecraft.client.MouseHandler.class.getDeclaredField(name);field.setAccessible(true);
            field.setDouble(client.mouseHandler,(name.equals("xpos")?client.getWindow().getScreenWidth():client.getWindow().getScreenHeight())*.4);
        }}catch(Exception error){throw new RuntimeException(error);}
    }
    private static void aim(Minecraft client,long id){
        var source=(ChuteBlockEntity)client.level.getBlockEntity(SOURCE);
        var packet=source.pickupAccess(Direction.UP).items().stream().filter(p->p.id==id).findFirst().orElse(null);
        if(packet==null)return;
        origin=((com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen)client.screen).currentRayOrigin();
        var point=SplineUtil.getPositionOnSpline(source.getBeltData(),packet.renderedProgress(source.getVisualState().presentationTick(BeltRenderClock.now(client.level)))).add(0,.18,BeltGeometry.LIFT_CARGO_OFFSET_Z);
        direction=point.subtract(origin).normalize();RtsPlacementRayFreeze.freeze(origin,direction);
    }
    // The game forces AWT headless mode. Use Windows' normal input queue for this opt-in desktop smoke fixture.
    private static void focus(Minecraft client){
        var handle=new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer.createConstant(
                org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(client.getWindow().getWindow())));
        com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(handle);
    }
    private static void nativeShift(boolean down){
        var input=new com.sun.jna.platform.win32.WinUser.INPUT();
        input.type=new com.sun.jna.platform.win32.WinDef.DWORD(com.sun.jna.platform.win32.WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");
        input.input.ki.wVk=new com.sun.jna.platform.win32.WinDef.WORD(0);
        input.input.ki.wScan=new com.sun.jna.platform.win32.WinDef.WORD(0x2A);
        input.input.ki.dwFlags=new com.sun.jna.platform.win32.WinDef.DWORD(com.sun.jna.platform.win32.WinUser.KEYBDINPUT.KEYEVENTF_SCANCODE | (down?0:com.sun.jna.platform.win32.WinUser.KEYBDINPUT.KEYEVENTF_KEYUP));
        com.sun.jna.platform.win32.User32.INSTANCE.SendInput(new com.sun.jna.platform.win32.WinDef.DWORD(1),
                new com.sun.jna.platform.win32.WinUser.INPUT[]{input},input.size());
    }
    private static void next(){phase++;ticks=0;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void screenshot(Minecraft client,String name)throws java.io.IOException{
        var path=client.gameDirectory.toPath().resolve("vertical-screenshots");
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget())){image.writeToFile(path.resolve(name));}
    }
}
