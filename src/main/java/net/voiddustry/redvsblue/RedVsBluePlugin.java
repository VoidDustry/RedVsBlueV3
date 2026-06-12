package net.voiddustry.redvsblue;

import arc.Events;
import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.*;


import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.*;
import arc.math.*;
import mindustry.Vars;
import mindustry.type.unit.*;
import mindustry.ai.Pathfinder;
import mindustry.ai.types.*;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.type.*;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.type.UnitType;
import mindustry.ui.Menus;
import mindustry.world.Tile;
import mindustry.net.*;
import net.voiddustry.redvsblue.admin.Admin;
import net.voiddustry.redvsblue.ai.AirAI;
import net.voiddustry.redvsblue.ai.BluePlayerTarget;
import net.voiddustry.redvsblue.ai.StalkerGroundAI;
import net.voiddustry.redvsblue.ai.StalkerSuicideAI;
import net.voiddustry.redvsblue.evolution.Evolution;
import net.voiddustry.redvsblue.evolution.Evolutions;
import net.voiddustry.redvsblue.game.crux.*;
import net.voiddustry.redvsblue.game.stations.StationsMenu;
import net.voiddustry.redvsblue.game.stations.Laboratory;
import net.voiddustry.redvsblue.game.building.UpdateConstructBlocks;
import net.voiddustry.redvsblue.game.starting_menu.StartingMenu;
import net.voiddustry.redvsblue.game.stations.*;
import net.voiddustry.redvsblue.game.units.SpecialUnits;
import net.voiddustry.redvsblue.player.Hud;
import net.voiddustry.redvsblue.player.Premium;
import net.voiddustry.redvsblue.util.MapVote;
import net.voiddustry.redvsblue.util.UnitsConfig;
import net.voiddustry.redvsblue.util.Utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.lang.Math;
import java.time.Instant;

import static net.voiddustry.redvsblue.util.MapVote.callMapVoting;
import static net.voiddustry.redvsblue.util.Utils.*;

public class RedVsBluePlugin extends Plugin {
    public static final int bluePlayerTargeting;

    static {
        Pathfinder.fieldTypes.add(BluePlayerTarget::new);
        bluePlayerTargeting = Pathfinder.fieldTypes.size - 1;
    }

    public static final HashMap<String, PlayerData> players = new HashMap<>();

    public float blueSpawnX;
    public float blueSpawnY;

    public static Seq<Tile> redSpawns = new Seq<>();

    private static int tick = 0;

    public static int stage = 0;
    public static float stageTimer = 0;
    public static boolean playing = false;

    public static boolean stage11 = false;

    public int restartCounter = 0;
    public int gamesUntilRestart = 2000000;

    static Timer.Task task = new Timer.Task() {
        @Override
        public void run() {
            if (stage != 10) {
                stage++;
                stageTimer = 300;
                spawnBoss();
                announceBundled("game.new-stage", 15, stage);
                ClassChooseMenu.updateUnitsMap();
            } else {
                 //calculate blue unit value for stage 11
                int blueUnitValue = 0;
                float typeModifier = 1;
                for (Unit u : Groups.unit) {
                    if (u.team==Team.blue) {
                        typeModifier = 1;
                        if (u.type == UnitTypes.quad) {
                            typeModifier = 2;
                        } else if (u.type == UnitTypes.sei) {
                            typeModifier = 2;
                        } else if (u.type == UnitTypes.aegires) {
                            typeModifier = 3;
                        } else if (u.type == UnitTypes.omura) {
                            typeModifier = 2;
                        } else if (u.type == UnitTypes.navanax) {
                            typeModifier = 1.5f;
                        }
                        blueUnitValue = blueUnitValue + ((int)(u.type.health*typeModifier));
                    }
                }
                Log.info("Blue unit value for stage 11: " + blueUnitValue);
                if (blueUnitValue < 100000) {
                gameOver(Team.blue);
                } else {
                    stage11 = true;
                    announceBundled("game.stage11", 5);
                    Timer timer = new Timer();
                    timer.schedule(() -> {
                        stage++;
                        stageTimer = 300;
                        spawnBoss();
                        announceBundled("game.new-stage", 15, 11);
                        ClassChooseMenu.updateUnitsMap();
                    }, 6);
                }
            }
            if (stage >= 12) {
                gameOver(Team.blue);
            }
        }
    };

