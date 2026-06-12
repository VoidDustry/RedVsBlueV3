package net.voiddustry.redvsblue.game.stations;

import arc.graphics.Color;
import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.content.Fx;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import mindustry.world.Tile;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.game.stations.stationData.StationData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.voiddustry.redvsblue.RedVsBluePlugin.players;

public class ArmorWorkbench {
    private static final Map<String, StationData> workbenches = new ConcurrentHashMap<>();

    public static void initTimer() {
        Timer.schedule(() -> workbenches.forEach((owner, pointData) -> {
            int centerX = pointData.tileOn().x * 8;
            int centerY = pointData.tileOn().y * 8;

            for (int i = 0; i < 38; i++) {
                Call.effect(Fx.pointHit, (float) (centerX + Math.sin(i) * 64), (float) (centerY + Math.cos(i) * 64), 1, Color.blue);
            }
            String text = pointData.owner().name + "[gold]'s\n[blue]Workbench";
            StationUtils.drawStationName(pointData.tileOn(), text, 0.4F);
        }), 0, 0.25F);
        Timer.schedule(() -> workbenches.forEach((owner, pointData) -> {
            int centerX = pointData.tileOn().x * 8;
            int centerY = pointData.tileOn().y * 8;

            Groups.player.each(p -> {
                if (!(p.unit() == null)) {
                    if (p.team() == Team.blue) {
                        if (p.dst(centerX, centerY) <= 64) {
                            if (p.unit().shield >= 0 && p.unit().shield < 30) {
                                p.unit().shield += 10;
                                Call.label("[blue]+10", 1, p.x, p.y);
                            }
    
                            if(Vars.world.tile(Math.round(p.mouseX / 8), Math.round(p.mouseY / 8)) != null && Vars.world.tile(Math.round(p.mouseX / 8), Math.round(p.mouseY / 8)).block() == Blocks.radar && p.shooting){
    
                                //int maxShield = (int) p.unit().type.health; // /50;
                                float maxShield = p.unit().type.health;
                                if (p.unit().type.health >= 17000) {
                                    maxShield = p.unit().type.health/3;
                                } else if (p.unit().type.health <= 290) {
                                    maxShield = p.unit().type.health*4;
                                } else if (p.unit().type == UnitTypes.aegires) {
                                    maxShield = p.unit().type.health/2;
                                }
    
                                int shieldPerPoint = (int) maxShield/20;
    
                                float finalMaxShield = maxShield;
                                int menu = Menus.registerMenu((player, option) -> {
                                    if (option < 0) {
                                        return;
                                    }

                                    if (option == 0 && players.get(p.uuid()).getScore() >= 1 && p.unit().shield <= (int) finalMaxShield) {
                                        p.unit().shield = p.unit().shield + shieldPerPoint;
                                        players.get(p.uuid()).subtractScore(1);
    
                                        Call.label("[royal]+" + shieldPerPoint, 3, p.x, p.y);
                                    }
                                });
    
                                String[][] buttons = new String[][]{
                                        {
                                            Bundle.format("stations.workbench.buy-armor", Bundle.findLocale(p.locale), shieldPerPoint),
                                        },
                                        {
                                            Bundle.get("stations.buttons.close", p.locale)
                                        }
                                };
    
                                openMenu(p, menu, buttons);
                            }
                        }
                    }
                }
            });
        }), 0, 1);
        Timer.schedule(ArmorWorkbench::renderWorkbenches, 0, 1);
    }

    public static void buyWorkbench(Player player, Tile tile) {
        if (players.get(player.uuid()).getScore() < 8) {
            player.sendMessage(Bundle.format("station.not-enough-money", Bundle.findLocale(player.locale), 8));
        } else {
            if (!workbenches.containsKey(player.uuid())) {
                if (!player.dead()) {
                    Tile playerTileOn = player.tileOn();
                    Tile tileUnderPlayer = Vars.world.tile(playerTileOn.x, playerTileOn.y - 1);
                    if (tile == null) {
                        tile = tileUnderPlayer;
                    }
                    if ((!player.dead() && player.team() == Team.blue && tile.block().isAir()) && tile.floor() != Blocks.empty) {
                        StationData workbenchData = new StationData(player, tile);
                        workbenches.put(player.uuid(), workbenchData);
                        Call.constructFinish(tile, Blocks.radar, null, (byte) 0, Team.blue, null);
                        Call.effect(Fx.regenParticle, tile.x * 8, tile.y * 8, 0, Color.red);
                        players.get(player.uuid()).subtractScore(8);
                    }
                }
            }
        }
    }

    public static void renderWorkbenches() {
        workbenches.forEach((owner, workbench) -> {
            if (workbench != null) {
                if (workbench.tileOn().block() != Blocks.radar || workbench.owner().team() != Team.blue) {
                    workbenches.remove(owner);
                    if (workbench.tileOn().block() == Blocks.radar) {
                        workbench.tileOn().build.kill();
                    }
                }
            }
        });
    }

    private static void openMenu(Player p, int menu, String[][] buttons) {
        Call.menu(p.con, menu, "[royal]" + Bundle.get("stations.buttons.workbench"), Bundle.format("stations.workbench.unit-armor", Bundle.findLocale(p.locale), p.unit().shield), buttons);
    }

    public static void clearWorkbenches() {
        workbenches.clear();
    }
}
