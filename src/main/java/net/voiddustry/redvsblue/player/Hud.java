package net.voiddustry.redvsblue.player;

import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.logic.LAccess;
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

        int minutes = (int) Math.floor(RedVsBluePlugin.stageTimer / 60);
        int seconds = (int) (RedVsBluePlugin.stageTimer - minutes * 60);

        String minutesString = ((minutes < 10)? "0" : "") + minutes;
        String secondsString = ((seconds < 10)? "0" : "") + seconds;

        String time = minutesString + ":" + secondsString;
        String playersText = "[royal]\uE872 " + playerCount(Team.blue) + " [scarlet]\uE872 " + playerCount(Team.crux) + "[gray] | [white]\uE872 " + playerCount();


        Groups.unit.each(u -> {
            if (u.team == Team.crux) {
                u.setProp(LAccess.ammo, u.type.ammoCapacity);
            }

        });



        Groups.player.each(player -> {
            Unit unit = player.unit();
           
            PlayerData data = RedVsBluePlugin.players.get(player.uuid());
            if (!(data == null)) {
    
                if(!RedVsBluePlugin.players.containsKey(player.uuid())) {
                    RedVsBluePlugin.players.put(player.uuid(), new PlayerData(player));
                }
        
                String textHud = (data.getLevel() == 5)? "[scarlet]Max" : "[accent]" + data.getExp() + " / " + data.getMaxExp();
      
                String hudText = "";
                if (!(player.unit() == null)) {
                    hudText = Bundle.format("game.hud", Bundle.findLocale(player.locale()), Administration.Config.serverName.get(), Math.floor(unit.health()), Math.floor(unit.shield()), data.getScore(), RedVsBluePlugin.stage, time, playersText, data.getLevel(), textHud);
                } else {
                    hudText = Bundle.format("game.hud", Bundle.findLocale(player.locale()), Administration.Config.serverName.get(), "-", "-", data.getScore(), RedVsBluePlugin.stage, time, playersText, data.getLevel(), textHud);
                }
    
                Call.setHudText(player.con, hudText);
                
                if (RedVsBluePlugin.playing && data.getUnit() != null) {
                    if (data.getUnit().dead) {
                        data.setTeam(Team.crux);
                        player.team(data.getTeam());
                    }
                }
                
                if (RedVsBluePlugin.playing && data.getUnit() != null && player.team() == Team.blue) {
                    if (!data.getUnit().dead) {
                        player.unit(data.getUnit());
                    }
                }
            }
        });
    }
}