    @Override
    public void init() {

        Log.info("&gRedVsBlue Plugin &rStarted!");


        Utils.initRules();
        Utils.initTimers();
        Utils.loadContent();
        SpecialUnits.init();
        SpawnEffect.initEffect();
        Boss.forEachBoss();
        Premium.init();
        CruxUnit.addEvent();

        Fi poolDir = Vars.dataDirectory.child("bosses");
        poolDir.mkdirs();
        

        for (UnitType unit : Vars.content.units()) {
            if (unit == UnitTypes.crawler) {
                unit.aiController = StalkerSuicideAI::new;
            }
            if (!unit.flying) {
                unit.aiController = StalkerGroundAI::new;
            }
            if (unit.flying) {
                unit.aiController = AirAI::new;
            }
            if (unit.naval) {
                unit.flying = true;
            }
        }


        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            if (playing) {
                if (players.containsKey(player.uuid())) {
                    PlayerData data = players.get(player.uuid());
                    player.team(data.getTeam());
                    if (player.team() == Team.blue) {
                        player.unit(data.getUnit());
                    }

                } else {
                    players.put(player.uuid(), new PlayerData(player));
                    PlayerData data = players.get(player.uuid());
                    Vars.world.tile(((int)blueSpawnX)/8,((int)blueSpawnY)/8).setNet(Blocks.air,Team.derelict,0);
                    Unit unit = getStartingUnit().spawn(Team.blue, blueSpawnX, blueSpawnY);

                    data.setUnit(unit);
                    player.unit(unit);
                }
                if (playing && player.team() == Team.crux) {
                    CruxUnit.callSpawn(player);
                }
            }

            int menu = Menus.registerMenu(((player1, option) -> {}));

            Call.menu(player.con, menu, Bundle.get("welcome", player.locale), Bundle.get("welcome.text", player.locale), new String[][]{{Bundle.get("stations.buttons.close", player.locale)}});
        });

        Events.on(EventType.PlayerChatEvent.class, event -> {
            if (Utils.voting) {
                if (Strings.canParseInt(event.message)) {
                    MapVote.registerVote(event.player, Strings.parseInt(event.message));
                }
            }
        });

        //kill credit
        HashMap<Unit, Unit> spawnedUnitOwnership = new HashMap<>();
        HashMap<Unit, Player> killCredit = new HashMap<>();

        //situations where kills should not be registered
        StatusEffect noRegisterKills = new StatusEffect("noRegisterKills") {{
            show = false;
        }};
        

        Events.on(EventType.UnitDamageEvent.class, event -> {
            if (event.unit != null && event.bullet.owner() instanceof Unit damager) {
                if ((damager.isPlayer() || (spawnedUnitOwnership.get(damager) != null && spawnedUnitOwnership.get(damager).isPlayer())) && damager.team == Team.blue) {
                    Player damagerPlayer;
                    if (damager.isPlayer()) {
                        damagerPlayer = damager.getPlayer();
                    } else {
                        damagerPlayer = spawnedUnitOwnership.get(damager).getPlayer();
                    }
                    if (!(damagerPlayer == null)) {
                        killCredit.put(event.unit, damagerPlayer);
                    }
                }
            }
        });

        Events.on(EventType.PlayEvent.class, event -> {
            Utils.initRules();
        });

        
        //blue kill registration
        Events.on(EventType.UnitDestroyEvent.class, event -> {
            
            if (!(event.unit.hasEffect(Vars.content.statusEffect("noRegisterKills"))) && !(event.unit.type instanceof MissileUnitType)) {
                Player killerPlayer = killCredit.get(event.unit);
                killCredit.remove(event.unit);
                
                if (killerPlayer == null) {
                    float minDist = 69420;
                    for (Player p : Groups.player) {
                        if (((!(p.unit() == null)) && event.unit.dst(p)<minDist) && (!(event.unit.team == p.team()))) {
                            if (event.unit.dst(p) < ((p.unit().type.range)*1.2+p.unit().type.speed*8))
                                killerPlayer=p;
                                minDist = event.unit.dst(p);
                        }
                    }
                }
                
                if (killerPlayer != null) {
                    if (killerPlayer.team() == Team.blue) {
                        PlayerData data = players.get(killerPlayer.uuid());
                        if (!(data == null)) {
                            players.get(killerPlayer.uuid()).addScore(data.getLevel());
                            Call.label(killerPlayer.con, "[lime]+" + data.getLevel(), 2, event.unit.x, event.unit.y);
                            data.addExp(1);
                            processLevel(killerPlayer, data);
                        }
                    }
                }
            }
        });

        //crux kill registration
        Events.on(EventType.UnitBulletDestroyEvent.class, event -> {
            if (event.unit != null && event.bullet.owner() instanceof Unit killer) {
                if (killer.isPlayer() && killer.team == Team.crux) {
                    PlayerData data = players.get(killer.getPlayer().uuid());
                    if (!(data == null)) {
                        data.addKill();
                        Call.label(killer.getPlayer().con, "[scarlet]+1", 2, event.unit.x, event.unit.y);
                        if (data.getKills() >= 2) {
                            Boss.spawnBoss(killer.getPlayer());
                        }
                    }

                }
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
        });

        //random boss
        Events.on(EventType.ContentPatchLoadEvent.class, event -> {
            Seq<Fi> pool = poolDir.findAll(f -> true);

            if(pool.isEmpty()){
                Log.info("No boss patch files found.");
                return;
            }

            Fi chosen = pool.random();

            try{
                String patchText = Jval.read(chosen.readString()).toString(Jformat.plain);

                event.patches.addUnique(patchText);

                Log.info("Selected random patch: @", chosen.name());
            }catch(Throwable t){
                Log.info("Invalid random patch file: @", chosen.name(), t);
            }
        });



        Events.on(EventType.UnitDestroyEvent.class, event -> {

            spawnedUnitOwnership.remove(event.unit);
            killCredit.remove(event.unit);
            
            if (event.unit.isPlayer()) {
                if (event.unit.team() == Team.blue) {
                    event.unit.getPlayer().team(Team.crux);
                    PlayerData data = players.get(event.unit.getPlayer().uuid());
                    if (!(data == null)) {
                        data.setTeam(Team.crux);
                        data.setScore(0);
                        data.setKills(0);
                    }

                    UnitTypes.renale.spawn(Team.malis, event.unit.x, event.unit.y).kill();

                    ClassChooseMenu.selectedUnit.put(event.unit.getPlayer().uuid(), UnitTypes.dagger);
                    CruxUnit.callSpawn(event.unit.getPlayer());
                } else if (event.unit.getPlayer().team() == Team.crux) {
                  CruxUnit.callSpawn(event.unit.getPlayer());
                  Player player = event.unit.getPlayer();
                  Boss.bosses.forEach(boss -> {
                      if (player == boss.player) {
                          Boss.bosses.remove(boss);
                      }
                  });
                }
            }
            gameOverCheck();
        });


        Timer.schedule(() -> {
            if (playing) {
                Hud.update();
            }
        },0.2f,0.2f);




        

        Events.on(EventType.WorldLoadEvent.class, event -> {
            Miner.clearMiners();
            RepairPoint.clearPoints();
            Laboratory.clearLabs();
            Booster.clearBoosters();
            ArmorWorkbench.clearWorkbenches();
            Recycler.clearRecyclers();
            SuppressorTower.clearTowers();
            Boss.bosses.clear();

            initRules();

            StartingMenu.canOpenMenu = true;


            Groups.player.each(p -> {
                PlayerData data = players.get(p.uuid());
                if (!(data == null)) {
                    data.setUnit(null);
                    data.setExp(0);
                    data.setLevel(1);
                    data.setScore(0);
                }
            });
            Log.info("Games until restart: "+(gamesUntilRestart-restartCounter));
            restartCounter = restartCounter+1;
            if (restartCounter >= gamesUntilRestart) {
                Log.info("[scarlet]The server is restarting");
                //net.dispose(); where do you find this mythical method
                Groups.player.each(p -> p.kick("[scarlet]Server is restarting"));
                System.exit(0);
            }
            
        });

        Events.on(EventType.WorldLoadEvent.class, event -> Timer.schedule(() -> {

            players.clear();

            Vars.state.rules.canGameOver = false;
            Vars.state.rules.unitCap = 32;

            Timer.schedule(() -> {
                UpdateConstructBlocks updateConstructBlocks = new UpdateConstructBlocks();
                Timer.schedule(updateConstructBlocks, 10f, 0.2f);
                Vars.state.rules.bannedBlocks.clear();
                initRules();
            }, 0.5f);

            Building core = Vars.state.teams.cores(Team.blue).first();

            redSpawns.clear();

            blueSpawnX = core.x();
            blueSpawnY = core.y();

            stage11 = false;

            Vars.state.teams.cores(Team.blue).each(Building::kill);

            for (int x = 0; x < Vars.state.map.width; x++) {
                for (int y = 0; y < Vars.state.map.height; y++) {
                    Tile tile = Vars.world.tile(x, y);
                    if (tile.overlay() == Blocks.spawn) {
                        redSpawns.add(tile);
                        break;
                    }
                }
            }

            Timer timer = new Timer();
            timer.scheduleTask(task, 300, 300);


            Groups.player.each(player -> {
                if (player != null) {
                    player.team(Team.blue);
                    players.put(player.uuid(), new PlayerData(player));

                    Unit unit = getStartingUnit().spawn(Team.blue, blueSpawnX, blueSpawnY);
                    PlayerData data = players.get(player.uuid());
                    data.setUnit(unit);

                }
            });

            playing = true;
            gameRun = false;
            gameover = false;
            hardcore = false;
            stage = 1;
            stageTimer = 300;
            voting = false;
            StartingMenu.canOpenMenu = true;

            ClassChooseMenu.selectedUnit.clear();
            ClassChooseMenu.updateUnitsMap();

            initRules();
            launchGameStartTimer();

            spawnedUnitOwnership.clear();
            killCredit.clear();


            Call.setRules(Vars.state.rules);


        }, 10));

    //    StatusEffect superShielded = new StatusEffect("superShielded") {{
       //     show = false;
        //    healthMultiplier = 9;
       // }};

        Events.run(EventType.Trigger.update, () -> {
            tick++;
            if (playing) {

                //shitty redspawn updater
                if (Vars.state.rules.objectiveFlags.contains("updateRedSpawns")) {
                    Vars.state.rules.objectiveFlags.remove("updateRedSpawns");
                    
                    RedVsBluePlugin.redSpawns.each(spawnpoint -> {
                        if (spawnpoint != RedVsBluePlugin.redSpawns.firstOpt()) {
                            RedVsBluePlugin.redSpawns.remove(spawnpoint);
                        }
                    });
                    Groups.build.each(bildeng -> {
                        if (bildeng.block == Blocks.reinforcedLiquidRouter && bildeng.team == Team.all[100]) {
                            RedVsBluePlugin.redSpawns.add(Vars.world.tile((((int)bildeng.x)/8),(((int)bildeng.y)/8)));
                            Vars.world.tile((((int)bildeng.x)/8),(((int)bildeng.y)/8)).setBlock(Blocks.air);
                        }
                    });
                    RedVsBluePlugin.redSpawns.remove(RedVsBluePlugin.redSpawns.firstOpt());  
                }
                

                CruxUnit.checkUnitCount();

                if(tick%3==0){

                    // draw hitboxes 
                    Groups.unit.each(u -> Call.label("[orange]X", 0.05F, u.x, u.y));
                    //register missiles
                    for (Unit unit : Groups.unit) {
                    if (unit.type instanceof MissileUnitType) {
                        if (!spawnedUnitOwnership.containsKey(unit)) {
                            Unit spawnerUnit = null;
                            int mindist = 999999;
                            int dist;
                            for (Unit unait : Groups.unit) {
                                dist = (int)(Math.round(Math.sqrt((unait.x - unit.x)*(unait.x - unit.x) + (unait.y - unit.y)*(unait.y - unit.y))));
                                if (dist<mindist && (unait.type == UnitTypes.disrupt || unait.type == UnitTypes.quell)) {
                                    spawnerUnit = unait;
                                    mindist = dist;
                                }
                            }
                            spawnedUnitOwnership.put(unit, spawnerUnit);
                        }
                    }
                }
                }
            }
        });

    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("m", "Open Evolve menu, you must stand near lab", (args, player) -> {
            if (players.containsKey(player.uuid()) && players.get(player.uuid()).isCanEvolve()) {
                Locale locale = Bundle.findLocale(player.locale());

                Evolution evolution = Evolutions.evolutions.get(player.unit().type().name);

                String[][] buttons = new String[evolution.evolutions.length][1];

                for (int i = 0; i < evolution.evolutions.length; i++) {
                    float multiplier = Laboratory.getMultiplier(evolution.evolutions[i], player);
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

                Call.menu(player.con, Laboratory.evolutionMenu, Bundle.get("menu.evolution.title", locale), Bundle.format("menu.evolution.message", locale, players.get(player.uuid()).getEvolutionStage(), Bundle.get("evolution.branch.initial", locale)), buttons);
            }
        });
//        handler.<Player>register("sm", "Starting menu, you can open it only util 1 stage", (args, player) -> {
//            Log.info(StartingMenu.canOpenMenu);
//            StartingMenu.openMenu(player, 0);
//        });
       // handler.<Player>register("b", "Open Building menu", (args, player) -> {
          //  if (playing && player.team() == Team.blue) {
              //  BuildMenu.openMenu(player);
            //}
       // });

        handler.<Player>register("bc", "Boss select menu", (args, player) -> {
            if (playing) {
                BossChooseMenu.openMenu(player);
            }
        });

        handler.<Player>register("c", "Open Class Choose menu, only for crux", (args, player) -> {
            if (player.team() == Team.crux) {
                ClassChooseMenu.openMenu(player);
            } else {
                player.sendMessage(Bundle.get("", player.locale));
            }
        });

        handler.<Player>register("s", "Open stations selecting menu", (args, player) -> StationsMenu.openMenu(player));

        handler.<Player>register("vote-map", "<map-number>", "Vote for specific map", (args, player) -> {
            if (Strings.canParseInt(args[0])) {
                MapVote.registerVote(player, Integer.valueOf(args[0]));
            } else {
                player.sendMessage(Bundle.get("not-a-number", player.locale));
            }
        });

        handler.<Player>register("ap", "Open a Admin Panel, only for admins", (args, player) -> {
            if (player.admin) {
                Admin.openAdminPanelMenu(player);
            }

        });
        
        handler.<Player>register("list-pinned-maps", "[scarlet]Admin only", (args, player) -> {
            if (player.admin) {
                try {
                    String mapList = "";
                    for (String mn : MapVote.pinnedMaps) {
                        mapList = mapList+"\n"+mn;
                    }
                    player.sendMessage(mapList);
                } catch (Exception e) {
                    player.sendMessage("Failed to list pinned maps" + e);
                }
            }
        });

        handler.<Player>register("pin-map","<filename>", "[scarlet]Admin only", (args, player) -> {
            if (player.admin) {
                try {
                    MapVote.pinnedMaps.add(args[0].trim());
                } catch (Exception e) {
                    player.sendMessage("Failed to pin a map" + e);
                }
            }
        });

        handler.<Player>register("clear-pinned-maps", "[scarlet]Admin only", (args, player) -> {
            if (player.admin) {
                try {
                    MapVote.pinnedMaps.clear();
                } catch (Exception e) {
                    player.sendMessage("Failed to clear pinned maps" + e);
                }
            }
        });

        handler.<Player>register("gameover", "Only for admins", (args, player) -> {
            if (player.admin) {
                callMapVoting();
                task.cancel();
            }
        });

        handler.<Player>register("blue", "Makes you blue, works only before stage 3", (args, player) -> {
            if (stage <= 3 && playing && player.team() != Team.blue && players.get(player.uuid()).getLastRedeemTime()+45<Instant.now().getEpochSecond()) {
                Unit oldUnit = player.unit();

                if (player.unit() != null) oldUnit.kill();

                player.team(Team.blue);
                players.put(player.uuid(), new PlayerData(player));
                PlayerData data = players.get(player.uuid());
                Unit unit = getStartingUnit().spawn(Team.blue, blueSpawnX, blueSpawnY);

                data.setUnit(unit);
                player.unit(unit);
                data.setLastRedeemTime(Instant.now().getEpochSecond());
                sendBundled("game.redeemed", player.name);
            } else {
                player.sendMessage(Bundle.get("game.late", player.locale));
            }
        });
        
        handler.<Player>removeCommand("rtv");
        handler.<Player>removeCommand("observer");
        handler.<Player>removeCommand("spectator");
//        handler.<Player>register("reset-data", "Use that if you blue and dont have unit.", (args, player) -> {
//            if (player.team() == Team.blue) {
//                players.put(player.uuid(), new PlayerData(player));
//            }
//        });
    }
    @Override
    public void registerServerCommands(CommandHandler handler) {
//        handler.register("reload", "<config-name>", "Reload config", (args) -> {
//            if (Objects.equals(args[0], "units")) {
//                int multipler = UnitsConfig.getPrices_multipler();
//                Log.info(multipler);
//            } else if (Objects.equals(args[0], "premium")) {
//                Premium.init();
//            }
//        });

        handler.register("setpoints", "[number] [playername...]",  "give people money, or take it(better), admin abuse, breaks capitalism, evil", (args) -> {
            try {
                Player player = null;
                for (Player p : Groups.player) {
                    if (p.name.contains(args[1].trim())) {
                        player = p;
                    }
                }
                players.get(player.uuid()).setScore(Integer.parseInt(args[0]));
                Log.info("Gave " + args[0] + " points to player " + player.name);
            } catch (Exception e) {
                Log.info("An error occured while executing the command");
                e.printStackTrace();
            }
        });

        handler.register("restart", "now actually works", (args) -> {
            Groups.player.each(p -> p.kick("[scarlet]Server is restarting"));
            Log.info("Server is restarting");
            System.exit(0);
        });
        
        handler.register("setstage","<number>" ,"Sets rvsb stage", (args) -> {
            try {
                stage = Integer.parseInt(args[0])-1;
                task.run();
            } catch (Exception e) {
                Log.info("Failed to set stage");
            }
        });

        
        handler.register("autorestart", "<yes/no> <gamesUntilRestart>", "self explanatory", (args) -> {
            try {
                if (args[0].trim().contains("yes")) {
                    Log.info("Games until restart: "+Integer.parseInt(args[1]));
                    gamesUntilRestart = Integer.parseInt(args[1]);
                } else {
                    Log.info("Autorestart disabled");
                    gamesUntilRestart = 2000000;
                }
            } catch (Exception e) {
                Log.info("A mysterious error has appeared while executing the command");
            }
        });
    }    

    public static void gameOverCheck() {
        int blueUnitCount = 0;
        int PlayerCount = 0;
        for (Unit u : Groups.unit) {
            if (u.team == Team.blue) {
                blueUnitCount = blueUnitCount+1;
            }
        }
        if ((playerCount(Team.blue) == 0 || blueUnitCount== 0) && ((playerCount(Team.blue) + playerCount(Team.crux)) != 0)) {
            gameOver(Team.crux);
        }
    }

    public static void gameOver(Team winner) {
        Events.fire("rvsb_game_over:" + winner.name);
        
        Groups.player.each(p -> {
            PlayerData data = players.get(p.uuid());
            if (!(data == null)) {
                data.setUnit(null);
                data.setExp(0);
                data.setLevel(1);
                data.setScore(0);
            }
        });
        
        if (winner == Team.crux) {
            if (playing) {
                RedVsBluePlugin.playing = false;
                if (!gameover) {
                    gameover = true;
                    callMapVoting();

                    task.cancel();

                    Groups.player.each(p -> {
                        int randomInt = getRandomInt(1, 255);
                        players.get(p.uuid()).setTeam(Team.get(randomInt));
                        p.team(Team.get(randomInt));
                    });
                }
            }
        } else if (winner == Team.blue) {
            if (!gameover) {
                gameover = true;
                callMapVoting();

                task.cancel();

                Groups.player.each(p -> {
                    int randomInt = getRandomInt(1, 255);
                    players.get(p.uuid()).setTeam(Team.get(randomInt));
                    p.team(Team.get(randomInt));
                });
            }
        }
    }
}
