package net.voiddustry.redvsblue.game.building;

import java.lang.Math;
import java.lang.Runnable;
import arc.util.Timer;
import java.util.HashMap;
import java.util.WeakHashMap;
import arc.*;
import arc.util.*;
import arc.struct.Seq;
import arc.math.geom.QuadTree.*;
import mindustry.*;
import mindustry.world.Tile;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.*;
import mindustry.world.Block;
import mindustry.Vars;
import mindustry.game.Rules;
import mindustry.content.*;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.gen.Call;
import mindustry.game.Team;
import mindustry.mod.*;
import mindustry.net.Administration.*;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.type.Category;

import net.voiddustry.redvsblue.RedVsBluePlugin;
import net.voiddustry.redvsblue.game.stations.*;


// updates construct blocks instead of the game doing it, original in usurvplugin
public class UpdateConstructBlocks implements Runnable {
  // false from map load until the blue core is killed and player data is reset
  public static volatile boolean open = false;

  public static void start() {
    Timer.schedule(new UpdateConstructBlocks(), 0f, 0.2f);
    // a rejected plan is dropped by NetServer.clientSnapshot, which also removes it from the client's queue
    Vars.netServer.admins.addActionFilter(a -> a.player == null || (a.type == ActionType.placeBlock ? !open || legal(a.player, a.block, a.tile) : a.type != ActionType.breakBlock));
    Events.run(Trigger.update, () -> Groups.unit.each(u -> { while (u.plans().remove(p -> p.breaking)); }));
  }

  static int cost(Block block) {
    for (ItemStack stack : block.requirements) if (stack.item == Items.dormantCyst) return stack.amount;
    return 0;
  }

  static boolean legal(Player player, Block block, Tile tile) {
    var data = RedVsBluePlugin.players.get(player.uuid());
    int cost = cost(block);
    if (player.team() != Team.blue || data == null || cost == 0 || data.getScore() < cost || Vars.state.rules.isBanned(block)) return false;
    String id = player.uuid();
    boolean owned;
    if (block == Blocks.pulverizer) owned = Miner.owns(id);
    else if (block == Blocks.mender) owned = RepairPoint.owns(id);
    else if (block == Blocks.phaseWall) owned = SuppressorTower.owns(id);
    else if (block == Blocks.beamNode) owned = Booster.owns(id);
    else if (block == Blocks.slagIncinerator) owned = Recycler.owns(id);
    else if (block == Vars.content.block("dp-workbench-station")) owned = ArmorWorkbench.owns(id);
    else if (block == Vars.content.block("dp-laboratory-station")) owned = Laboratory.owns(id);
    else return true;
    return !owned && tile.block().isAir() && tile.floor() != Blocks.empty;
  }

  @Override
  public void run() {
    if (!open) return;
    try {
      
      for (Unit u : Groups.unit) {
        if (u.team == Team.blue && u.buildPlan() != null) {
          if (u.buildPlan().worldContext) { 
            if (u.isBuilding()) {
              BuildPlan bp = u.buildPlan();
              
              int cost = cost(bp.block);
              Tile tile = Vars.world.tile(bp.x,bp.y);
                           
              if (u.getPlayer() != null && u.buildPlan().placeable(Team.blue) && u.dst(bp.x*8,bp.y*8)<Vars.buildingRange) {
                Player player = u.getPlayer();
                if (u.buildPlan().breaking == false && u.isBuilding()) {
                  //Log.info("Attempting to consume " + cost + " ,from player " + player);
                  if (RedVsBluePlugin.players.get(player.uuid()).getScore()>=cost && cost!=0) {
                    //Log.info("finishing construction");
                    if (bp.block.category == Category.logic) {
                      if (bp.block == Blocks.pulverizer) {
                        Miner.buyMiner(player, tile);
                      } else if (bp.block == Blocks.mender) {
                        RepairPoint.buyRepairPoint(player, tile);
                      } else if (bp.block == Blocks.phaseWall) {
                        SuppressorTower.buyTower(player, tile);
                      } else if (bp.block == Vars.content.block("dp-workbench-station")) {
                        ArmorWorkbench.buyWorkbench(player, tile);
                      } else if (bp.block == Vars.content.block("dp-laboratory-station")) {
                        Laboratory.buyLab(player, tile);
                      } else if (bp.block == Blocks.beamNode) {
                        Booster.buyBooster(player, tile);
                      } else if (bp.block == Blocks.slagIncinerator) {
                        Recycler.buyRecycler(player, tile);
                      }
                    } else {
                    RedVsBluePlugin.players.get(player.uuid()).subtractScore(cost);
                    Call.constructFinish(tile,bp.block,null,(byte)bp.rotation,player.team(),bp.config);
                    tile.build.placed();
                    }
                  } else {
                    if (tile.block() instanceof ConstructBlock cb) {
                      tile.setNet(Blocks.air,Team.derelict,0);
                    }
                  }
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      Log.info("goofy ahh exception in the building system");
      e.printStackTrace();
    }
  }
}
