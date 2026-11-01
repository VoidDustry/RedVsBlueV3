package net.voiddustry.redvsblue.game.stations;

import net.voiddustry.redvsblue.util.AuraCircle;
import net.voiddustry.redvsblue.util.StationFx;
import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.PlayerData;
import net.voiddustry.redvsblue.RedVsBluePlugin;
import net.voiddustry.redvsblue.evolution.Evolution;
import net.voiddustry.redvsblue.evolution.EvolutionMenu;
import net.voiddustry.redvsblue.evolution.Evolutions;
import net.voiddustry.redvsblue.game.stations.stationData.StationData;
import net.voiddustry.redvsblue.util.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.voiddustry.redvsblue.RedVsBluePlugin.players;

public class Laboratory {
    private static int labTick;
    private static final Map<String, StationData> labsMap =
            new ConcurrentHashMap<>();

    /**
     * Evolves the player's current unit into {@code targetUnitName}, charging the current price.
     * The caller is responsible for checking that the target is a valid evolution of the current unit.
     *
     * @return whether the evolution happened
     */
    public static boolean evolve(mindustry.gen.Player player, String targetUnitName) {
        Unit oldUnit = player.unit();

        if (oldUnit == null || oldUnit.type() == null) {
            return false;
        }

        Evolution evolutionOption =
                Evolutions.evolutions.get(targetUnitName);

        if (evolutionOption == null) {
            Log.warn(
                    "Evolution data for unit '@' was not found.",
                    targetUnitName
            );
            return false;
        }

        /*
         * Resolve the target through the active content registry at the
         * moment the player evolves. Do not use a cached UnitType or a
         * UnitTypes constant here.
         */
        UnitType targetType = Vars.content.unit(targetUnitName);

        if (targetType == null) {
            Log.warn(
                    "Cannot evolve player '@': Vars.content.unit('@') returned null.",
                    player.name(),
                    targetUnitName
            );
            return false;
        }

        PlayerData playerData = players.get(player.uuid());

        if (playerData == null) {
            return false;
        }

        float multiplier = getMultiplier(evolutionOption, player);
        int evolutionCost = (int) (evolutionOption.cost * multiplier);

        if (playerData.getScore() < evolutionCost) {
            player.sendMessage(
                    Bundle.get("evolution.not-enough", player.locale)
            );
            return false;
        }

        Tile playerTile = player.tileOn();

        boolean canSpawnHere =
                targetType.flying
                        || targetType.canBoost
                        || targetType.groundLayer == Layer.legUnit
                        || (playerTile != null
                        && playerTile.block() == Blocks.air);

        if (!canSpawnHere) {
            return false;
        }

        Unit newUnit = targetType.spawn(
                player.team(),
                oldUnit.x,
                oldUnit.y
        );

        if (newUnit == null || newUnit.dead()) {
            return false;
        }

        newUnit.health = newUnit.type.health / 2f;

        /*
         * Explicitly transfer control in the same way as the working
         * setunit server command.
         */
        newUnit.controller(player);
        net.voiddustry.redvsblue.util.Utils.assignUnit(player, newUnit);
        playerData.setUnit(newUnit);

        oldUnit.kill();

        StationFx.star(newUnit.x, newUnit.y, Math.max(24f, newUnit.hitSize), StationFx.lab);
        labsMap.values().stream()
                .filter(l -> l.tileOn().dst(newUnit) <= 64)
                .findFirst()
                .ifPresent(l -> StationFx.zap(l.tileOn().worldx(), l.tileOn().worldy(), newUnit.x, newUnit.y, StationFx.lab));

        playerData.subtractScore(evolutionCost);
        playerData.setEvolutionStage(evolutionOption.tier);
        playerData.setLastEvolutionTime(
                Instant.now().getEpochSecond()
        );

        Utils.sendBundled(
                "game.evolved",
                player.name(),
                targetType
        );
        return true;
    }

    public static void initTimer() {
        Timer.schedule(() -> {
            renderLabs();

            Groups.player.each(player -> {
                if (players.containsKey(player.uuid())) {
                    players.get(player.uuid()).setCanEvolve(false);
                }

                labsMap.forEach((uuid, lab) -> {
                    int centerX = lab.tileOn().x * 8;
                    int centerY = lab.tileOn().y * 8;

                    if (player.team() == Team.blue
                            && player.unit() != null
                            && player.dst(centerX, centerY) <= 48) {

                        PlayerData playerData =
                                players.get(player.uuid());

                        if (playerData != null) {
                            playerData.setCanEvolve(true);
                        }

                        Call.infoPopup(
                                player.con,
                                Bundle.get(
                                        "evolution.evolution-available",
                                        player.locale
                                ),
                                0.5f,
                                0,
                                0,
                                0,
                                -200,
                                0
                        );
                    }
                });
            });

            labTick++;
            labsMap.forEach((uuid, lab) -> {
                int centerX = lab.tileOn().x * 8;
                int centerY = lab.tileOn().y * 8;

                // particles get pulled in from the edge, sparks every 2 s
                float angle = Mathf.random(360f);
                StationFx.seek(centerX + Angles.trnsx(angle, 48), centerY + Angles.trnsy(angle, 48), centerX, centerY, StationFx.lab);
                if (labTick % 4 == 0) StationFx.sparks(centerX, centerY, StationFx.lab);
            });
        }, 0, 0.5f);
    }

