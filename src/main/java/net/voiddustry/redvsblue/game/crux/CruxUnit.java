package net.voiddustry.redvsblue.game.crux;

import arc.util.Timer;
import arc.Events;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import net.voiddustry.redvsblue.RedVsBluePlugin;
import net.voiddustry.redvsblue.util.Utils;

import java.util.Random;
import java.util.HashMap;
import java.lang.Math;

public class CruxUnit {

    private static final HashMap<Unit, Long> spawnTimes = new HashMap<>();

    public static void addEvent() {
        Events.on(EventType.UnitDestroyEvent.class, event -> {
                spawnTimes.remove(event.unit);
        });
    }
    
    public static void callSpawn(Player player) {
        
        UnitType type = ClassChooseMenu.selectedUnit.get(player.uuid());

        if (type != null) {
            Tile cruxSpawn = RedVsBluePlugin.redSpawns.random();

            if (cruxSpawn != null && cruxSpawn.block() != null) {
                Call.logicExplosion(Team.crux, cruxSpawn.x*8, cruxSpawn.y*8, 80, 999999, true, true, true, true);
            }
            Unit unit = type.spawn(Team.crux, cruxSpawn);

            if (unit != null && !unit.dead) {
                unit.health = Integer.MAX_VALUE;
                unit.apply(StatusEffects.overclock, 180);;

                //unit.apply(Vars.content.statusEffect("superShielded"), 120f);
                unit.apply(Vars.content.statusEffect("shielded"), 300f);

                if (unit.type == UnitTypes.corvus) {
                    if (RedVsBluePlugin.stage == 11) {
                        unit.apply(StatusEffects.electrified, 600f);
                        unit.apply(StatusEffects.sapped, 99999f);
                        unit.apply(StatusEffects.freezing, 99999f);
                    }
                }

                Timer.schedule(() -> {
                    if (unit.type == UnitTypes.crawler) {
                        unit.health = 20;
                        unit.addItem(Items.pyratite, 10);
                    } else if (unit.type == UnitTypes.merui) {
                        unit.health = 100;
                        unit.addItem(Items.pyratite, 99);
                    } else if (unit.type == UnitTypes.mace) {
                        unit.health = 100;
                    } else if (unit.type == UnitTypes.dagger) {
                        unit.health = 100;
                    } else {
                        unit.health = unit.type.health;
                    }

                }, 3);

                player.unit(unit);
                unit.spawnedByCore = true;
            }
        }
    }

    private static void spawnCrux() {
        UnitType unitType = UnitTypes.alpha;
        if (!ClassChooseMenu.units.isEmpty()) {
            unitType = ClassChooseMenu.units.keys().toSeq().get(Utils.getRandomInt(0, ClassChooseMenu.units.size)); //Utils.getRandomInt(0, ClassChooseMenu.units.size)
        }
        spawnCrux(unitType);
    }

    private static void spawnCrux(UnitType unitType) {
        if (Utils.gameRun) {
            Tile cruxSpawn = RedVsBluePlugin.redSpawns.random();

            if (cruxSpawn != null && cruxSpawn.block() != null) {
                Call.logicExplosion(Team.crux, cruxSpawn.x*8, cruxSpawn.y*8, 80, 999999, true, true, true, true);
            }

            if (RedVsBluePlugin.stage >= 11) {

                boolean s11BossDead = true;
                
                for (Unit u : Groups.unit) {
                    if (u.type==UnitTypes.evoke) {
                        s11BossDead = false;
                    }
                }

                if (s11BossDead) {
                    Random rand = new Random();
                    if (rand.nextInt(20) == 1) {
                        UnitTypes.latum.spawn(Team.crux, cruxSpawn);
                    }
                    if (rand.nextInt(13) == 1) {
                        UnitTypes.conquer.spawn(Team.crux, cruxSpawn);
                    }
                    if (rand.nextInt(7) == 1) {
                        UnitTypes.tecta.spawn(Team.crux, cruxSpawn);
                    }
                }
            }
            
            Unit unit = unitType.spawn(Team.crux, cruxSpawn);
            spawnTimes.put(unit, System.currentTimeMillis());

            //unit.apply(Vars.content.statusEffect("superShielded"), 120f);
            unit.apply(Vars.content.statusEffect("shielded"), 300f);

            if (unit.type == UnitTypes.corvus) {
                if (RedVsBluePlugin.stage == 11) {
                    unit.apply(StatusEffects.electrified, 600f);
                    unit.apply(StatusEffects.sapped, 99999f);
                    unit.apply(StatusEffects.freezing, 99999f);
                }
            }

            Timer.schedule(() -> {
                if (unit.type == UnitTypes.crawler) {
                    unit.health = 20;
                    unit.addItem(Items.pyratite, 10);
                } else if (unit.type == UnitTypes.merui) {
                    unit.health = 100;
                    unit.addItem(Items.pyratite, 99);
                } else if (unit.type == UnitTypes.mace) {
                    unit.health = 100;
                } else if (unit.type == UnitTypes.dagger) {
                    unit.health = 100;
                } else {
                    unit.health = unit.type.health;
                }
            }, 2);
        }
    };


    public static void checkUnitCount() {
        if (Utils.gameRun) {
            int players = Groups.player.size();
            int stage = RedVsBluePlugin.stage;

            int cruxUnitsCount = Math.round((players + stage) / (float) 3);

            final HashMap<UnitType, Integer> cruxUnits = new HashMap<>();

            if (!ClassChooseMenu.units.isEmpty()) {
                ClassChooseMenu.units.keys().toSeq().each(type -> {
                    Groups.unit.each(u -> {
                        if (u.team == Team.crux && u.type == type) {
                        
                            if ((!(spawnTimes.get(u) == null)) && System.currentTimeMillis()-spawnTimes.get(u)<45000) {
                                if (cruxUnits.get(type) == null) {
                                    cruxUnits.put(type, 0);
                                } else {
                                    cruxUnits.put(type, cruxUnits.get(type)+1);
                                }
                            }
                        }
                    });
                    if (cruxUnits.get(type) == null || cruxUnits.get(type) < cruxUnitsCount/ClassChooseMenu.units.keys().toSeq().size) {
                        spawnCrux(type);
                    }
                });
            }
        }
    }
}
