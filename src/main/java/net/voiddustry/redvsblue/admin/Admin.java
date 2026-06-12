package net.voiddustry.redvsblue.admin;

import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import net.voiddustry.redvsblue.game.crux.Boss;
import net.voiddustry.redvsblue.util.Utils;

public class Admin {
    public static void openAdminPanelMenu(Player player) {
        int menu = Menus.registerMenu((playerInMenu, option) -> {
            if (option < 0) {
                return;
            }

            switch (option) {
                case 0 -> Boss.spawnBoss(Utils.getRandomPlayer(Team.crux));
            }
        });
        Call.menu(player.con, menu, "[scarlet]Admin Panel", "", new String[][]{{"[red]Spawn Random Boss"}});
    }
}
