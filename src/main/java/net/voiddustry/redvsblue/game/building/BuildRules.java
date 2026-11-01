package net.voiddustry.redvsblue.game.building;

import arc.Events;
import arc.struct.Queue;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.net.Administration.ActionType;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import net.voiddustry.redvsblue.PlayerData;
import net.voiddustry.redvsblue.game.stations.*;

import static net.voiddustry.redvsblue.RedVsBluePlugin.players;

// rejected plans are dropped by NetServer.clientSnapshot, which also calls removeQueueBlock on the client
public class BuildRules {
    public static void init() {
        Vars.netServer.admins.addActionFilter(action -> {
            if (action.player == null) return true;
            if (action.type == ActionType.breakBlock) return false;
            if (action.type != ActionType.placeBlock) return true;
            // until the blue core is gone, plans just wait in the queue and get checked once building opens
            if (!UpdateConstructBlocks.open) return true;
            return allowed(action.player, action.block, action.tile);
        });

        Events.run(Trigger.update, () -> {
            for (Unit unit : Groups.unit) {
                Queue<BuildPlan> plans = unit.plans();
                if (plans == null) continue;
                for (int i = plans.size - 1; i >= 0; i--) {
                    if (plans.get(i).breaking) plans.removeIndex(i);
                }
            }
        });
    }

    public static int cost(Block block) {
        if (block == null) return 0;
        for (ItemStack stack : block.requirements) {
            if (stack.item == Items.dormantCyst) return stack.amount;
        }
        return 0;
    }

    private static boolean allowed(Player player, Block block, Tile tile) {
        if (player.team() != Team.blue) return false;
        PlayerData data = players.get(player.uuid());
        if (data == null || block == null || tile == null) return false;

        int cost = cost(block);
        if (cost <= 0 || Vars.state.rules.isBanned(block)) return false;

        if (isStation(block)) {
            if (ownsStation(block, player.uuid())) return false;
            if (!tile.block().isAir() || tile.floor() == Blocks.empty) return false;
        }

        return data.getScore() >= cost;
    }

    private static boolean isStation(Block block) {
        return block == Blocks.pulverizer || block == Blocks.mender || block == Blocks.phaseWall
                || block == Blocks.beamNode || block == Blocks.slagIncinerator
                || block == Vars.content.block("dp-workbench-station")
                || block == Vars.content.block("dp-laboratory-station");
    }

    private static boolean ownsStation(Block block, String uuid) {
        if (block == Blocks.pulverizer) return Miner.owns(uuid);
        if (block == Blocks.mender) return RepairPoint.owns(uuid);
        if (block == Blocks.phaseWall) return SuppressorTower.owns(uuid);
        if (block == Blocks.beamNode) return Booster.owns(uuid);
        if (block == Blocks.slagIncinerator) return Recycler.owns(uuid);
        if (block == Vars.content.block("dp-workbench-station")) return ArmorWorkbench.owns(uuid);
        if (block == Vars.content.block("dp-laboratory-station")) return Laboratory.owns(uuid);
        return false;
    }
}
