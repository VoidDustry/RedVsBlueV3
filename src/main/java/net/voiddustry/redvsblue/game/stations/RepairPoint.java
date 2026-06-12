package net.voiddustry.redvsblue.game.stations;

import arc.graphics.Color;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import mindustry.world.Tile;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.game.stations.stationData.StationData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.voiddustry.redvsblue.RedVsBluePlugin.players;

public class RepairPoint {
    private static final Map<String, StationData> repairPointsMap = new ConcurrentHashMap<>();

    public static void initTimer() {
        Timer.schedule(() -> repairPointsMap.forEach((owner, pointData) -> {
            int centerX = pointData.tileOn().x * 8;
            int centerY = pointData.tileOn().y * 8;

            for (int i = 0; i < 19; i++) {
                Call.effect(Fx.healWaveDynamic, (float) (centerX + Math.sin(i) * 32), (float) (centerY + Math.cos(i) * 32), 1, Color.red);
            }

            String text = pointData.owner().name + "[gold]'s\n[cyan]Repair Point";
            StationUtils.drawStationName(pointData.tileOn(), text, 1.1F);

            Groups.unit.each(u -> {
                if (u.team == Team.blue) {
                    if (u.dst(centerX, centerY) <= 32) {
                        if (u.health <= u.type.health) {
                            float add = u.type.health/100;
                            if (u.type.health >= 8200) {
                                add = u.type.health/400;
                            } else if (u.type.health <= 130) {
                                add = u.type.health/50;
                            }

                            u.health += add;
                            Call.label("[lime]+" + add, 1, u.x, u.y);
                        }
                    }
                }
            });

        }), 0, 1);
        Timer.schedule(RepairPoint::renderRepairPoints, 0, 1);
    }

    public static void buyRepairPoint(Player player, Tile tile) {
        if (players.get(player.uuid()).getScore() < 10) {
            player.sendMessage(Bundle.format("station.not-enough-money", Bundle.findLocale(player.locale), 10));
        } else {
            if (!repairPointsMap.containsKey(player.uuid())) {
                if (!player.dead()) {
                    Tile playerTileOn = player.tileOn();
                    Tile tileUnderPlayer = Vars.world.tile(playerTileOn.x, playerTileOn.y - 1);
                    if (tile == null) {
                        tile = tileUnderPlayer;
                    }

                    if ((!player.dead() && player.team() == Team.blue && tile.block().isAir()) && tile.floor() != Blocks.empty) {
                        StationData repairPointsData = new StationData(player, tile);
                        repairPointsMap.put(player.uuid(), repairPointsData);
                        Call.constructFinish(tile, Blocks.mender, null, (byte) 0, Team.blue, null);
                        Call.effect(Fx.regenParticle, tile.x*8, tile.y*8, 0, Color.red);
                        players.get(player.uuid()).subtractScore(10);
                    }
                }
            }
        }
    }

    public static void renderRepairPoints() {
        repairPointsMap.forEach((owner, repairPoint) -> {
            if (repairPoint != null) {
                if (repairPoint.tileOn().block() != Blocks.mender || repairPoint.owner().team() != Team.blue) {
                    repairPointsMap.remove(owner);
                    if (repairPoint.tileOn().block() == Blocks.mender) {
                        repairPoint.tileOn().build.kill();
                    }
                }
            }
        });
    }

    public static void clearPoints() {
        repairPointsMap.clear();
    }
}
