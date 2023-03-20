package net.voiddustry.redvsblue;

import arc.Events;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.util.*;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.ai.Pathfinder;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.type.UnitType;
import mindustry.ui.Menus;
import mindustry.world.Block;
import mindustry.world.Tile;

import net.voiddustry.redvsblue.admin.Admin;
import net.voiddustry.redvsblue.ai.AirAI;
import net.voiddustry.redvsblue.game.Miner;
import net.voiddustry.redvsblue.game.MinerData;
import net.voiddustry.redvsblue.game.RepairPoint;
import net.voiddustry.redvsblue.util.MapVote;
import net.voiddustry.redvsblue.util.UnitsConfig;
import net.voiddustry.redvsblue.util.Utils;
import net.voiddustry.redvsblue.ai.BluePlayerTarget;
import net.voiddustry.redvsblue.ai.StalkerGroundAI;
import net.voiddustry.redvsblue.ai.StalkerSuicideAI;
import net.voiddustry.redvsblue.evolution.Evolution;
import net.voiddustry.redvsblue.evolution.Evolutions;

import java.util.*;

import static net.voiddustry.redvsblue.util.MapVote.callMapVoting;
import static net.voiddustry.redvsblue.util.Utils.*;
import static net.voiddustry.redvsblue.util.WebhookUtils.*;

@SuppressWarnings("unused")
public class RedVsBluePlugin extends Plugin {
    public static final int bluePlayerTargeting;

    static {
        Pathfinder.fieldTypes.add(BluePlayerTarget::new);
        bluePlayerTargeting = Pathfinder.fieldTypes.size - 1;
    }

    public static final HashMap<String, Boolean> playerInBuildMode = new HashMap<>();
    public static final HashMap<String, Block> selectedBuildBlock = new HashMap<>();
    public static final HashMap<String, PlayerData> players = new HashMap<>();

    public Map<Player, Integer> timer = new HashMap<>();

    public float blueSpawnX;
    public float blueSpawnY;
    public static float redSpawnX;
    public static float redSpawnY;

    public static int stage = 0;
    public static boolean playing = false;

    private final int evolutionMenu = Menus.registerMenu((player, option) -> {
        if (option == -1) return;

        Evolution evolution = Evolutions.evolutions.get(player.unit().type().name);
        Evolution evolutionOption = Evolutions.evolutions.get(evolution.evolutions[option]);

        PlayerData playerData = players.get(player.uuid());

        if (playerData.getScore() >= evolutionOption.cost) {
            Unit unit = evolutionOption.unitType.spawn(Team.blue, player.x(), player.y());

            if (!unit.dead()) {
                Unit oldUnit = playerData.getUnit();
                playerData.setUnit(unit);

                player.unit(unit);
                oldUnit.kill();

                playerData.subtractScore(evolutionOption.cost);
                playerData.setEvolutionStage(evolutionOption.tier);

                Utils.sendBundled("game.evolved", player.name(), evolution.evolutions[option]);
            }
        }
    });

