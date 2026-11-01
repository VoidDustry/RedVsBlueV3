package net.voiddustry.redvsblue;

import arc.Events;
import arc.Core;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.serialization.Jval;
import arc.util.serialization.Jval.*;


import arc.graphics.Color;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.ObjectMap;
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
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.mod.data.PatchAsset;
import mindustry.type.UnitType;
import mindustry.ui.Menus;
import mindustry.world.Tile;
import mindustry.net.*;
import mindustry.entities.EntityGroup;
import net.voiddustry.redvsblue.admin.Admin;
import net.voiddustry.redvsblue.ai.AirAI;
import net.voiddustry.redvsblue.ai.BluePlayerTarget;
import net.voiddustry.redvsblue.ai.StalkerGroundAI;
import net.voiddustry.redvsblue.ai.StalkerSuicideAI;
import net.voiddustry.redvsblue.evolution.EvolutionMenu;
import net.voiddustry.redvsblue.evolution.Evolutions;
import net.voiddustry.redvsblue.game.crux.*;
import net.voiddustry.redvsblue.game.stations.StationsMenu;
import net.voiddustry.redvsblue.game.stations.Laboratory;
import net.voiddustry.redvsblue.game.building.UpdateConstructBlocks;
import net.voiddustry.redvsblue.game.starting_menu.StartingMenu;
import net.voiddustry.redvsblue.game.stations.*;
import net.voiddustry.redvsblue.game.units.SpecialUnits;
import net.voiddustry.redvsblue.game.units.special.Serverminate;
import net.voiddustry.redvsblue.player.Hud;
import net.voiddustry.redvsblue.player.Premium;
import net.voiddustry.redvsblue.util.MapVote;
import net.voiddustry.redvsblue.util.MenuGuard;
import net.voiddustry.redvsblue.util.OrbitalStrike;
import net.voiddustry.redvsblue.util.UnitsConfig;
import net.voiddustry.redvsblue.util.Utils;
import net.voiddustry.redvsblue.logic.LInstructions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
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

    private static final HashMap<Unit, WorldLabel> hitboxLabels = new HashMap<>();

    public float blueSpawnX;
    public float blueSpawnY;

    public static Seq<Tile> redSpawns = new Seq<>();

    private static int tick = 0;

    public static int stage = 0;
    public static float stageTimer = 0;
    public static boolean playing = false;

    public int restartCounter = 0;
    public int gamesUntilRestart = 2000000;

    /** last stage; when its timer runs out blue wins */
    public static final int finalStage = 13;

    static Timer.Task task = new Timer.Task() {
        @Override
        public void run() {
            if (stage >= finalStage) {
                gameOver(Team.blue);
                return;
            }
            stage++;
            stageTimer = 300;
            spawnBoss();
            announceBundled("game.new-stage", 15, stage);
            ClassChooseMenu.updateUnitsMap();
        }
    };

    /** the one clock that drives {@link #task}; never create another */
    static final Timer stageClock = new Timer();

    /** (re)starts the stage clock so the next stage comes a full 300 s from now */
    public static void restartStageClock() {
        task.cancel();
        stageTimer = 300;
        stageClock.scheduleTask(task, 300, 300);
    }

    @Override
    public void init() {

        Log.info("&gRedVsBlue Plugin &rStarted!");

        LInstructions.register();
        Utils.initRules();
        Utils.initTimers();
        Utils.loadContent();
        SpecialUnits.init();
        SpawnEffect.initEffect();
        Boss.forEachBoss();
        Utils.initControlFilter();
        Premium.init();
        CruxUnit.addEvent();
        StationButtons.init();
        EvolutionMenu.init();
        

        StrengthSpawner.init();

        Events.on(EventType.WorldLoadEvent.class, event -> {
            for (UnitType unit : Vars.content.units()) {
                // data-asset missiles ("controller": "MissileAI", e.g. the Serverminate dash trigger) keep their AI,
                // MissileAI is what makes them fly at the shooter's cursor; Serverminate reads the shooter from it
                if (unit.aiController.get() instanceof MissileAI) {
                    continue;
                }
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
        });


        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;
            if (playing) {
                if (players.containsKey(player.uuid())) {
                    PlayerData data = players.get(player.uuid());
                    player.team(data.getTeam());
                    if (player.team() == Team.blue) {
                        Utils.assignUnit(player, data.getUnit());
                    }

                } else {
                    players.put(player.uuid(), new PlayerData(player));
                    PlayerData data = players.get(player.uuid());
                    Vars.world.tile(((int)blueSpawnX)/8,((int)blueSpawnY)/8).setNet(Blocks.air,Team.derelict,0);
                    Unit unit = getStartingUnit().spawn(Team.blue, blueSpawnX, blueSpawnY);

                    data.setUnit(unit);
                    Utils.assignUnit(player, unit);
                }
                if (playing && player.team() == Team.crux) {
                    CruxUnit.callSpawn(player);
                }
            }

            // the client camera starts at a random spot on join; keep it on the player's unit for a second
            Utils.focusCamera(player, 1f);

            int menu = Menus.registerMenu(((player1, option) -> {}));

            MenuGuard.menu(player.con, menu, Bundle.get("welcome", player.locale), Bundle.get("welcome.text", player.locale), new String[][]{{Bundle.get("stations.buttons.close", player.locale)}});
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
        //anuke why does perfectly fine code make dp status effects break the game
        //StatusEffect noRegisterKills = new StatusEffect("noRegisterKills") {{
          //  show = false;
        //}};
        

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
            StatusEffect noRegister = Vars.content.statusEffect("dp-no-register-kills");
            if (noRegister == null) {
                noRegister = Vars.content.statusEffect("noRegisterKills");
            }

            if ((noRegister == null || !event.unit.hasEffect(noRegister)) && !(event.unit.type instanceof MissileUnitType) && !(event.unit instanceof TimedKillc)) {
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
            // bullet.shooter is the unit that fired the first stage, so kills by missile units
            // (e.g. the Serverminate dash) count for the player who fired them
            Entityc source = event.bullet.shooter != null ? event.bullet.shooter : event.bullet.owner();
            if (event.unit != null && source instanceof Unit killer) {
                if (killer.isPlayer() && killer.team == Team.crux) {
                    PlayerData data = players.get(killer.getPlayer().uuid());
                    if (!(data == null)) {
                        data.addKill();
                        Call.label(killer.getPlayer().con, "[scarlet]+1", 2, event.unit.x, event.unit.y);
                        if (data.getKills() >= 2) {
                            Boss.killStreak(killer.getPlayer());
                        }
                    }

                }
            }
        });

        Events.on(EventType.BlockDestroyEvent.class, event -> {
        });





        Events.on(EventType.UnitDestroyEvent.class, event -> {

            spawnedUnitOwnership.remove(event.unit);
            killCredit.remove(event.unit);
            
            if (event.unit.isPlayer()) {
                Player owner = event.unit.getPlayer();
                PlayerData ownerData = players.get(owner.uuid());
                if (owner.team() == Team.blue && ownerData != null && ownerData.getUnit() != null
                        && ownerData.getUnit() != event.unit && !ownerData.getUnit().dead) {
                    // not their own unit (a controlled block or a grabbed unit): they still have theirs, the HUD loop puts them back
                    owner.clearUnit();
                } else if (owner.team() == Team.blue) {
                    UnitTypes.renale.spawn(Team.malis, event.unit.x, event.unit.y).kill();
                    // detaches the player from the dying unit before switching the team, then gives a fresh crux spawn
                    CruxUnit.convertToCrux(owner);
                } else if (owner.team() == Team.crux) {
                    owner.clearUnit();
                    CruxUnit.callSpawn(owner);
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
            Boss.clear();

            initRules();

            StageUnits.load();
            StrengthSpawner.load();
            Evolutions.load();

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

            restartStageClock();


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
            StrengthSpawner.reset();
            voting = false;
            StartingMenu.canOpenMenu = true;

            ClassChooseMenu.selectedUnit.clear();
            ClassChooseMenu.updateUnitsMap();

            initRules();
            launchGameStartTimer();

            spawnedUnitOwnership.clear();
            killCredit.clear();

            hitboxLabels.values().forEach(WorldLabel::hide);
            hitboxLabels.clear();


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

                    StatusEffect noDrawHitbox = Serverminate.noDrawHitbox();

                    Groups.unit.each(u -> {
                        // units with dp-no-draw-hitbox (e.g. the Serverminate dash trigger) get no X
                        if (noDrawHitbox != null && u.hasEffect(noDrawHitbox)) return;

                        WorldLabel label = hitboxLabels.get(u);

                        if (label == null || !label.isAdded()) {
                            label = WorldLabel.create();
                            label.fontSize = 0.5F;
                            label.text = "[orange]X";
                            label.x = u.x;
                            label.y = u.y;
                            label.add();
                            hitboxLabels.put(u, label);
                        } else {
                            label.x = u.x;
                            label.y = u.y;
                        }
                    });

                    // Remove labels for units that no longer exist
                    Iterator<Entry<Unit, WorldLabel>> it = hitboxLabels.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry<Unit, WorldLabel> entry = it.next();
                        Unit u = entry.getKey();
                        if (u == null || u.dead || !u.isAdded() || (noDrawHitbox != null && u.hasEffect(noDrawHitbox))) {
                            entry.getValue().hide();   // hide(), not remove()
                            it.remove();
                        }
                    }

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
            if (players.containsKey(player.uuid())) {
                EvolutionMenu.open(player);
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
                Unit old = player.unit();
                // detach first: Player.team() also sets the team of the attached unit, so a crux unit the player had
                // taken over (not spawnedByCore, so it is not despawned) was turned blue together with the player
                player.clearUnit();
                if (old != null && old.spawnedByCore) Call.unitDespawn(old);
                CruxUnit.resetPossession(player);

                player.team(Team.blue);
                players.put(player.uuid(), new PlayerData(player));
                PlayerData data = players.get(player.uuid());
                Unit unit = getStartingUnit().spawn(Team.blue, blueSpawnX, blueSpawnY);

                data.setUnit(unit);
                Utils.assignUnit(player, unit);
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

		handler.<Player>register("smite", "[y/n]",
		"Abandon hope, all ye who enter here", (String[] args, Player player) -> {
			if (!playing) return;
			if (player.team() != Team.blue) {
				player.con.send(new SMCP(Bundle.get("f.vote.forBlue", player.locale)), true);
				return;
			}
			if (args.length == 0) {
				openSmiteMenu(player);
				return;
			}
			if (smiteTarget == null) {
				player.con.send(new SMCP(Bundle.get("f.vote.none", player.locale)), true);
				return;
			}
			String arg = args[0];
			boolean voteYes = yes.get(arg);
			if (!voteYes && !no.get(arg)) {
				player.con.send(new SMCP(Bundle.get("f.vote.syntax", player.locale)), true);
				return;
			}
			if (!smiteAllowDupes && (smiteUuids.contains(player.uuid()) || smiteIps.contains(player.con.address))) {
				player.con.send(new SMCP(Bundle.get("f.vote.already", player.locale)), true);
				return;
			}
			smiteUuids.add(player.uuid());
			smiteIps.add(player.con.address);
			if (voteYes) {
				votes++;
				Utils.sendBundled("f.vote.yes", player.name, smiteTargetName);
			} else {
				votes--;
				Utils.sendBundled("f.vote.no", player.name, smiteTargetName);
			}
			if (votes >= SMITE_VOTES_NEEDED) {
				Player target = Groups.player.find(p -> p.uuid().equals(smiteTarget));
				endSmiteVote();
				if (target == null || target.team() != Team.blue) {
					sendBundled("f.vote.escaped");
					return;
				}
				sendBundled("f.vote.passed");
				smiteUsed = true;
				OrbitalStrike.start(target);
			}
		});
		Events.on(EventType.WorldLoadEvent.class, e -> {
			OrbitalStrike.cancel();
			// new game: smite is available again, a vote from the previous map is dropped
			smiteUsed = false;
			smiteTarget = null;
			smiteUuids.clear();
			smiteIps.clear();
		});
		Events.on(EventType.GameOverEvent.class, e -> OrbitalStrike.cancel());
		Events.run(Trigger.update, () -> {
			OrbitalStrike.update();
			if (smiteTarget == null || System.currentTimeMillis() < smiteTime) return;
			endSmiteVote();
			sendBundled("f.vote.finish");
		});
    }

	private static final CharTreeSet yes = new CharTreeSet("y", "yes", "Y", "YES", "н", "нуы", "Н", "НУЫ", "нуі", "НУІ");
	private static final CharTreeSet no = new CharTreeSet("n", "no", "N", "NO", "т", "тщ", "Т", "ТЩ");

	private static final long SMITE_VOTE_MS = 90_000L;
	private static final long SMITE_COOLDOWN_MS = 5 * 60_000L;
	private static final int SMITE_VOTES_NEEDED = 5;

	/** uuid of the player being voted on, null = no vote running */
	private static String smiteTarget = null;
	/** name of the player being voted on, kept for vote messages even if they leave */
	private static String smiteTargetName = "";
	private static long smiteTime = 0;
	/** uuid and ip of the player who started the current vote */
	private static String smiteStarterUuid = null, smiteStarterIp = null;
	/** per player: "u:" + uuid / "i:" + ip -> time until which they cannot start a new vote (set when their vote ends) */
	private static final ObjectMap<String, Long> smiteCooldowns = new ObjectMap<>();
	private static int votes = 0;
	/** a smite vote already passed this game; reset on world load */
	private static boolean smiteUsed = false;
	/** testing only: when true the same uuid/ip can vote any number of times (server command smite-dupes) */
	private static boolean smiteAllowDupes = false;
	/** everyone who voted in the current vote: each uuid and each ip only once */
	private static final ObjectSet<String> smiteUuids = new ObjectSet<>();
	private static final ObjectSet<String> smiteIps = new ObjectSet<>();
	/** player uuid -> uuids listed in the selection menu shown to them */
	private static final ObjectMap<String, String[]> smiteChoices = new ObjectMap<>();
	private static final int smiteMenu = Menus.registerMenu(RedVsBluePlugin::chooseSmiteTarget);

	private static void openSmiteMenu(Player player) {
		if (smiteUsed) {
			player.con.send(new SMCP(Bundle.get("f.vote.used", player.locale)), true);
			return;
		}
		if (smiteTarget != null) {
			player.con.send(new SMCP(Bundle.get("f.vote.underway", player.locale)), true);
			return;
		}
		long wait = smiteCooldownLeft(player);
		if (wait > 0) {
			long s = (wait + 999) / 1000;
			player.con.send(new SMCP(Bundle.format("f.vote.cooldown", Bundle.findLocale(player.locale()), s / 60, String.format("%02d", s % 60))), true);
			return;
		}
		Seq<Player> blue = Groups.player.copy(new Seq<>()).select(p -> p.team() == Team.blue);
		String[][] options = new String[blue.size][];
		String[] uuids = new String[blue.size];
		for (int i = 0; i < blue.size; i++) {
			options[i] = new String[]{blue.get(i).name};
			uuids[i] = blue.get(i).uuid();
		}
		smiteChoices.put(player.uuid(), uuids);
		MenuGuard.menu(player.con, smiteMenu, Bundle.get("f.vote.smite", player.locale),
			Bundle.get("f.vote.hope", player.locale) + "\n" + Bundle.get("f.vote.select", player.locale), options);
	}

	private static void chooseSmiteTarget(Player player, int option) {
		String[] uuids = smiteChoices.remove(player.uuid());
		if (uuids == null || option < 0 || option >= uuids.length || !playing || player.team() != Team.blue) return;
		if (smiteTarget != null) {
			player.con.send(new SMCP(Bundle.get("f.vote.underway", player.locale)), true);
			return;
		}
		if (smiteUsed || smiteCooldownLeft(player) > 0) return;
		Player target = Groups.player.find(p -> p.uuid().equals(uuids[option]));
		if (target == null || target.team() != Team.blue) {
			player.con.send(new SMCP(Bundle.get("f.vote.gone", player.locale)), true);
			return;
		}
		smiteTarget = target.uuid();
		smiteTargetName = target.name;
		smiteStarterUuid = player.uuid();
		smiteStarterIp = player.con.address;
		smiteTime = System.currentTimeMillis() + SMITE_VOTE_MS;
		smiteUuids.clear();
		smiteIps.clear();
		smiteUuids.add(player.uuid());
		smiteIps.add(player.con.address);
		votes = 1;
		Utils.sendBundled("f.vote.start", target.name);
	}

	private static void endSmiteVote() {
		smiteTarget = null;
		smiteUuids.clear();
		smiteIps.clear();
		long until = System.currentTimeMillis() + SMITE_COOLDOWN_MS;
		if (smiteStarterUuid != null) smiteCooldowns.put("u:" + smiteStarterUuid, until);
		if (smiteStarterIp != null) smiteCooldowns.put("i:" + smiteStarterIp, until);
		smiteStarterUuid = smiteStarterIp = null;
	}

	/** ms until this player may start another smite vote (matched by uuid or ip), 0 = allowed now */
	private static long smiteCooldownLeft(Player player) {
		long now = System.currentTimeMillis();
		for (var it = smiteCooldowns.entries().iterator(); it.hasNext(); ) if (it.next().value <= now) it.remove();
		long until = Math.max(smiteCooldowns.get("u:" + player.uuid(), 0L), smiteCooldowns.get("i:" + player.con.address, 0L));
		return Math.max(until - now, 0L);
	}

	public static class CharTreeSet {
		private CharTree[] branches;

		public CharTreeSet(String... args) {
			super();
			int size = 0;
			int length = args.length;
			for (int i = 0; i < length; i++) {
				String arg = args[i];
				int len = arg.length();
				for (int j = 0; j < len; j++) {
					char character = arg.charAt(j);
					if (size < character) size = character;
				}
			}
			this.branches = new CharTree[++size];
			for (int i = 0; i < length; i++) {
				String arg = args[i];
				int len = arg.length();
				CharTree branch = null;
				CharTree[] branches = this.branches;
				for (int j = 0; j < len; j++) {
					char character = arg.charAt(j);
					branch = branches[character];
					branches = (branch == null ? branches[character] = branch = new CharTree(size) : branch).branches;
				}
				branch.end = true;
			}
			int zip = -1;
			for (int i = 0; i < size; i++) {
				CharTree branch = this.branches[i];
				if (branch == null) continue;
				branch.zip();
				zip = i;
			}
			if (++zip == size) return;
			CharTree[] tmp = new CharTree[zip];
			for (int i = 0; i < zip; i++) tmp[i] = this.branches[i];
			this.branches = tmp;
		}

		public boolean get(String arg) {
			int length = arg.length();
			if (length == 0) return false;
			char character = arg.charAt(0);
			if (character >= this.branches.length) return false;
			CharTree branch = this.branches[character];
			if (branch == null) return false;
			for (int i = 1; i < length; i++) {
				CharTree[] branches = branch.branches;
				if ((character = arg.charAt(i)) >= branches.length
				|| (branch = branches[character]) == null) return false;
			}
			return branch.end;
		}
	}

	public static class CharTree {
		private static final CharTree[] cork = new CharTree[0];

		public CharTree[] branches;
		public boolean end = false;

		public CharTree(int length) {
			this.branches = new CharTree[length];
		}

		public void zip() {
			int zip = -1;
			int length = this.branches.length;
			for (int i = 0; i < length; i++) {
				CharTree branch = this.branches[i];
				if (branch == null) continue;
				branch.zip();
				zip = i;
			}
			if (++zip == length) return;
			if (zip == 0) {
				this.branches = cork;
			} else {
				CharTree[] tmp = new CharTree[zip];
				for (int i = 0; i < zip; i++) tmp[i] = this.branches[i];
				this.branches = tmp;
			}
		}
	}

	public static class SMCP extends SendMessageCallPacket {

		public SMCP(String message) {
			super();
			this.message = message;
		}

		static {
			ObjectIntMap<Class<?>> map = Reflect.get(Net.class, "packetToId");
			map.put(SMCP.class, map.get(SendMessageCallPacket.class, -1));
		}
	}

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("smite-dupes", "[on/off]", "Testing: allow the same player/IP to vote in /smite more than once", args -> {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("on")) smiteAllowDupes = true;
                else if (args[0].equalsIgnoreCase("off")) smiteAllowDupes = false;
                else {
                    Log.err("Usage: smite-dupes [on/off]");
                    return;
                }
            }
            Log.info("Duplicate smite votes: @", smiteAllowDupes ? "&gallowed (testing)&fr" : "&rblocked&fr");
        });
        handler.register("crux-spawns", "[reload]", "Show strength spawner state, or reload rvsbUnits/spawns.hjson + unitWeights.hjson", args -> {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) StrengthSpawner.load();
            Log.info(StrengthSpawner.status());
        });
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
                if (args[1].trim().equalsIgnoreCase("all")) {
                    int amount = Integer.parseInt(args[0]);
                    for (Player p : Groups.player) {
                        PlayerData data = players.get(p.uuid());
                        if (data != null) {
                            data.setScore(amount);
                        }
                    }
                    Log.info("Gave " + args[0] + " points to ALL players");
                    return;
                }
        
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
        
        handler.register("setunit", "<unitname> <playername...>", "sets a player's unit", (args) -> {
            try {
                String unitName = args[0].trim();
                String playerName = args[1].trim().toLowerCase();
        
                UnitType type = Vars.content.unit(unitName);
        
                if (type == null) {
                    Log.info("Unit not found: " + unitName);
                    return;
                }
        
                if (playerName.equals("all")) {
                    for (Player p : Groups.player) {
                        Unit old = p.unit();
                        Unit unit = type.spawn(p.team(), old.x, old.y);
                        unit.controller(p);
                        Utils.assignUnit(p, unit);
                        old.health = -1000;
                        PlayerData data = players.get(p.uuid());
                        if (data != null) {
                            data.setUnit(unit);
                        }
                    }
                    Log.info("Set ALL players' unit to " + type.name + " / id " + type.id);
                    return;
                }
        
                Player player = null;
        
                for (Player p : Groups.player) {
                    if (p.name.toLowerCase().contains(playerName)) {
                        player = p;
                        break;
                    }
                }
        
                if (player == null) {
                    Log.info("Player not found: " + args[1]);
                    return;
                }
        
                Unit old = player.unit();
                Unit unit = type.spawn(player.team(), old.x, old.y);
                unit.controller(player);
                Utils.assignUnit(player, unit);
                old.health=-1000;
                players.get(player.uuid()).setUnit(unit);
        
                Log.info("Set " + player.name + "'s unit to " + type.name + " / id " + type.id);
            } catch (Exception e) {
                Log.info("An error occurred while executing the command");
                e.printStackTrace();
            }
        });

        handler.register("restart", "now actually works", (args) -> {
            Groups.player.each(p -> p.kick("[scarlet]Server is restarting"));
            Log.info("Server is restarting");
            System.exit(0);
        });
        
        handler.register("randomboss", "[playername...]", "Same as Spawn Random Boss in /ap: gives a random crux player (or the named one) a killing streak", args -> {
            Player target;
            if (args.length > 0) {
                target = Groups.player.find(p -> Strings.stripColors(p.name).equalsIgnoreCase(args[0]) || p.name.equalsIgnoreCase(args[0]));
                if (target == null) {
                    Log.info("No player named @.", args[0]);
                    return;
                }
                if (target.team() != Team.crux) {
                    Log.info("@ is not on crux.", Strings.stripColors(target.name));
                    return;
                }
            } else {
                target = Utils.getRandomPlayer(Team.crux);
                if (target == null) {
                    Log.info("No crux players.");
                    return;
                }
            }
            if (Boss.hasAura(target)) {
                Log.info("@ is already on a killing streak.", Strings.stripColors(target.name));
                return;
            }
            Boss.killStreak(target);
            Log.info("@ goes on a killing streak.", Strings.stripColors(target.name));
        });

        handler.register("setstage","<number>" ,"Sets rvsb stage", (args) -> {
            int target;
            try {
                target = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                Log.info("Not a number: @", args[0]);
                return;
            }
            if (target < 1 || target > finalStage) {
                Log.info("Stage must be between 1 and @", finalStage);
                return;
            }
            if (!playing || gameover) {
                Log.info("No game is running");
                return;
            }
            // start the stage the same way the clock does, then restart the clock so the next stage is
            // a full 300 s away (the old command left the clock alone, so the next stage came early)
            stage = target - 1;
            task.run();
            restartStageClock();
            Log.info("Stage set to @", stage);
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
        StageUnits.rollBosses();
        StrengthSpawner.stop();
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