    /**
     * Opens the evolution tree UI for a nearby laboratory station button press.
     */
    public static void openStationMenu(
            mindustry.gen.Player player
    ) {
        EvolutionMenu.open(player);
    }

    /** units whose stage in evolutions.hjson is at least this get the steep early-buy price */
    public static final int STEEP_PRICE_STAGE = 11;

    public static float getMultiplier(
            Evolution evolution,
            mindustry.gen.Player player
    ) {
        if (evolution == null
                || RedVsBluePlugin.players.get(
                player.uuid()
        ) == null) {
            return 4;
        }

        double timeSinceLastEvolution =
                Instant.now().getEpochSecond()
                        - RedVsBluePlugin.players
                        .get(player.uuid())
                        .getLastEvolutionTime();

        int requiredStage = evolution.stage;
        float multiplier = 0;

        if (timeSinceLastEvolution < 180) {
            multiplier =
                    (float) (
                            (180 - timeSinceLastEvolution)
                                    / 1000
                    );

            multiplier +=
                    ((float) Math.sqrt(evolution.cost)
                            / evolution.cost)
                            * multiplier;
        }

        if (RedVsBluePlugin.stage == requiredStage) {
            multiplier += 1f;
        } else if (RedVsBluePlugin.stage > requiredStage) {
            multiplier += 0.75f;
        } else if (requiredStage >= STEEP_PRICE_STAGE) {
            // stage 11+ units before their stage: 800% one stage early, 1600% two stages early, doubling per stage
            multiplier += (float) Math.pow(
                    2,
                    requiredStage
                            - RedVsBluePlugin.stage
                            + 2
            );
        } else {
            multiplier +=
                    1
                            + (float) Math.pow(
                            2,
                            requiredStage
                                    - RedVsBluePlugin.stage
                    ) / 4;
        }

        BigDecimal roundedMultiplier =
                new BigDecimal(
                        String.valueOf(multiplier)
                );

        roundedMultiplier =
                roundedMultiplier.setScale(
                        3,
                        RoundingMode.HALF_UP
                );

        return roundedMultiplier.floatValue();
    }

    public static float getMultiplier(
            String evolution,
            mindustry.gen.Player player
    ) {
        return getMultiplier(
                Evolutions.evolutions.get(evolution),
                player
        );
    }

    public static boolean owns(String uuid) {
        return labsMap.containsKey(uuid);
    }

    public static void buyLab(
            mindustry.gen.Player player,
            Tile tile
    ) {
        PlayerData playerData =
                players.get(player.uuid());

        if (playerData == null) {
            return;
        }

        if (playerData.getScore() < 7) {
            player.sendMessage(
                    Bundle.format(
                            "station.not-enough-money",
                            Bundle.findLocale(player.locale),
                            7
                    )
            );
            return;
        }

        if (labsMap.containsKey(player.uuid())
                || player.dead()) {
            return;
        }

        Tile playerTileOn = player.tileOn();

        if (playerTileOn == null) {
            return;
        }

        if (tile == null) {
            tile = Vars.world.tile(
                    playerTileOn.x,
                    playerTileOn.y - 1
            );
        }

        if (tile == null
                || player.dead()
                || player.team() != Team.blue
                || !tile.block().isAir()
                || tile.floor() == Blocks.empty) {
            return;
        }

        String text = player.name + "[gold]'s\n[purple]Lab";
        mindustry.gen.WorldLabel label = StationUtils.createStationLabel(tile, text);
        StationData laboratoryData =
                new StationData(player, tile, label, AuraCircle.fixed(tile.x * 8, tile.y * 8, 48, StationFx.lab));

        labsMap.put(
                player.uuid(),
                laboratoryData
        );

        Call.constructFinish(
                tile,
                Vars.content.block(
                        "dp-laboratory-station"
                ),
                null,
                (byte) 0,
                Team.blue,
                null
        );

        StationFx.at(Fx.teleportActivate, tile.x * 8, tile.y * 8, 0, StationFx.lab, null, 48);

        playerData.subtractScore(7);
    }

    public static void renderLabs() {
        labsMap.forEach((owner, lab) -> {
            if (lab == null) {
                return;
            }

            if (lab.tileOn().block()
                    != Vars.content.block(
                    "dp-laboratory-station"
            )
                    || lab.owner().team()
                    != Team.blue) {

                labsMap.remove(owner);
                lab.destroy();

                if (lab.tileOn().block()
                        == Vars.content.block(
                        "dp-laboratory-station"
                )) {
                    lab.tileOn().build.kill();
                }
            }
        });
    }

    public static void clearLabs() {
        labsMap.values().forEach(StationData::destroy);
        labsMap.clear();
    }
}