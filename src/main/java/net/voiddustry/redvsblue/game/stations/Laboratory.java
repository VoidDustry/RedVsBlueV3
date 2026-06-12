package net.voiddustry.redvsblue.game.stations;

import arc.graphics.Color;
import arc.util.Timer;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.ui.Menus;
import mindustry.world.Tile;
import net.voiddustry.redvsblue.RedVsBluePlugin;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.PlayerData;
import net.voiddustry.redvsblue.evolution.Evolution;
import net.voiddustry.redvsblue.evolution.Evolutions;
import net.voiddustry.redvsblue.game.stations.stationData.StationData;
import net.voiddustry.redvsblue.util.Utils;
import mindustry.graphics.Layer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.Math;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;


import static net.voiddustry.redvsblue.RedVsBluePlugin.players;

public class Laboratory {
    private static final Map<String, StationData> labsMap = new ConcurrentHashMap<>();

    public static final int evolutionMenu = Menus.registerMenu((player, option) -> {
        if (option == -1) return;

        if (player.unit() == null || player.unit().type() == null) return;
        Evolution evolution = Evolutions.evolutions.get(player.unit().type().name);

        if (evolution == null || option < 0 || option >= evolution.evolutions.length) return;
        String selectedEvolution = evolution.evolutions[option];
        Evolution evolutionOption = Evolutions.evolutions.get(selectedEvolution);
        if (evolutionOption == null) return;

        PlayerData playerData = players.get(player.uuid());
        if (playerData == null) return;

        if (playerData.getScore() >= ((int)(evolutionOption.cost*getMultiplier(evolutionOption, player)))) {
            if (player.unit() != null && (player.tileOn().block() == Blocks.air || evolutionOption.unitType.flying==true || evolutionOption.unitType.canBoost == true || evolutionOption.unitType.groundLayer==Layer.legUnit)) {
                Unit unit = evolutionOption.unitType.spawn(Team.blue, player.x(), player.y());
                unit.health = unit.type.health/2;

                if (!unit.dead()) {
                    Unit oldUnit = playerData.getUnit();
                    playerData.setUnit(unit);

                    player.unit(unit);
                    oldUnit.kill();

                    playerData.subtractScore((int)(getMultiplier(evolutionOption, player)*evolutionOption.cost));
                    playerData.setEvolutionStage(evolutionOption.tier);
                    playerData.setLastEvolutionTime(Instant.now().getEpochSecond());

                    Utils.sendBundled("game.evolved", player.name(), selectedEvolution);
                }
            }
        }
    });

    public static void initTimer() {

        Timer.schedule(() -> {
            renderLabs();


            Groups.player.each(p -> {
                if (players.containsKey(p.uuid())) {
                    players.get(p.uuid()).setCanEvolve(false);
                }

                labsMap.forEach((uuid, lab) -> {
                    int centerX = lab.tileOn().x * 8;
                    int centerY = lab.tileOn().y * 8;

                    if (p.team() == Team.blue && !(p.unit() == null)) {
                        if (p.dst(centerX, centerY) <= 48) {
                            if(Vars.world.tile(Math.round(p.mouseX / 8), Math.round(p.mouseY / 8)) != null && Vars.world.tile(Math.round(p.mouseX / 8), Math.round(p.mouseY / 8)).block() == Blocks.carbideWall && p.shooting) {
                                Locale locale = Bundle.findLocale(p.locale());

                                Evolution evolution = Evolutions.evolutions.get(p.unit().type().name);

                                if (!(evolution == null)) {
    
                                    String[][] buttons = new String[evolution.evolutions.length][1];
    
                                    for (int i = 0; i < evolution.evolutions.length; i++) {
                                        float multiplier = getMultiplier(evolution.evolutions[i], p);
                                        int cost = (int)(Evolutions.evolutions.get(evolution.evolutions[i]).cost*multiplier);

                                        String textColor = "";

                                       if (multiplier > 1 && multiplier <= 1.99) {
                                            textColor = "[orange]";
                                        } else if (cost>Evolutions.evolutions.get(evolution.evolutions[i]).cost) {
                                            textColor = "[red]";
                                        } else if (cost<Evolutions.evolutions.get(evolution.evolutions[i]).cost) {
                                            textColor = "[green]";
                                        } else {
                                            textColor = "[yellow]";
                                        }
                                        buttons[i][0] = Bundle.format("menu.evolution.evolve", locale, evolution.evolutions[i],(textColor+cost+" - "+(multiplier*100)+"%"));
                                    }
    
                                    Call.menu(p.con, evolutionMenu, Bundle.get("menu.evolution.title", locale), Bundle.format("menu.evolution.message", locale, players.get(p.uuid()).getEvolutionStage(), Bundle.get("evolution.branch.initial", locale)), buttons);
                                }
                            }
                            if (!(players.get(p.uuid()) == null)) {
                            players.get(p.uuid()).setCanEvolve(true);
                            }
                            Call.infoPopup(p.con, Bundle.get("evolution.evolution-available", p.locale), 0.5F, 0, 0, 0, -200, 0);
                        }
                    }
                });
            });

            labsMap.forEach((uuid, lab) -> {

                int centerX = lab.tileOn().x * 8;
                int centerY = lab.tileOn().y * 8;

                for (int i = 0; i < 19; i++) {
                    Call.effect(Fx.vaporSmall, (float) (centerX + Math.sin(i) * 48), (float) (centerY + Math.cos(i) * 48), 1, Color.purple);
                }
                StationUtils.drawStationName(lab.tileOn(), lab.owner().name + "[gold]'s\n[purple]Lab", 0.6F);
            });
        }, 0, 0.5F);
    }

