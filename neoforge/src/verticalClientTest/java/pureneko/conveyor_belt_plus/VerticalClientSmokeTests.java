package pureneko.conveyor_belt_plus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.registry.*;
import pureneko.conveyor_belt_plus.client.BeltPickupTarget;
import pureneko.conveyor_belt_plus.client.renderers.*;
import pureneko.conveyor_belt_plus.util.*;
import java.nio.file.Files;

/** Opt-in disposable real-client validation of both shader programs and interaction projections. */
@EventBusSubscriber(modid=ConveyorBeltPlus.MOD_ID,value=Dist.CLIENT)
public final class VerticalClientSmokeTests {
    private static final BlockPos SOURCE=new BlockPos(0,120,0);
    private static int phase,ticks,total;
    private static volatile boolean ready;
    private static volatile Vec3 pinned;
    private static long aimId;
    private static boolean aimUpgrade;
    private static long outlineFrames, upgradeFrames, previewFrames;
    private static int outlinedWhite;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static volatile int benchmarkSink;

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {

        var client=Minecraft.getInstance();
        if (++total>2400) throw new AssertionError("Vertical smoke timeout, phase "+phase);
        ticks++;
        if(phase<13 && client.player!=null && pinned!=null){
            client.player.setPos(pinned);client.player.setDeltaMovement(Vec3.ZERO);client.player.setNoGravity(true);
            // Follow the displayed parcel rather than assuming server and interpolated client progress coincide.
            var source=(ChuteBlockEntity)client.level.getBlockEntity(SOURCE);
            if(source!=null && source.getBeltData()!=null){
                var packet=source.pickupAccess(Direction.UP).items().stream().filter(p->p.id==aimId).findFirst().orElse(null);
                if(aimUpgrade || packet!=null){
                    double progress=aimUpgrade?.45:packet.renderedProgress(source.getVisualState().presentationTick(BeltRenderClock.now(client.level)));
                    var point=SplineUtil.getPositionOnSpline(source.getBeltData(),progress).add(0,aimUpgrade?0:.18,aimUpgrade?BeltGeometry.LIFT_SURFACE_OFFSET_Z:BeltGeometry.LIFT_CARGO_OFFSET_Z);
                    var direction=point.subtract(client.player.getEyePosition());
                    client.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))));
                    client.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
                    client.player.xRotO=client.player.getXRot();client.player.yRotO=client.player.getYRot();
                }
            }
        }
        client.options.pauseOnLostFocus=false;
        if(client.player!=null && phase<13)client.mouseHandler.releaseMouse();
        if (phase==0 && ticks==20) client.setScreen(new TitleScreen());
        try {
            if (phase==0 && ticks>30 && client.screen instanceof TitleScreen) {
                client.createWorldOpenFlows().openWorld("VerticalSmoke",()->client.setScreen(new TitleScreen()));
                advance();
            } else if (phase==1 && client.player!=null && client.level!=null) {
                client.getSingleplayerServer().execute(()->{
                    var world=client.getSingleplayerServer().overworld();
                    var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.setGameMode(GameType.CREATIVE);
                    for(int x=-5;x<=6;x++) for(int z=-5;z<=7;z++) {
                        world.setBlockAndUpdate(new BlockPos(x,118,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        for(int y=119;y<=132;y++)world.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    world.setBlockAndUpdate(SOURCE,BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.UP));
                    world.setBlockAndUpdate(SOURCE.above(8),BlockContent.UNIVERSAL_CHUTE.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.DOWN));
                    var source=(ChuteBlockEntity)world.getBlockEntity(SOURCE);
                    if(!source.connectOutgoing(Direction.UP,SOURCE.above(8),Direction.DOWN,java.util.List.of(),1))throw new AssertionError("smoke lift rejected");
                    var queue=source.pickupAccess(Direction.UP).items();
                    queue.addLast(new ChuteBlockEntity.BeltItem(.30f,101,new ItemStack(Items.DIAMOND,16)));
                    queue.addLast(new ChuteBlockEntity.BeltItem(.60f,102,FluidPackets.create(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1000))));
                    queue.addLast(new ChuteBlockEntity.BeltItem(.90f,103,new ItemStack(Items.GOLD_BLOCK,4)));
                    source.setChanged();world.getChunkSource().blockChanged(SOURCE);
                    world.setDayTime(6000);
                    player.getAbilities().flying=true;player.onUpdateAbilities();
                    player.teleportTo(world,3,125,6,155,15);
                    ready=true;
                });advance();
            } else if(phase==2 && ready && ticks>120) {
                screenshot(client,"01-lift.png");
                benchmark(client);
                aim(client,101,ItemStack.EMPTY,false);advance();
            } else if(phase==3 && ready && ticks>35 && BeltPickupTarget.current()!=null && BeltPickupTarget.current().id()==101 && BeltPickupTarget.canHighlight()) {
                require(BeltPickupTarget.canHighlight() && BeltPickupTarget.current().id()==101,"aimed item outline eligibility");
                require(BeltCargoOutline.renderedFrames()>0,"item outline rendered with compiled shaders");
                screenshot(client,"02-item-outline.png");
                outlineFrames=BeltCargoOutline.renderedFrames();
                aim(client,102,new ItemStack(Items.BUCKET),false);advance();
            } else if(phase==4 && ready && ticks>35 && BeltPickupTarget.current()!=null && BeltPickupTarget.current().id()==102 && BeltPickupTarget.canHighlight()) {
                require(BeltPickupTarget.canHighlight() && BeltPickupTarget.current().id()==102,"compatible fluid container highlights parcel");
                require(BeltCargoOutline.renderedFrames()>outlineFrames,"fluid outline rendered");
                screenshot(client,"03-fluid-outline.png");
                outlinedWhite=centreWhitePixels(client);
                aim(client,102,new ItemStack(Items.LAVA_BUCKET),false);advance();
            } else if(phase==5 && ready && ticks>25) {
                require(!BeltPickupTarget.canHighlight(),"incompatible fluid container never highlights");
                require(outlinedWhite>centreWhitePixels(client)+2,"eligible fluid has visible white outline pixels, incompatible container does not");
                screenshot(client,"04-incompatible-container.png");
                var held=new ItemStack(ItemContent.ADVANCED_BELT.get(),2);
                held.set(ComponentContent.BELT_START.get(),SOURCE.east(4));held.set(ComponentContent.BELT_DIR.get(),Direction.WEST);
                upgradeFrames=BeltOutlineRenderer.upgradeFrames();
                aim(client,-1,held,true);advance();
            } else if(phase==6 && ready && ticks>25 && BeltPickupTarget.currentUpgrade()!=null && BeltOutlineRenderer.upgradeFrames()>upgradeFrames) {
                require(BeltPickupTarget.currentUpgrade()!=null && BeltOutlineRenderer.upgradeFrames()>upgradeFrames,"new-tier body projection rendered");
                screenshot(client,"05-upgrade-projection.png");
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new pureneko.conveyor_belt_plus.network.BeltUpgradeNetworking.Request(SOURCE,Direction.UP));
                advance();
            } else if(phase==7 && ticks>30) {
                var source=(ChuteBlockEntity)client.level.getBlockEntity(SOURCE);
                require(source.getBeltTier()==2,"body upgrade packet completed and synchronized");
                require(client.player.getMainHandItem().get(ComponentContent.BELT_START.get()).equals(SOURCE.east(4)),"network upgrade retains draft");
                aim(client,101,ItemStack.EMPTY,false);advance();
            } else if(phase==8 && ready && ticks>25) {
                screenshot(client,"06-upgraded-lift.png");
                outlineFrames=BeltCargoOutline.renderedFrames();
                reload=client.reloadResourcePacks();advance();
            } else if(phase==9 && ticks>60 && reload.isDone() && client.getOverlay()==null && BeltPickupTarget.current()!=null) {
                reload.join();
                require(BeltPickupTarget.canHighlight() && BeltCargoOutline.renderedFrames()>outlineFrames,"resource reload restores private mask and shaders");
                screenshot(client,"07-after-resource-reload.png");
                outlineFrames=BeltCargoOutline.renderedFrames();
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(),1000,700);
                advance();
            } else if(phase==10 && ticks>35) {
                require(BeltCargoOutline.renderedFrames()>outlineFrames,"outline works after framebuffer resize");
                screenshot(client,"08-after-resize.png");
                previewFrames=BeltOutlineRenderer.previewFrames();
                constructionPreview(client,false);advance();
            } else if(phase==11 && ready && ticks>30) {
                require(BeltOutlineRenderer.previewFrames()>previewFrames && !BeltOutlineRenderer.previewValid(),"invalid non-endpoint retains red construction projection");
                screenshot(client,"09-non-endpoint-preview.png");
                previewFrames=BeltOutlineRenderer.previewFrames();constructionPreview(client,true);advance();
            } else if(phase==12 && ready && ticks>30) {
                require(client.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.MISS && BeltOutlineRenderer.previewFrames()>previewFrames,
                        "construction projection follows ray in empty air");
                screenshot(client,"10-air-preview.png");
                Files.writeString(client.gameDirectory.toPath().resolve("vertical-smoke-success.txt"),"Vertical symmetry, item/fluid alpha outline, incompatible container, body upgrade packet + draft, resource reload, framebuffer resize, non-endpoint and empty-air construction projections passed.\n");
                if(Boolean.getBoolean("conveyor.rtsClientSmoke"))
                    Class.forName("pureneko.conveyor_belt_plus.RtsClientSmokeTests").getMethod("begin",Minecraft.class).invoke(null,client);
                else client.stop();
                phase=13;
            }
        }catch(Throwable error){
            try{screenshot(client,"failure-phase-"+phase+".png");}catch(Exception ignored){}
            throw new RuntimeException("Vertical client smoke failed phase "+phase+" player="+(client.player==null?null:client.player.position())+" target="+BeltPickupTarget.current(),error);
        }
    }
    private static void aim(Minecraft client,long id,ItemStack held,boolean upgrade){
        ready=false;
        aimId=id;aimUpgrade=upgrade;
        client.options.keyShift.setDown(upgrade);
        client.getSingleplayerServer().execute(()->{
            var world=client.getSingleplayerServer().overworld();
            var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            var source=(ChuteBlockEntity)world.getBlockEntity(SOURCE);
            Vec3 point;
            if(upgrade)point=SplineUtil.getPositionOnSpline(source.getBeltData(),.45).add(0,0,BeltGeometry.LIFT_SURFACE_OFFSET_Z);
            else{
                var packet=source.pickupAccess(Direction.UP).items().stream().filter(p->p.id==id).findFirst().orElseThrow();
                point=SplineUtil.getPositionOnSpline(source.getBeltData(),packet.progress).add(0,.18,0);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND,held);
            player.setShiftKeyDown(upgrade);
            player.getAbilities().flying=true;player.onUpdateAbilities();player.setNoGravity(true);
            player.teleportTo(world,point.x,point.y-player.getEyeHeight(),point.z+3,180,0);
            pinned=player.position();
            ready=true;
        });
    }
    private static void constructionPreview(Minecraft client, boolean air){
        ready=false;aimId=-99;aimUpgrade=false;client.options.keyShift.setDown(false);
        client.getSingleplayerServer().execute(()->{
            var world=client.getSingleplayerServer().overworld();
            var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            var held=new ItemStack(ItemContent.BELT.get());
            held.set(ComponentContent.BELT_START.get(),SOURCE);held.set(ComponentContent.BELT_DIR.get(),Direction.UP);
            player.setItemInHand(InteractionHand.MAIN_HAND,held);player.setShiftKeyDown(false);
            player.teleportTo(world,4.5,124,4.5,0,air?0:90);
            pinned=player.position();ready=true;
        });
    }
    private static void benchmark(Minecraft client)throws java.io.IOException{
        var source=(ChuteBlockEntity)client.level.getBlockEntity(SOURCE);
        var data=source.getBeltData();
        for(int i=0;i<100;i++)benchmarkSink=ChuteBeltRenderer.createSplineModel(data,SOURCE).length;
        long start=System.nanoTime();
        for(int i=0;i<10000;i++)benchmarkSink=ChuteBeltRenderer.createSplineModel(data,SOURCE).length;
        long rebuilt=System.nanoTime()-start;
        var cached=ChuteBeltRenderer.createSplineModel(data,SOURCE);
        start=System.nanoTime();for(int i=0;i<10000;i++)benchmarkSink=cached.length;
        long reused=System.nanoTime()-start;
        Files.writeString(client.gameDirectory.toPath().resolve("vertical-performance.txt"),
                "Same lift mesh (8-block endpoint separation), 10000 preview frames: rebuild="+rebuilt+" ns; cached reuse="+reused+" ns. This measures mesh CPU work, not total FPS.\n");
    }
    private static int centreWhitePixels(Minecraft client){
        try(var image=Screenshot.takeScreenshot(client.getMainRenderTarget())){
            int count=0,cx=image.getWidth()/2,cy=image.getHeight()/2;
            for(int y=cy-40;y<cy+40;y++)for(int x=cx-40;x<cx+40;x++){
                int rgb=image.getPixelRGBA(x,y);
                if((rgb&255)>225 && ((rgb>>8)&255)>225 && ((rgb>>16)&255)>225)count++;
            }
            return count;
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void advance(){phase++;ticks=0;}
    private static void screenshot(Minecraft client,String name)throws java.io.IOException{
        var path=client.gameDirectory.toPath().resolve("vertical-screenshots");Files.createDirectories(path);
        try(var image=Screenshot.takeScreenshot(client.getMainRenderTarget())){image.writeToFile(path.resolve(name));}
        if(name.contains("outline"))try{
            var field=BeltCargoOutline.class.getDeclaredField("mask");field.setAccessible(true);
            var mask=(com.mojang.blaze3d.pipeline.RenderTarget)field.get(null);
            if(mask!=null)try(var image=Screenshot.takeScreenshot(mask)){
                int pixels=0;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getPixelRGBA(x,y)&0xFFFFFF)!=0)pixels++;
                require(pixels>0,"selected cargo renders a nonempty silhouette mask");
                image.writeToFile(path.resolve("mask-"+name));
            }
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
}
