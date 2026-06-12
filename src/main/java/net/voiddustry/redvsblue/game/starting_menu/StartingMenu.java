package net.voiddustry.redvsblue.game.starting_menu;

import arc.struct.ObjectMap;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.net.NetConnection;
import mindustry.type.UnitType;
import mindustry.ui.Menus;
import net.voiddustry.redvsblue.Bundle;
import net.voiddustry.redvsblue.PlayerData;
import net.voiddustry.redvsblue.RedVsBluePlugin;
import net.voiddustry.redvsblue.util.Utils;

public class StartingMenu {

    private final ObjectMap<Integer ,StartingItem> selectedItemsList = new ObjectMap<>();

    public static boolean canOpenMenu;

    // settings

    public static void openMenu(Player player, int category) {
        NetConnection connection = player.con;
        if (canOpenMenu) {
            int menu = Menus.registerMenu((player1, option) -> {
                if (option < 0) {
                    return;
                }

                if (option <= 3) {
                    switch (option) {
                        case 0 -> openMenu(player, 0);
                        case 1 -> openMenu(player, 1);
                        case 2 -> openMenu(player, 2);
                        case 3 -> openMenu(player, 4);
                    }
                }
            });

            String[] categories = {"[gray]X", "[lime]Unit", "[royal]Build", "[red]Item"};
            String[] util = {"[cyan]Finish", "[lime]20", "[lightgray]Close"};

            switch (category) {
                case 0 -> {

                    String[][] buttons = {
                            categories,
                            {"[accent]Random"},
                            {""},
                            {""},
                            {""},
                            {""},
                            {""},
                            util
                    };
                    menu(connection, menu, "", buttons);
                }
            }
        } else {
            // TODO:
        }

    }

    private static void menu(NetConnection connection, int menu, String text, String[][] buttons) {
        Call.menu(connection, menu, Bundle.get("menu.build.title", connection.player.locale), text, buttons);
    }

    private static void giveUnit(Player player, UnitType unitType) {
        PlayerData playerData = RedVsBluePlugin.players.get(player.uuid());

        Unit unit = unitType.spawn(Team.blue, player.x(), player.y());
        unit.health = unit.type.health/2;

        if (!unit.dead()) {
            Unit oldUnit = playerData.getUnit();
            playerData.setUnit(unit);

            player.unit(unit);
            oldUnit.kill();

            playerData.subtractScore(StartingItems.getStartingItem(unitType.name).cost);
        }
    }
}
