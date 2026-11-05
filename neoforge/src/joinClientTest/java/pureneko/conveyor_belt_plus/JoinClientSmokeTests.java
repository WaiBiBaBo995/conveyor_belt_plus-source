package pureneko.conveyor_belt_plus;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import pureneko.conveyor_belt_plus.registry.*;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.client.renderers.ChuteBeltRenderer;
import pureneko.conveyor_belt_plus.compat.rts.RtsClient;
import pureneko.conveyor_belt_plus.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid=ConveyorBeltPlus.MOD_ID,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class JoinClientSmokeTests {
    private static final BlockPos LIFT=new BlockPos(0,120,0), START=new BlockPos(3,120,3), END=START.east(6);
    private static int phase,ticks,total;private static volatile boolean ready;
    private static org.joml.Vector3f look;
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {

        var client=Minecraft.getInstance();if(++total>1200)throw new AssertionError("Join smoke timeout phase="+phase+" view="+RtsClient.view());ticks++;
        client.options.pauseOnLostFocus=false;
        if(phase==2 && ready && client.player!=null){
            client.mouseHandler.releaseMouse();client.player.setPos(.5,127,4);client.player.setDeltaMovement(Vec3.ZERO);
            client.player.setYRot(180);client.player.setXRot(0);client.player.yRotO=180;client.player.xRotO=0;
        }
        try {
            if(phase==0 && ticks==20)client.setScreen(new TitleScreen());
            if(phase==0 && ticks>30 && client.screen instanceof TitleScreen){client.createWorldOpenFlows().openWorld("JoinSmoke",()->client.setScreen(new TitleScreen()));next();}
            else if(phase==1 && client.player!=null && client.level!=null){
                client.getSingleplayerServer().execute(()->{
                    var world=client.getSingleplayerServer().overworld();var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    com.rtsbuilding.rtsbuilding.Config.ENABLE_SURVIVAL_PROGRESSION.set(false);player.setGameMode(GameType.CREATIVE);
                    for(int x=-3;x<13;x++)for(int z=-3;z<13;z++){
                        world.setBlockAndUpdate(new BlockPos(x,118,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        for(int y=119;y<131;y++)world.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    world.setBlockAndUpdate(LIFT,BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.UP));
                    world.setBlockAndUpdate(LIFT.above(8),BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.DOWN));
                    var source=(ChuteBlockEntity)world.getBlockEntity(LIFT);require(source.connectOutgoing(Direction.UP,LIFT.above(8),Direction.DOWN,java.util.List.of(),2),"lift fixture");
                    source.pickupAccess(Direction.UP).items().add(new ChuteBlockEntity.BeltItem(.5f,901,new ItemStack(Items.DIAMOND,16)));
                    world.setBlockAndUpdate(START,BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.EAST));
                    world.setBlockAndUpdate(END,BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.WEST));
                    player.teleportTo(world,.5,127,4,180,0);ready=true;
                });next();
            }else if(phase==2 && ready && ticks>40 && client.level.getBlockEntity(LIFT) instanceof ChuteBlockEntity source && source.getBeltData()!=null){
                geometry(client,source.getBeltData(),LIFT);screenshot(client,"01-vertical-upper-join.png");
                client.getSingleplayerServer().execute(()->{
                    var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.teleportTo(player.serverLevel(),6.5,122,11.5,180,60);RtsCameraManager.start(player,false);
                });next();
            }else if(phase==3 && ticks>35 && client.screen instanceof BuilderScreen){
                ClientRtsController.get().setSmoothCamera(false);ClientRtsController.get().setMode(BuilderMode.INTERACT);
                ClientRtsController.get().selectItemForPlacement("conveyor_belt_plus:advanced_belt","Belt",new ItemStack(ItemContent.ADVANCED_BELT.get()));
                pin(client);aim(client,START);var press=press(client);require(press.isCanceled(),"first endpoint retains immediate press behavior");next();
            }else if(phase==4 && ticks>20 && RtsClient.view()!=null && START.equals(RtsClient.view().stack().get(ComponentContent.BELT_START.get()))){
                pin(client);aim(client,END);var press=press(client);require(!press.isCanceled(),"second press reaches RTS camera");
                require(client.screen.mouseClicked(mouseX(client),mouseY(client),1),"native screen begins right gesture");
                require(camera(client).isRightPressActive(),"native right gesture active");
                look=new org.joml.Vector3f(client.gameRenderer.getMainCamera().getLookVector());
                RtsPlacementRayFreeze.clear();client.screen.mouseDragged(mouseX(client)+20,mouseY(client),1,20,0);
                require(camera(client).isRightDragRotated(),"native drag threshold reached");next();
            }else if(phase==5 && ticks>20){
                require(look.distanceSquared(client.gameRenderer.getMainCamera().getLookVector())>.00001,"RTS camera actually rotates while drafting");
                release(client);require(!camera(client).isRightPressActive(),"release clears native gesture");next();
            }else if(phase==6 && ticks>20){
                require(((ChuteBlockEntity)client.level.getBlockEntity(START)).getBeltData()==null,"drag release never connects endpoint");
                pin(client);aim(client,END);require(START.equals(RtsClient.view().stack().get(ComponentContent.BELT_START.get())),"drag preserves draft");
                require(!press(client).isCanceled(),"stationary second press deferred");client.screen.mouseClicked(mouseX(client),mouseY(client),1);next();
            }else if(phase==7 && ticks>25){
                require(((ChuteBlockEntity)client.level.getBlockEntity(START)).getBeltData()==null,"holding without release does not connect");
                pin(client);aim(client,END);release(client);next();
            }else if(phase==8 && ticks>20 && END.equals(((ChuteBlockEntity)client.level.getBlockEntity(START)).getTarget())){
                require(RtsClient.view().stack().get(ComponentContent.BELT_START.get())==null,"completed connection clears draft once");
                screenshot(client,"02-rts-single-click-connected.png");RtsPlacementRayFreeze.clear();
                java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("join-smoke-success.txt"),"passed: vertical UP/DOWN mesh contact, carrier/hit alignment; actual RTS camera drag preserves draft and does not connect; stationary hold connects only on release; first endpoint remains immediate.\n");
                phase=9;client.stop();
            }
        }catch(Throwable error){throw new RuntimeException("Join smoke phase="+phase+" view="+RtsClient.view(),error);}
    }
    private static void geometry(Minecraft client,ChuteBlockEntity.BeltData data,BlockPos origin){
        for(boolean up:new boolean[]{true,false}){
            var from=up?origin:origin.above(8);var to=up?origin.above(8):origin;
            var lift=ChuteBlockEntity.BeltData.create(client.level,from,up?Direction.UP:Direction.DOWN,to,up?Direction.DOWN:Direction.UP,java.util.List.of());
            require(Math.abs(lift.totalLength()-9)<1e-6,"rotated connection planes retain full length");
            double minY=100,maxY=-100,minZ=100,maxZ=-100;
            for(var quad:ChuteBeltRenderer.createSplineModel(lift,from))for(var v:java.util.List.of(quad.a(),quad.b(),quad.c(),quad.d())){
                minY=Math.min(minY,v.y()+from.getY()-origin.getY());maxY=Math.max(maxY,v.y()+from.getY()-origin.getY());minZ=Math.min(minZ,v.z());maxZ=Math.max(maxZ,v.z());
            }
            require(Math.abs(minY)<1e-6 && Math.abs(maxY-9)<1e-6,"both mesh ends touch interface connection planes");
            require(Math.abs(minZ-.18)<1e-6 && Math.abs(maxZ-.33)<1e-6,"surface and skirt match rotated horizontal interface");
            var point=SplineUtil.getPositionOnSpline(lift,.5);var cargo=BeltGeometry.cargoPoint(lift,point);
            require(Math.abs(cargo.z-.30-BeltGeometry.surfacePoint(lift,point).z)<1e-6,"carrier rear touches band");
            require(lift.interactionPath().raycast(point.add(0,0,2),point.add(0,0,-2))!=null,"pickup path follows corrected band");
        }
    }
    private static void pin(Minecraft client)throws Exception{
        for(var name:new String[]{"xpos","ypos"}){var field=net.minecraft.client.MouseHandler.class.getDeclaredField(name);field.setAccessible(true);field.setDouble(client.mouseHandler,(name.equals("xpos")?client.getWindow().getScreenWidth():client.getWindow().getScreenHeight())*.4);}
    }
    private static void aim(Minecraft client,BlockPos pos){RtsPlacementRayFreeze.clear();var screen=(BuilderScreen)client.screen;var origin=screen.currentRayOrigin();var target=client.level.getBlockState(pos).getShape(client.level,pos).bounds().getCenter().add(Vec3.atLowerCornerOf(pos));RtsPlacementRayFreeze.freeze(origin,target.subtract(origin).normalize());require(RtsClient.view()!=null && RtsClient.view().hit().getBlockPos().equals(pos),"cursor reaches intended interface: "+pos);}
    private static double mouseX(Minecraft c){return c.mouseHandler.xpos()*c.getWindow().getGuiScaledWidth()/c.getWindow().getScreenWidth();}
    private static double mouseY(Minecraft c){return c.mouseHandler.ypos()*c.getWindow().getGuiScaledHeight()/c.getWindow().getScreenHeight();}
    private static net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre press(Minecraft c){var event=new net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(c.screen,mouseX(c),mouseY(c),1);RtsClient.click(event);return event;}
    private static void release(Minecraft c){var event=new net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonReleased.Pre(c.screen,mouseX(c),mouseY(c),1);RtsClient.release(event);if(!event.isCanceled())c.screen.mouseReleased(mouseX(c),mouseY(c),1);}
    private static CameraInputHandler camera(Minecraft c)throws Exception{var field=BuilderScreen.class.getSuperclass().getDeclaredField("cameraInput");field.setAccessible(true);return (CameraInputHandler)field.get(c.screen);}
    private static void next(){phase++;ticks=0;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void screenshot(Minecraft c,String name)throws Exception{var dir=c.gameDirectory.toPath().resolve("join-screenshots");java.nio.file.Files.createDirectories(dir);try(var image=net.minecraft.client.Screenshot.takeScreenshot(c.getMainRenderTarget())){image.writeToFile(dir.resolve(name));}}
}
