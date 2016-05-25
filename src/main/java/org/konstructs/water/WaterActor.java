package org.konstructs.water;

import akka.actor.ActorRef;
import akka.actor.Props;
import konstructs.api.*;
import konstructs.api.messages.BlockUpdateEvent;
import konstructs.api.messages.BoxQueryResult;
import konstructs.api.messages.ReplaceBlock;
import konstructs.plugin.KonstructsActor;
import konstructs.plugin.PluginConstructor;

import scala.concurrent.duration.Duration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.Map;

public class WaterActor extends KonstructsActor {

    class TriggerWaterQueue {}
    class HeatTrigger {}

    HashSet<Position> waterList;
    HashMap<Position, Integer> heatMap;
    HashMap<Position, BlockTypeId> waterQueue;

    private float simulation_speed;

    public WaterActor(ActorRef universe) {
        super(universe);
        waterList = new HashSet<>();
        heatMap = new HashMap<>();
        waterQueue = new HashMap<>();
        simulation_speed = 1;

        scheduleSelfOnce(new TriggerWaterQueue(), simulationFactor(100));
        scheduleSelfOnce(new HeatTrigger(), simulationFactor(1000));
    }

    @Override
    public void onReceive(Object message) {
        super.onReceive(message);

        if (message instanceof TriggerWaterQueue) {

            if (!waterQueue.isEmpty()) {
                Position p = (Position) waterQueue.keySet().toArray()[0];

                replaceBlock(
                        BlockFilterFactory.VACUUM.or(BlockFilterFactory.withState(BlockType.STATE_LIQUID)),
                        p, Block.create(waterQueue.get(p)), simulationFactor(400));

                waterQueue.remove(p);
            }

            scheduleSelfOnce(new TriggerWaterQueue(), simulationFactor(100));
        }

        if (message instanceof HeatTrigger) {
            processHeat();
            scheduleSelfOnce(new HeatTrigger(), simulationFactor(10000));
        }

    }

    @Override
    public void onBlockUpdateEvent(BlockUpdateEvent event) {
        for (Map.Entry<Position, BlockUpdate> block : event.getUpdatedBlocks().entrySet()) {
            boxQuery(Box.createAround(block.getKey(), new Position(1, 1, 1)));
        }
    }

    @Override
    public void onBoxQueryResult(BoxQueryResult result) {
        BlockTypeId below = result.getBlocks()[10];
        BlockTypeId center = result.getBlocks()[13];

        Position pos_below = result.getBox().getFrom().add(new Position(1, 0, 1));
        Position pos_center = result.getBox().getFrom().add(new Position(1,1,1));

        if (!center.equals(BlockTypeId.fromString("org/konstructs/water"))
                && !center.getNamespace().equals("org/konstructs/water")) return;

        // It's vacuum below, fall down one block
        if (below.equals(BlockTypeId.VACUUM)) {
            BlockTypeId newBlock = increaseWaterBlock(center, 75);
            replaceVacuumBlock(pos_below, Block.create(newBlock), simulationFactor(400));
            replaceWithVacuum(BlockFilterFactory.withState(BlockType.STATE_LIQUID), pos_center, simulationFactor(400));

        // Merge with existing water under
        } else if (below.equals(BlockTypeId.fromString("org/konstructs/water"))
                || below.getNamespace().equals("org/konstructs/water")) {

            if (!heatMap.containsKey(pos_below) || (heatMap.containsKey(pos_below) && heatMap.get(pos_below) < 20)) {
                waterQueue.put(pos_below, mergeWaterBlocks(center, below));
                increaseHeat(pos_below);
                replaceWithVacuum(BlockFilterFactory.withState(BlockType.STATE_LIQUID), pos_center, simulationFactor(400));
            }

        // We hit a solid block, merge or spread water
        } else {

            BlockTypeId reducedBlockType = reduceWaterBlock(center, 25);

            if (!reducedBlockType.equals(BlockTypeId.VACUUM)) {
                placeOrMergeWater(pos_center.addX(1), reducedBlockType, result.get(pos_center.addX(1)));
                placeOrMergeWater(pos_center.addZ(1), reducedBlockType, result.get(pos_center.addZ(1)));
                placeOrMergeWater(pos_center.subtractX(1), reducedBlockType, result.get(pos_center.subtractX(1)));
                placeOrMergeWater(pos_center.subtractZ(1), reducedBlockType, result.get(pos_center.subtractZ(1)));
            }
        }

    }

    private int simulationFactor(int n) {
        return (int)(simulation_speed * n) + (int)(120 * Math.random());
    }

    private void placeOrMergeWater(Position pos, BlockTypeId reducedBlockType, BlockTypeId nextBlockType) {
        if (nextBlockType.equals(BlockTypeId.VACUUM)) {
            replaceVacuumBlock(pos, Block.create(reducedBlockType), simulationFactor(400));
        } else if(nextBlockType.getNamespace().equals("org/konstructs/water")
                || nextBlockType.equals(BlockTypeId.fromString("org/konstructs/water"))) {
            BlockTypeId mergedBlockId = mergeWaterBlocks(reducedBlockType, nextBlockType);
            if (!mergedBlockId.equals(nextBlockType)) {
                queueWater(pos, mergedBlockId);
            }
        }
    }

