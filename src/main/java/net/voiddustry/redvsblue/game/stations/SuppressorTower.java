package net.voiddustry.redvsblue.game.stations;

import net.voiddustry.redvsblue.util.AuraCircle;
import net.voiddustry.redvsblue.util.StationFx;
import arc.graphics.Color;
import arc.util.Timer;
import arc.struct.Seq;

import arc.util.Timer;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import arc.math.Mathf;
import mindustry.gen.Player;
import mindustry.world.Tile;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.game.stations.stationData.StationData;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static net.voiddustry.redvsblue.RedVsBluePlugin.players;

public class SuppressorTower {
    private static final Map<String, StationData> suppressorTowerMap = new ConcurrentHashMap<>();
    private static final Seq<String> suppressorPlaced = new Seq<>();

    private static int tick;

    public static void initTimer() {
        Timer.schedule(() -> tick++, 0, 0.5F);
        Timer.schedule(() -> suppressorTowerMap.forEach((owner, pointData) -> {
            int centerX = pointData.tileOn().x * 8;
            int centerY = pointData.tileOn().y * 8;

            // range ring every 1.5 s, lightning to at most 3 of the units in range every tick
            if (tick % 3 == 0) StationFx.ring(centerX, centerY, 128, StationFx.suppress);
            Seq<mindustry.gen.Unit> inRange = new Seq<>();

            Groups.unit.each(u -> {
               if (u.team() == Team.crux) {
                    if (u.dst(centerX, centerY) <= 128) {
                        float removedHp = u.maxHealth / 125;

                        u.health -= removedHp;
                        u.apply(StatusEffects.sapped, 50);

                        String removedHpText = "[#55557F]-" + Math.round(removedHp*10)/10;
                        StationFx.label(removedHpText, 0.5F, u.x, u.y);
                        inRange.add(u);
                    }

                }
            });
            inRange.shuffle();
            for (int k = 0; k < Math.min(3, inRange.size); k++) {
                StationFx.zap(centerX, centerY, inRange.get(k).x, inRange.get(k).y, StationFx.suppress);
            }
            if (!inRange.isEmpty()) {
                // low, very quiet arc crackle on every shot
                Call.soundAt(Sounds.shootArc, centerX, centerY, 0.12f, Mathf.random(0.5f, 0.58f));
            }
        }), 0, 0.5F);
        Timer.schedule(SuppressorTower::renderSuppressorTowers, 0, 0.5F);
    }

    public static boolean owns(String uuid) {
        return suppressorTowerMap.containsKey(uuid) || suppressorPlaced.contains(uuid);
    }

    public static void buyTower(Player player, Tile tile) {
        if (players.get(player.uuid()).getScore() < 20) {
            player.sendMessage(Bundle.format("station.not-enough-money", Bundle.findLocale(player.locale), 20));
        } else {
            if (!suppressorTowerMap.containsKey(player.uuid()) && !suppressorPlaced.contains(player.uuid())) {
                if (!player.dead()) {
                    Tile playerTileOn = player.tileOn();
                    Tile tileUnderPlayer = Vars.world.tile(playerTileOn.x, playerTileOn.y - 1);
                    if (tile == null) {
                        tile = tileUnderPlayer;
                    }

                    final Tile placeTile = tile;
                    if ((!player.dead() && player.team() == Team.blue && tile.block().isAir()) && tile.floor() != Blocks.empty) {
                        Call.constructFinish(tile, Blocks.phaseWall, null, (byte) 0, Team.blue, null);
                        players.get(player.uuid()).subtractScore(20);
                        StationUtils.drawStationName(tile, player.name + "[gold]'s\n[accent]Tesla Tower" + " - deploying", 10.5F);
                        suppressorPlaced.add(player.uuid());
                        Timer.schedule(() -> {
                            String text = player.name + "[gold]'s\n[accent]Tesla Tower";
                            mindustry.gen.WorldLabel label = StationUtils.createStationLabel(placeTile, text);
                            StationData towerData = new StationData(player, placeTile, label, AuraCircle.fixed(placeTile.x * 8, placeTile.y * 8, 128, StationFx.suppress));
                            suppressorTowerMap.put(player.uuid(), towerData);
                            suppressorPlaced.remove(player.uuid());
                            StationFx.at(Fx.teleportActivate, placeTile.x*8, placeTile.y*8, 0, StationFx.suppress, null, 48);
                        }, 10);
                    }
                }
            }
        }
    }

    public static void renderSuppressorTowers() {
        suppressorTowerMap.forEach((owner, tower) -> {
            if (tower != null) {
                if (tower.tileOn().block() != Blocks.phaseWall || tower.owner().team() != Team.blue) {
                    suppressorTowerMap.remove(owner);
                    tower.destroy();
                    if (tower.tileOn().block() == Blocks.phaseWall) {
                        tower.tileOn().build.kill();
                    }
                }
            }
        });
    }

    public static void clearTowers() {
        suppressorTowerMap.values().forEach(StationData::destroy);
        suppressorTowerMap.clear();
        suppressorPlaced.clear();
    }
}