    public static float getMultiplier(Evolution evo, Player player) {
        if (RedVsBluePlugin.players.get(player.uuid()) == null) {
            return 4;
        }
        double timeSinceLastEvo = Instant.now().getEpochSecond()-RedVsBluePlugin.players.get(player.uuid()).getLastEvolutionTime();
        int stage = evo.stage;
        float multiplier = 0;
        if (timeSinceLastEvo<180) {
            multiplier = (float)((180-timeSinceLastEvo)/1000);
            multiplier = multiplier + (((float)Math.sqrt(evo.cost))/evo.cost)*multiplier;
        }
        if (RedVsBluePlugin.stage == stage) {
            multiplier = multiplier+1f;
        } else if (RedVsBluePlugin.stage > stage) {
            multiplier = multiplier+0.75f;
        } else {
            multiplier = multiplier+1+((float)Math.pow(2,stage-RedVsBluePlugin.stage)/4);
        }
        BigDecimal bd = new BigDecimal(String.valueOf(multiplier));
        bd = bd.setScale(3, RoundingMode.HALF_UP);
        multiplier = bd.floatValue();
        return multiplier;
    }

    public static float getMultiplier(String evolution, Player player) {
        return getMultiplier(Evolutions.evolutions.get(evolution), player);
    }

    public static void buyLab(Player player, Tile tile) {
        if (players.get(player.uuid()).getScore() < 7) {
            player.sendMessage(Bundle.format("station.not-enough-money", Bundle.findLocale(player.locale), 7));
        } else {
            if (!labsMap.containsKey(player.uuid())) {
                if (!player.dead()) {
                    Tile playerTileOn = player.tileOn();
                    Tile tileUnderPlayer = Vars.world.tile(playerTileOn.x, playerTileOn.y - 1);
                    if (tile == null) {
                        tile = tileUnderPlayer;
                    }

                    if ((!player.dead() && player.team() == Team.blue && tile.block().isAir()) && tile.floor() != Blocks.empty) {
                        StationData laboratoryData = new StationData(player, tile);
                        labsMap.put(player.uuid(), laboratoryData);
                        Call.constructFinish(tile, Blocks.carbideWall, null, (byte) 0, Team.blue, null);
                        Call.effect(Fx.regenParticle, tile.x*8, tile.y*8, 0, Color.red);
                        players.get(player.uuid()).subtractScore(7);
                    }
                }
            }
        }
    }

    public static void renderLabs() {
        labsMap.forEach((owner, lab) -> {
            if (lab != null) {
                if (lab.tileOn().block() != Blocks.carbideWall || lab.owner().team() != Team.blue) {
                    labsMap.remove(owner);
                    if (lab.tileOn().block() == Blocks.carbideWall) {
                        lab.tileOn().build.kill();
                    }
                }
            }
        });
    }

    public static void clearLabs() {
        labsMap.clear();
    }
}
