package net.voiddustry.redvsblue.game.stations;

import net.voiddustry.redvsblue.util.AuraCircle;
import net.voiddustry.redvsblue.util.StationFx;
import arc.graphics.Color;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
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

public class Booster {
    private static final Map<String, StationData> boostersMap = new ConcurrentHashMap<>();
    private static final float BOOST_RADIUS = 64f;
    /** overclock lasts 3 s, the same as the pulse interval */
    private static final float BUFF_TICKS = 3f * 60f;

    public static void initTimer() {
        // one pulse every 3 s; every blue unit inside gets overclock for 3 s and sparks
        Timer.schedule(() -> boostersMap.forEach((owner, pointData) -> {
            int centerX = pointData.tileOn().x * 8;
            int centerY = pointData.tileOn().y * 8;

            StationFx.pulse(centerX, centerY, BOOST_RADIUS, StationFx.boost);

            Groups.unit.each(u -> {
                if (u.team == Team.blue && !u.dead && u.within(centerX, centerY, BOOST_RADIUS)) {
                    u.apply(StatusEffects.overclock, BUFF_TICKS);
                    StationFx.sparks(u.x, u.y, StationFx.boost);
                }
            });
        }), 0, 3);
        Timer.schedule(Booster::renderBoosters, 0, 1);
    }

    public static boolean owns(String uuid) {
        return boostersMap.containsKey(uuid);
    }

    public static void buyBooster(Player player, Tile tile) {
        if (players.get(player.uuid()).getScore() < 18) {
            player.sendMessage(Bundle.format("station.not-enough-money", Bundle.findLocale(player.locale), 18));
        } else {
            if (!boostersMap.containsKey(player.uuid())) {
                if (!player.dead()) {
                    Tile playerTileOn = player.tileOn();
                    Tile tileUnderPlayer = Vars.world.tile(playerTileOn.x, playerTileOn.y - 1);
                    if (tile == null) {
                        tile = tileUnderPlayer;
                    }

                    if ((!player.dead() && player.team() == Team.blue && tile.block().isAir()) && tile.floor() != Blocks.empty) {
                        String text = player.name + "[gold]'s\n[orange]Booster";
                        mindustry.gen.WorldLabel label = StationUtils.createStationLabel(tile, text);
                        StationData boosterData = new StationData(player, tile, label, AuraCircle.fixed(tile.x * 8, tile.y * 8, BOOST_RADIUS, StationFx.boost));
                        boostersMap.put(player.uuid(), boosterData);
                        Call.constructFinish(tile, Blocks.beamNode, null, (byte) 0, Team.blue, null);
                        StationFx.at(Fx.teleportActivate, tile.x*8, tile.y*8, 0, StationFx.boost, null, 48);
                        players.get(player.uuid()).subtractScore(18);
                    }
                }
            }
        }
    }

    public static void renderBoosters() {
        boostersMap.forEach((owner, booster) -> {
            if (booster != null) {
                if (booster.tileOn().block() != Blocks.beamNode || booster.owner().team() != Team.blue) {
                    boostersMap.remove(owner);
                    booster.destroy();
                    if (booster.tileOn().block() == Blocks.beamNode) {
                        booster.tileOn().build.kill();
                    }
                }
            }
        });
    }

    public static void clearBoosters() {
        boostersMap.values().forEach(StationData::destroy);
        boostersMap.clear();
    }
}
