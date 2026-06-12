package net.voiddustry.redvsblue.game.stations;

import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import net.voiddustry.redvsblue.Bundle;

public class StationsMenu {
    public static void openMenu(Player player) {
        if (!player.dead() && player.team() == Team.blue) {
            int menu = Menus.registerMenu((player1, option) -> {
                if (option < 0) {
                    return;
                }

                switch (option) {
                    case 0 -> SuppressorTower.buyTower(player, null);
                    case 1 -> Miner.buyMiner(player, null);
                    case 2 -> RepairPoint.buyRepairPoint(player, null);
                    case 3 -> ArmorWorkbench.buyWorkbench(player, null);
                    case 4 -> Laboratory.buyLab(player, null);
                    case 5 -> Recycler.buyRecycler(player, null);
                    case 6 -> Booster.buyBooster(player, null);
                }
            });


            String title = Bundle.get("stations.title", player.locale);
            String text = Bundle.get("stations.text", player.locale);

            String[][] buttons = {
                    { Bundle.get("stations.buttons.suppressor-tower", player.locale)},
                    { Bundle.get("stations.buttons.miner", player.locale) },
                    { Bundle.get("stations.buttons.repair-point", player.locale) },
                    { Bundle.get("stations.buttons.workbench", player.locale)},
                    { Bundle.get("stations.buttons.lab", player.locale)},
                    { Bundle.get("stations.buttons.recycler", player.locale)},
                    { Bundle.get("stations.buttons.booster", player.locale)},
                    { Bundle.get("stations.buttons.close", player.locale)}
            };

            Call.menu(player.con, menu, title, text, buttons);
        }
    }
}
