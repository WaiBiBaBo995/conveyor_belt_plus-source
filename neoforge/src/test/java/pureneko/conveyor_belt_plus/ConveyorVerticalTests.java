package pureneko.conveyor_belt_plus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import pureneko.conveyor_belt_plus.blocks.*;
import pureneko.conveyor_belt_plus.registry.*;
import pureneko.conveyor_belt_plus.util.*;
import pureneko.conveyor_belt_plus.filter.FilterRule;
import java.util.List;

@GameTestHolder(ConveyorBeltPlus.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConveyorVerticalTests {
    private static ChuteBlockEntity chute(GameTestHelper test, BlockPos position, Block block, Direction facing) {
        test.getLevel().setBlockAndUpdate(position, block.defaultBlockState().setValue(ChuteBlock.FACING, facing));
        return (ChuteBlockEntity)test.getLevel().getBlockEntity(position);
    }
    private static void tick(ChuteBlockEntity chute) { chute.tick(chute.getLevel(), chute.getBlockPos(), chute.getBlockState(), chute); }

    @GameTest(template="empty", templateNamespace=ConveyorBeltPlus.MOD_ID)
    public static void verticalGeometryAndFaces(GameTestHelper test) {
        var world=test.getLevel();
        var bottom=test.absolutePos(new BlockPos(2,1,2));
        var top=bottom.above(3);
        for (var facing : Direction.values()) {
            var node=chute(test,bottom,BlockContent.CHUTE_BLOCK.get(),facing);
            test.assertTrue(node.getOwnFacing()==facing && !node.getBlockState().getShape(world,bottom).isEmpty(), "all six placements have a shape and port");
            var faces=java.util.EnumSet.noneOf(Direction.class);
            for (var side : ExtractionSide.values()) test.assertTrue(faces.add(side.direction(facing)), "six extraction selectors map to six unique world faces");
        }
        var player=test.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setYRot(0);
        for(var face:Direction.values()) for(boolean sneak:new boolean[]{false,true}) {
            var held=new ItemStack(ItemContent.CHUTE.get());
            player.setShiftKeyDown(sneak);
            var hit=new net.minecraft.world.phys.BlockHitResult(bottom.getCenter(),face,bottom,false);
            var context=new net.minecraft.world.item.context.BlockPlaceContext(player,InteractionHand.MAIN_HAND,held,hit);
            var placed=BlockContent.CHUTE_BLOCK.get().getStateForPlacement(context);
            var expected=ChutePlacementPlan.facing(face,player.getDirection(),sneak,true);
            test.assertTrue(placed.getValue(ChuteBlock.FACING)==expected,"direct interface placement follows default-horizontal/Shift-vertical rule");
        }
        var upShape=BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.UP).getShape(world,bottom).bounds();
        var downShape=BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(ChuteBlock.FACING,Direction.DOWN).getShape(world,bottom).bounds();
        test.assertTrue(Math.abs(upShape.minZ-downShape.minZ)<1e-7 && Math.abs(upShape.maxZ-downShape.maxZ)<1e-7
                && Math.abs(upShape.minY+downShape.maxY-1)<1e-7 && Math.abs(upShape.maxY+downShape.minY-1)<1e-7,
                "vertical interfaces share footprint and mirror exactly in height");
        var lower=chute(test,bottom,BlockContent.CHUTE_BLOCK.get(),Direction.UP);
        chute(test,top,BlockContent.CHUTE_BLOCK.get(),Direction.DOWN);
        var up=ChuteBlockEntity.BeltData.create(world,bottom,Direction.UP,top,Direction.DOWN,List.of(),true,1.25,0);
        var down=ChuteBlockEntity.BeltData.create(world,top,Direction.DOWN,bottom,Direction.UP,List.of(),true,1.25,0);
        test.assertTrue(up!=null && down!=null && up.isVertical() && down.isVertical(), "both lift directions are valid");
        test.assertTrue(Math.abs(up.totalLength()-4)<.0001 && Math.abs(down.totalLength()-4)<.0001, "mount anchors preserve straight length");
        test.assertTrue(ChuteBlockEntity.BeltData.create(world,bottom,Direction.UP,top.east(),Direction.DOWN,List.of())==null, "offset vertical endpoints rejected");
        test.assertTrue(ChuteBlockEntity.BeltData.create(world,bottom,Direction.UP,top,Direction.UP,List.of())==null, "inverted receiving port rejected");
        test.assertTrue(ChuteBlockEntity.BeltData.create(world,bottom,Direction.UP,top,Direction.WEST,List.of())==null, "mixed endpoint directions rejected");
        for (double progress : new double[]{0,.25,.5,.75,1}) {
            var point=SplineUtil.getPositionOnSpline(up,progress);
            test.assertTrue(Math.abs(point.x-bottom.getX()-.5)<1e-7 && Math.abs(point.z-bottom.getZ()-.5)<1e-7, "lift remains exactly vertical");
        }
        var middle=SplineUtil.getPositionOnSpline(up,.5);
        var bandHit=up.interactionPath().raycast(middle.add(0,0,3),middle.add(0,0,-3));
        test.assertTrue(bandHit!=null && bandHit.point().z<middle.z-.10 && bandHit.point().z>middle.z-.35,
                "vertical interaction follows the band behind the carrier");
        test.succeed();
    }

    @GameTest(template="empty", templateNamespace=ConveyorBeltPlus.MOD_ID)
    public static void verticalItemsBothDirectionsAndReload(GameTestHelper test) {
        var world=test.getLevel();
        double speed=pureneko.conveyor_belt_plus.config.ConveyorConfig.SPEEDS[0].get();
        try {
            pureneko.conveyor_belt_plus.config.ConveyorConfig.SPEEDS[0].set(64.0);
            for (boolean ascending : new boolean[]{true,false}) {
                var bottom=test.absolutePos(new BlockPos(2,1,ascending?2:5));
                var top=bottom.above(4);
                world.setBlockAndUpdate(bottom,Blocks.CHEST.defaultBlockState());
                world.setBlockAndUpdate(top,Blocks.CHEST.defaultBlockState());
                var lower=chute(test,bottom.above(),BlockContent.CHUTE_BLOCK.get(),Direction.UP);
                var upper=chute(test,top.below(),BlockContent.CHUTE_BLOCK.get(),Direction.DOWN);
                var source=ascending?lower:upper;
                var destination=ascending?upper:lower;
                var sourceInv=(ChestBlockEntity)world.getBlockEntity(ascending?bottom:top);
                var destinationInv=(ChestBlockEntity)world.getBlockEntity(ascending?top:bottom);
                sourceInv.setItem(0,new ItemStack(Items.DIAMOND,17));
                test.assertTrue(source.connectOutgoing(source.getOwnFacing(),destination.getBlockPos(),destination.getOwnFacing(),List.of(),1), "vertical item route connects");
                destination.connectIncoming(destination.getOwnFacing(),source.getBlockPos());
                tick(source);
                var saved=source.saveWithFullMetadata(world.registryAccess());
                source.loadWithComponents(saved,world.registryAccess());
                test.assertTrue(source.getOwnFacing()==(ascending?Direction.UP:Direction.DOWN), "reload preserves vertical facing");
                for(int i=0;i<8;i++) tick(source);
                test.assertTrue(sourceInv.isEmpty() && destinationInv.countItem(Items.DIAMOND)==17 && source.pickupAccess(source.getOwnFacing()).items().isEmpty(), "vertical transport conserves item count after reload");
            }
        } finally { pureneko.conveyor_belt_plus.config.ConveyorConfig.SPEEDS[0].set(speed); }
        test.succeed();
    }

    @GameTest(template="empty", templateNamespace=ConveyorBeltPlus.MOD_ID)
    public static void universalReplacementKeepsLiftAndSettings(GameTestHelper test) {
        var world=test.getLevel();
        var sourcePos=test.absolutePos(new BlockPos(2,1,2));
        var source=chute(test,sourcePos,BlockContent.CHUTE_BLOCK.get(),Direction.UP);
        var destination=chute(test,sourcePos.above(3),BlockContent.CHUTE_BLOCK.get(),Direction.DOWN);
        test.assertTrue(source.connectOutgoing(Direction.UP,destination.getBlockPos(),Direction.DOWN,List.of(),1), "replacement test route");
        destination.connectIncoming(Direction.DOWN,sourcePos);
        source.setRule(false,0,FilterRule.item(new ItemStack(Items.DIAMOND),false));
        source.setWhitelistMode(false,true);
        source.setExtractionSides(false,ExtractionSide.LEFT.bit());
        source.pickupAccess(source.getOwnFacing()).items().add(new ChuteBlockEntity.BeltItem(.4f,78,new ItemStack(Items.DIAMOND,12)));
        var player=test.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ItemContent.UNIVERSAL_CHUTE.get(),2));
        test.assertTrue(UpgradeInteractions.chute(world,sourcePos,player,player.getMainHandItem(),(ChuteBlock)BlockContent.UNIVERSAL_CHUTE.get()), "same-tier universal conversion succeeds");
        var upgraded=(ChuteBlockEntity)world.getBlockEntity(sourcePos);
        test.assertTrue(upgraded.getOwnFacing()==Direction.UP && upgraded.getTarget().equals(destination.getBlockPos()) && upgraded.getBeltTier()==1,"facing, connection and belt tier retained");
        test.assertTrue(upgraded.isWhitelistMode(false) && upgraded.getRule(false,0).sameRule(source.getRule(false,0)) && upgraded.getExtractionSides(false)==ExtractionSide.LEFT.bit(),"item settings retained");
        test.assertTrue(!upgraded.isWhitelistMode(true) && upgraded.getRedstoneMode(true)==RedstoneControl.Mode.ALWAYS,"new domain enabled with defaults");
        var packet=upgraded.pickupAccess(upgraded.getOwnFacing()).items().getFirst();
        test.assertTrue(packet.id==78 && packet.progress==.4f && packet.stack.getCount()==12,"in-flight contents retained");
        test.assertTrue(player.getMainHandItem().getCount()==1 && player.getInventory().countItem(ItemContent.CHUTE.get())==1,"consume/refund exactly one interface");
        test.assertFalse(UpgradeInteractions.canReplace((ChuteBlock)BlockContent.ULTIMATE_CHUTE.get(),(ChuteBlock)BlockContent.UNIVERSAL_CHUTE.get()),"lower universal cannot replace higher tier");
        test.assertFalse(UpgradeInteractions.canReplace((ChuteBlock)BlockContent.UNIVERSAL_CHUTE.get(),(ChuteBlock)BlockContent.ADVANCED_CHUTE.get()),"universal cannot narrow to item-only");
        test.succeed();
    }

    @GameTest(template="empty", templateNamespace=ConveyorBeltPlus.MOD_ID)
    public static void beltBodyUpgradeReachAndDraft(GameTestHelper test) {
        var world=test.getLevel();
        var sourcePos=test.absolutePos(new BlockPos(2,2,2));
        var source=chute(test,sourcePos,BlockContent.CHUTE_BLOCK.get(),Direction.EAST);
        chute(test,sourcePos.east(8),BlockContent.CHUTE_BLOCK.get(),Direction.WEST);
        test.assertTrue(source.connectOutgoing(Direction.EAST,sourcePos.east(8),Direction.WEST,List.of(),1),"body upgrade route");
        source.pickupAccess(source.getOwnFacing()).items().add(new ChuteBlockEntity.BeltItem(.5f,90,new ItemStack(Items.DIAMOND,3)));
        var packet=source.pickupAccess(source.getOwnFacing()).items().getFirst();
        var player=test.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); player.setShiftKeyDown(true);
        var held=new ItemStack(ItemContent.ADVANCED_BELT.get(),2);
        held.set(ComponentContent.BELT_START.get(),sourcePos.north(2)); held.set(ComponentContent.BELT_DIR.get(),Direction.SOUTH);
        player.setItemInHand(InteractionHand.MAIN_HAND,held);
        var aim=SplineUtil.getPositionOnSpline(source.getBeltData(),.5);
        player.setPos(aim.x,aim.y+2,aim.z); player.setXRot(90); player.setYRot(0);
        test.assertTrue(UpgradeInteractions.beltAt(player,sourcePos,Direction.EAST),"body ray upgrades without aiming at endpoint");
        test.assertTrue(source.getBeltTier()==2 && source.pickupAccess(source.getOwnFacing()).items().getFirst()==packet && packet.progress==.5f,"upgrade preserves identity and progress");
        test.assertTrue(held.getCount()==1 && player.getInventory().countItem(ItemContent.BELT.get())==1,"body upgrade consumes/refunds one belt");
        test.assertTrue(held.get(ComponentContent.BELT_START.get()).equals(sourcePos.north(2)),"connection draft preserved");
        test.assertFalse(UpgradeInteractions.beltAt(player,sourcePos,Direction.EAST),"same tier rejected");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ItemContent.ULTIMATE_BELT.get(),2));
        world.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(aim).above(),Blocks.STONE.defaultBlockState());
        test.assertFalse(UpgradeInteractions.beltAt(player,sourcePos,Direction.EAST),"solid blocker prevents upgrade");
        world.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(aim).above(),Blocks.AIR.defaultBlockState());
        player.setPos(aim.x,aim.y+20,aim.z);
        test.assertFalse(UpgradeInteractions.beltAt(player,sourcePos,Direction.EAST),"remote spoofed owner cannot upgrade out of reach");
        player.setPos(aim.x,aim.y+2,aim.z);
        var lastBelt=new ItemStack(ItemContent.ULTIMATE_BELT.get());
        lastBelt.set(ComponentContent.BELT_START.get(),sourcePos.north(3));
        lastBelt.set(ComponentContent.BELT_DIR.get(),Direction.SOUTH);
        player.setItemInHand(InteractionHand.MAIN_HAND,lastBelt);
        test.assertTrue(UpgradeInteractions.beltAt(player,sourcePos,Direction.EAST),"last offered belt upgrades");
        var refund=player.getInventory().items.stream().filter(stack->stack.is(ItemContent.ADVANCED_BELT.get())).findFirst().orElseThrow();
        test.assertTrue(refund.get(ComponentContent.BELT_START.get()).equals(sourcePos.north(3)),"last-item draft follows refunded old belt");

        test.succeed();
    }

    @GameTest(template="empty", templateNamespace=ConveyorBeltPlus.MOD_ID)
    public static void unloadedTargetPausesWithoutForcingChunks(GameTestHelper test) {
        var world=test.getLevel();
        var position=test.absolutePos(new BlockPos(2,2,2));
        var source=chute(test,position,BlockContent.CHUTE_BLOCK.get(),Direction.EAST);
        chute(test,position.east(8),BlockContent.CHUTE_BLOCK.get(),Direction.WEST);
        test.assertTrue(source.connectOutgoing(Direction.EAST,position.east(8),Direction.WEST,List.of(),1),"unload fixture route");
        source.pickupAccess(Direction.EAST).items().add(new ChuteBlockEntity.BeltItem(.5f,55,new ItemStack(Items.DIAMOND,7)));
        var saved=source.saveWithFullMetadata(world.registryAccess());
        var unloaded=position.east(100000);
        test.assertFalse(world.hasChunkAt(unloaded),"remote test chunk starts unloaded");
        saved.putLong("target",unloaded.asLong());
        source.loadWithComponents(saved,world.registryAccess());
        for(int i=0;i<10;i++)tick(source);
        test.assertTrue(source.getTarget().equals(unloaded) && !world.hasChunkAt(unloaded),"route retained without loading remote chunk");
        var again=source.saveWithFullMetadata(world.registryAccess());
        var packet=again.getList("moving",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        test.assertTrue(again.getList("moving",net.minecraft.nbt.Tag.TAG_COMPOUND).size()==1 && Math.abs(packet.getFloat("a")-.5f)<1e-6,"queued packet and progress retained while unloaded");
        test.succeed();
    }
}
