package net.voiddustry.redvsblue.game.crux;

import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.PlayerData;
import net.voiddustry.redvsblue.RedVsBluePlugin;

public class BossChooseMenu {
    public static void openMenu(Player player) {
        if (player.team() != Team.crux) {
            return;
        }

        PlayerData data = RedVsBluePlugin.players.get(player.uuid());

        int menu = Menus.registerMenu(((player1, option) -> {
            if (option < 0) {
                return;
            }

            switch (option) {
                case 0 -> data.setSelectedBoss(UnitTypes.mace);
            }
        }));

        String title = Bundle.get("boss.title", player.locale);

        String[][] buttons = {
                { Bundle.get("boss.bosses.mace", player.locale) }
        };

        Call.menu(player.con, menu, title, "", buttons);
    }
}