    private void increaseHeat(Position p) {
        int num = heatMap.containsKey(p) ? heatMap.get(p) : 0;
        heatMap.remove(p);
        heatMap.put(p, num + 1);
    }

    private void decreaseHeat(Position p) {

        int num = heatMap.containsKey(p) ? heatMap.get(p) : 0;
        heatMap.remove(p);
        if (num > 0) {
            heatMap.put(p, num - 1);
        }
    }

    private void processHeat() {
        for (Object p : heatMap.keySet().toArray()) {
            decreaseHeat(((Position)p));
        }

        System.out.println("HEAT:");
        for (Map.Entry<Position, Integer> h : heatMap.entrySet()) {
            System.out.println(h.getKey() + " -> " + h.getValue());

            if (h.getValue() > 18) {
                // Overwrite problem blocks with stone
                replaceBlock(BlockFilterFactory.VACUUM.or(BlockFilterFactory.withState(BlockType.STATE_LIQUID)),
                        h.getKey(), Block.create("org/konstructs/stone"));
            }

        }

        for (Map.Entry<Position, BlockTypeId> h : waterQueue.entrySet()) {
            System.out.println(h.getKey() + " -> " + h.getValue());
        }
    }

    private void queueWater(Position pos, BlockTypeId typeId) {
        if (!heatMap.containsKey(pos) || (heatMap.containsKey(pos) && heatMap.get(pos) < 20)) {
            waterQueue.put(pos, typeId);
            increaseHeat(pos);
        }
    }

    private int waterBlockToNum(BlockTypeId b) {
        if(b.getNamespace().equals("org/konstructs/water") || b.equals(BlockTypeId.fromString("org/konstructs/water"))) {
            return b.getName().equals("water") ? 100 : new Integer(b.getName());
        }
        return 0;
    }

    private BlockTypeId numToWaterBlock(int n) {
        if (n > 75) {
            return BlockTypeId.fromString("org/konstructs/water");
        } else if (n > 50) {
            return BlockTypeId.fromString("org/konstructs/water/75");
        } else if (n > 25) {
            return BlockTypeId.fromString("org/konstructs/water/50");
        } else if (n > 0) {
            return BlockTypeId.fromString("org/konstructs/water/25");
        }

        return BlockTypeId.fromString("org/konstructs/vacuum");
    }

    private BlockTypeId mergeWaterBlocks(BlockTypeId b1, BlockTypeId b2) {
        return numToWaterBlock(waterBlockToNum(b1) + (waterBlockToNum(b2) / 2));
    }

    private BlockTypeId reduceWaterBlock(BlockTypeId b, int amount) {
        return numToWaterBlock(waterBlockToNum(b) - amount);
    }

    private BlockTypeId increaseWaterBlock(BlockTypeId b, int amount) {
        return numToWaterBlock(waterBlockToNum(b) + amount);
    }

    /**
     * Replace a single block
     */
    public void replaceBlock(BlockFilter filter, Position pos, Block block) {
        getUniverse().tell(new ReplaceBlock(filter, pos, block), getSelf());
    }

    /**
     * Replace a single block with a delay
     */
    public void replaceBlock(BlockFilter filter, Position pos, Block block, int msec) {
        scheduleOnce(new ReplaceBlock(filter, pos, block), msec, getUniverse(), getSelf());
    }

    /**
     * Like scheduleOnce from {@link KonstructsActor} except that this one allows you
     * to specify a sender.
     */
    public void scheduleOnce(Object obj, int msec, ActorRef to, ActorRef sender) {
        getContext().system().scheduler().scheduleOnce(
                Duration.create(msec, TimeUnit.MILLISECONDS),
                to, obj, getContext().system().dispatcher(), sender);
    }

    /**
     * Like replaceVacuumBlock from {@link KonstructsActor} except that this one allows
     * to to specify a delayed action.
     */
    public void replaceVacuumBlock(Position position, Block block, int msec) {
        scheduleOnce(new ReplaceBlock(BlockFilterFactory.VACUUM, position, block), msec, getUniverse(), getSelf());
    }

    /**
     * Like replaceWithVacuum from {@link KonstructsActor} except that this one allows
     * to to specify a delayed action.
     */
    public void replaceWithVacuum(BlockFilter filter, Position position, int msec) {
        scheduleOnce(new ReplaceBlock(filter, position, Block.create(BlockTypeId.VACUUM)), msec, getUniverse(), getSelf());
    }

    @PluginConstructor
    public static Props props(String pluginName, ActorRef universe) {
        return Props.create(WaterActor.class, universe);
    }
}
