package net.voiddustry.redvsblue.player;


import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.Vars;

import mindustry.gen.Unit;
import mindustry.net.Administration;
import mindustry.type.UnitType;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.PlayerData;
import net.voiddustry.redvsblue.RedVsBluePlugin;
import net.voiddustry.redvsblue.game.crux.CruxUnit;

import static net.voiddustry.redvsblue.util.Utils.playerCount;

public class Hud {
    public static void update() {

        CruxUnit.checkUnitCount();

        int minutes = (int) Math.floor(RedVsBluePlugin.stageTimer / 60);
        int seconds = (int) (RedVsBluePlugin.stageTimer - minutes * 60);

        String minutesString = ((minutes < 10)? "0" : "") + minutes;
        String secondsString = ((seconds < 10)? "0" : "") + seconds;

        String time = minutesString + ":" + secondsString;
        String playersText = "[royal]\uE872 " + playerCount(Team.blue) + " [scarlet]\uE872 " + playerCount(Team.crux) + "[gray] | [white]\uE872 " + playerCount();


        Groups.unit.each(u -> {
            if (u.team == Team.crux) {
                u.ammo = u.type.ammoCapacity;
            }

        });

        Groups.unit.each(u -> Call.label("[orange]X", 0.016F, u.x, u.y));
        // allow map makers to draw fake hitboxes with erekir liquid routers
        Groups.build.each(b -> {
            if (b.block == Blocks.reinforcedLiquidRouter && b.team == Team.crux) {
                Call.label("[orange]X", 0.016F, b.x, b.y);
            }
        });


        //allows mappers to edit 'redSpawns', only changes 'redSpawns',removes the objectiveFlag and the 100 team erekir liquid routers placed in order to send coordinates
        //very cringe code
        if (Vars.state.rules.objectiveFlags.contains("updateRedSpawns")) {
            Vars.state.rules.objectiveFlags.remove("updateRedSpawns");
            //hopefully no crash due to empty spawns
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


        Groups.player.each(player -> {
            Unit unit = player.unit();

            if(!RedVsBluePlugin.players.containsKey(player.uuid())) {
                RedVsBluePlugin.players.put(player.uuid(), new PlayerData(player));
            }
            PlayerData data = RedVsBluePlugin.players.get(player.uuid());

            String textHud = (data.getLevel() == 5)? "[scarlet]Max" : "[accent]" + data.getExp() + " / " + data.getMaxExp();

            String hudText = Bundle.format("game.hud", Bundle.findLocale(player.locale()), Administration.Config.serverName.get(), Math.floor(unit.health()), Math.floor(unit.shield()), data.getScore(), RedVsBluePlugin.stage, time, playersText, data.getLevel(), textHud);
            Call.setHudText(player.con, hudText);

            if (RedVsBluePlugin.playing && data.getUnit() != null) {
                if (data.getUnit().dead) {
                    data.setTeam(Team.crux);
                    player.team(data.getTeam());
                }
            }

            if (player.unit().type == UnitTypes.quasar && player.unit().shield >= -10 && player.unit().shield <= 0) {
                player.unit().shield = 300;
            }

            if (RedVsBluePlugin.playing && data.getUnit() != null && player.team() == Team.blue) {
                if (!data.getUnit().dead) {
                    player.unit(data.getUnit());
                }
            }
        });
    }
}
