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
  @Override
  public void run() {
    try {
      
      for (Unit u : Groups.unit) {
        if (u.team == Team.blue && u.buildPlan() != null) {
          if (u.buildPlan().worldContext) { 
            if (u.isBuilding()) {
              BuildPlan bp = u.buildPlan();
              
              int cost = 0;
              Tile tile = Vars.world.tile(bp.x,bp.y);
              
              for(int i = 0; i < bp.block.requirements.length; i++){
                if (bp.block.requirements[i].item==Items.dormantCyst) {
                  cost = bp.block.requirements[i].amount;
                  break;
                }
              }
                           
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
                      } else if (bp.block == Blocks.radar) {
                        ArmorWorkbench.buyWorkbench(player, tile);
                      } else if (bp.block == Blocks.carbideWall) {
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