    @Override
    public void init() {
        sendServerStartMessage();
        initStats();
        Miner.initTimer();
        RepairPoint.initTimer();
//        Vars.state.rules.ambientLight = new Color();Call.setRules(Vars.state.rules);
        for (UnitType unit : Vars.content.units()) {
            if (unit == UnitTypes.crawler) {
                unit.aiController = StalkerSuicideAI::new;
            }
            if (!unit.flying) {
                unit.physics = false;
                unit.aiController = StalkerGroundAI::new;
            }
            if (unit.flying) {
                unit.aiController = AirAI::new;
            }
            if (unit.naval) {
                unit.flying = true;
            }


        }

        Timer.schedule(() -> timer.replaceAll((player, time) -> time = time + 1), 0, 1);

        Timer.schedule(() -> Groups.player.each(p -> {
            if (p.team() == Team.blue) {
                players.get(p.uuid()).setScore(players.get(p.uuid()).getScore() + money_per_min);
                p.sendMessage(Bundle.format("game.salary", Bundle.findLocale(p.locale), money_per_min));

            }
        }), 0, 60);

        Events.on(EventType.PlayerJoin.class, event -> {
            Player player = event.player;

            if (players.containsKey(player.uuid())) {
                PlayerData data = players.get(player.uuid());
                player.team(data.getTeam());
                if (player.team() == Team.blue) {
                    player.unit(data.getUnit());
                }

            } else {
                players.put(player.uuid(), new PlayerData(player));
                PlayerData data = players.get(player.uuid());
                Unit unit = getStartingUnit().spawn(Team.blue, blueSpawnX, blueSpawnY);

                data.setUnit(unit);
                player.unit(unit);
            }
            if (!playerInBuildMode.containsKey(player.uuid())) {
                playerInBuildMode.put(player.uuid(), false);
            }
            if (!selectedBuildBlock.containsKey(player.uuid())) {
                selectedBuildBlock.put(player.uuid(), Blocks.thoriumWall);
            }
            if (!timer.containsKey(player)) {
                timer.put(player, 0);
            }

            if (playing && player.team() == Team.crux) {
                spawnUnitForCrux(event.player);
            }

           sendPlayerJoinMessage(player.plainName());
        });

        Events.on(EventType.PlayerLeave.class, event -> sendPlayerLeaveMessage(event.player.plainName()));

        Events.on(EventType.PlayerChatEvent.class, event -> {
            sendPlayerChatMessage(event.message, event.player.plainName());
            Call.sound(Sounds.chatMessage, 2, 2, 1);
            if (Utils.voting) {
                if (Strings.canParseInt(event.message)) {
                    MapVote.registerVote(event.player, Strings.parseInt(event.message));
                }
            }
        });

        Events.on(EventType.UnitBulletDestroyEvent.class, event -> {
            if (event.unit != null && event.bullet.owner() instanceof Unit killer) {
                if (killer.isPlayer()) {
                    PlayerData data = players.get(killer.getPlayer().uuid());
                    players.get(killer.getPlayer().uuid()).addScore(killer.team() == Team.blue ? (hardcore ? data.getLevel()*2 : 1) : 1);
                    Call.label(killer.getPlayer().con, killer.team() == Team.blue ? "[lime]+" + (hardcore ? data.getLevel()*2 : 1) : "[lime]+1", 2, event.unit.x, event.unit.y);
                    data.addExp(1);
                    processLevel(killer.getPlayer(), data);
                    if (event.unit.isPlayer()) {
                        sendPlayerKillMessage(killer.getPlayer().plainName(), event.unit.getPlayer().plainName());
                    }
                }
            }
        });

        Events.on(EventType.PlayerBanEvent.class, event -> sendPlayerBanMessage(event.player));

        Events.on(EventType.UnitDestroyEvent.class, event -> {
            if (event.unit.isPlayer()) {
                if (event.unit.team() == Team.blue) {
                    event.unit.getPlayer().team(Team.crux);
                    PlayerData data = players.get(event.unit.getPlayer().uuid());
                    data.setTeam(Team.crux);
                    data.setScore(0);
                } else if (playing && event.unit.getPlayer().team() == Team.crux) {
                    spawnUnitForCrux(event.unit.getPlayer());
                }
            }
            gameOverCheck();
        });

        Events.on(EventType.WaveEvent.class, event -> {
            stage = (int) Math.floor(Vars.state.wave / 6f) + 1;

            if (Vars.state.wave % 6 == 0 && stage > 1) {
                if (playerCount(Team.crux) >= 5)
                    Utils.spawnBoss();
            }
            if (Vars.state.wave == 102) {
                sendGameWinMessage();
            }
        });

        Events.on(EventType.GameOverEvent.class, event -> sendGameOverMessage());

        Events.on(EventType.WorldLoadEvent.class, event -> {
            Miner.clearMiners();
            RepairPoint.clearPoints();
            initRules();
            Groups.player.each(p -> {
                PlayerData data = players.get(p.uuid());
                data.setUnit(null);
                data.setExp(0);
                data.setLevel(1);
            });
        });

        Events.on(EventType.WorldLoadEvent.class, event -> Timer.schedule(() -> {
            String mapname = Vars.state.map.file.file().getName();
            Vars.state.rules.canGameOver = false;
            Vars.state.rules.unitCap = 32;

            Building core = Vars.state.teams.cores(Team.blue).first();

            blueSpawnX = core.x();
            blueSpawnY = core.y();

            Vars.state.teams.cores(Team.blue).each(Building::kill);

            for (int x = 0; x < Vars.state.map.width; x++) {
                for (int y = 0; y < Vars.state.map.height; y++) {
                    Tile tile = Vars.world.tile(x, y);
                    if (tile.overlay() == Blocks.spawn) {
                        redSpawnX = tile.getX();
                        redSpawnY = tile.getY();
                        break;
                    }
                }
            }

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
            gameover = false;
            sendGameStartMessage();
            initRules();

            Call.setRules(Vars.state.rules);
        }, 5));

        Events.run(EventType.Trigger.update, () -> Groups.player.each(player -> {

            Unit unit = player.unit();
            PlayerData data = players.get(player.uuid());

            String textHud = "[accent]" + data.getExp() + " / " + data.getMaxExp();

            Call.label(player.con, "[scarlet]+", 0.01F, player.x, player.y);

            Call.setHudText(player.con(), Bundle.format("game.hud", Bundle.findLocale(player.locale()), Math.floor(unit.health()), Math.floor(unit.shield()), data.getScore(), stage, data.getLevel(), textHud));

            if (playerInBuildMode.get(player.uuid())) {
                Tile position;
                if (player.mouseX >= 0 && player.mouseX <= (Vars.state.map.width-1)*8 && player.mouseY >= 0 && player.mouseY <= (Vars.state.map.height-1)*8) {
                    position = Vars.world.tile(Math.round(player.mouseX / 8), Math.round(player.mouseY / 8));

                    String text = "[gray][\uE805]";
                    String textAnnounce = Bundle.get("build.not-enough-money", player.locale);

                    if (data.getScore() >= 2) {
                        if (selectedBuildBlock.get(player.uuid()) != Blocks.air) {
                            if (Objects.equals(position.block().name, "air")) {
                                if (timer.get(player) >= 2) {
                                    text = "[lime][\uE805]";
                                    textAnnounce = String.valueOf(selectedBuildBlock.get(player.uuid()));
                                } else {
                                    text = "[yellow][\uE805]";
                                    textAnnounce = Bundle.get("build.cooldown", player.locale);
                                }
                            } else {
                                text = "[scarlet][\uE868]";
                                textAnnounce = "";
                            }
                        } else if (selectedBuildBlock.get(player.uuid()) == Blocks.air) {
                            if (timer.get(player) >= 2) {
                                if (position.build != null) {
                                    if (position.build.team == Team.blue) {
                                        text = "[lime][\uE805]";
                                        textAnnounce = Bundle.get("build.destroy-wall", player.locale);
                                    } else {
                                        text = "[scarlet][\uE868]";
                                        textAnnounce = "";
                                    }
                                }
                            } else {
                                text = "[yellow][\uE805]";
                                textAnnounce = Bundle.get("build.cooldown", player.locale);
                            }
                        }

                        if (player.shooting && timer.get(player) >= 2) {
                            if (Objects.equals(position.block().name, "air")) {
                                Vars.world.tile(Math.round(player.mouseX / 8), Math.round(player.mouseY / 8)).setNet(selectedBuildBlock.get(player.uuid()), player.team(), 0);
                                Call.effect(Reflect.get(Fx.class, "dynamicExplosion"), position.x * 8, position.y * 8, 0.5F, Color.blue);
                                timer.put(player, 0);
                                data.setScore(data.getScore() - 2);
                                data.addExp(1);
                            } else if (selectedBuildBlock.get(player.uuid()) == Blocks.air) {
                                if (position.build != null) {
                                    if (position.build.team == Team.blue) {
                                        Vars.world.tile(Math.round(player.mouseX / 8), Math.round(player.mouseY / 8)).setNet(selectedBuildBlock.get(player.uuid()), player.team(), 0);
                                        Call.effect(Reflect.get(Fx.class, "heal"), position.x * 8, position.y * 8, 1, Color.blue);
                                        timer.put(player, 0);
                                    }
                                }
                            }
                        }
                    }
                    Call.label(player.con, text, 0.01F, (float) ((Math.round(player.mouseX / 8)) * 8), (float) ((Math.round(player.mouseY / 8)) * 8));
                    Call.label(player.con, textAnnounce, 0.01F, (float) ((Math.round(player.mouseX / 8)) * 8), (float) (((Math.round(player.mouseY / 8)) * 8) - 5));

                } else {
                    Call.announce(player.con, Bundle.get("build.player-mouse-out-of-bounds-of-map", player.locale));
                }
            }

            if (playing && data.getUnit().dead) {
                data.setTeam(Team.crux);
                player.team(data.getTeam());
            }

            if (playing && !data.getUnit().dead) {
                player.unit(data.getUnit());
            }


        }));
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("report", "<player> <reason...>","[white]<player-name> <text> [gray]- Report player to discord server. For false report you will get ban.", ((args, player) -> {
            if (Objects.equals(args[0], " ")) return;
            sendReport(args[0], player, args);
            player.sendMessage(Bundle.get("report.reportSend", player.locale));
        }));

        handler.<Player>register("b", "Open block select menu", ((args, player) -> Utils.openBlockSelectMenu(player)));

        handler.<Player>register("build", "", "Toggle build mode", ((args, player) -> {
            if (playerInBuildMode.get(player.uuid())) {
                playerInBuildMode.put(player.uuid(), false);
                player.sendMessage("[scarlet]Building Disabled");
            } else {
                playerInBuildMode.put(player.uuid(), true);
                player.sendMessage("[lime]Building Enabled");
            }
        }));

        handler.<Player>register("e", "Open evolution menu", ((args, player) -> {
            if (player.team() != Team.blue) return;

            Locale locale = Bundle.findLocale(player.locale());

            Evolution evolution = Evolutions.evolutions.get(player.unit().type().name);

            String[][] buttons = new String[evolution.evolutions.length][1];

            for (int i = 0; i < evolution.evolutions.length; i++) {
                buttons[i][0] = Bundle.format("menu.evolution.evolve", locale, evolution.evolutions[i], Evolutions.evolutions.get(evolution.evolutions[i]).cost);
            }

            Call.menu(player.con, evolutionMenu, Bundle.get("menu.evolution.title", locale), Bundle.format("menu.evolution.message", locale, players.get(player.uuid()).getEvolutionStage(), Bundle.get("evolution.branch.initial", locale)), buttons);
        }));

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
            } else {

            }

        });

        handler.<Player>register("gameover", "Only for admins", (args, player) -> callMapVoting());

        handler.<Player>register("miner", "Buy a miner!", (args, player) -> {
            Miner.buyMiner(player);
        });

        handler.<Player>register("repair-point", "Buy a repair point!", (args, player) -> {
            RepairPoint.buyRepairPoint(player);
        });

        handler.<Player>register("redeem", "<uuid>", "redeem player", (args, player) -> {
            if (player.admin) {
                PlayerData data = players.get(args[0]);
                data.setTeam(Team.blue);
                data.setUnit(getStartingUnit().spawn(Team.blue, blueSpawnX, blueSpawnY));
                sendBundled("game.redeem", data.getName());
            }
        });

        handler.<Player>register("hardcore", "Enable [scarlet]HARDCORE[] mode. Only for admins.", (args, player) -> {
            if (player.admin) {
                enableHardCore();
            }
        });

        handler.<Player>register("set-score", "<amount>","setscore", (args, player) -> {
            if (player.admin) {
                players.get(player.uuid()).setScore(Integer.parseInt(args[0]));
            }
        });

        handler.<Player>register("discord","Join to discord server", (args, player) -> {
            Call.openURI(player.con, "https://discord.gg/KkBjRmb5Db");
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("reload", "<config-name>", "Reload config", (args) -> {
            if (Objects.equals(args[0], "units")) {
                int multipler = UnitsConfig.getPrices_multipler();
                Log.info(multipler);
            }
        });

    }

    public static void gameOverCheck() {
        if (playerCount(Team.blue) == 0) {
            gameOver(Team.crux);
        }
    }

    public static void gameOver(Team winner) {
        if (winner == Team.crux) {
            RedVsBluePlugin.playing = false;
            if (!gameover) {
                gameover = true;
                callMapVoting();
            }
        } else if (winner == Team.blue) {
            // TODO:
        }
    }
}
