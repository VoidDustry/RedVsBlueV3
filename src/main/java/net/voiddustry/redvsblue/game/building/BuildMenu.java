package net.voiddustry.redvsblue.game.building;

import mindustry.gen.Call;
import mindustry.gen.Player;

import mindustry.net.NetConnection;
import mindustry.ui.Menus;
import net.voiddustry.redvsblue.Bundle;

import java.util.HashMap;
import java.util.Map;

public class BuildMenu {

    private static final Map<Player, BlocksType> selectedBlock = new HashMap<>();
    private static final Map<Player, Boolean> singleBlock = new HashMap<>();

    public static void openMenu(Player player) {
        if (player != null) {
            if (!selectedBlock.containsKey(player)) {
                selectedBlock.put(player, BlocksTypes.berylliumWall);
                singleBlock.put(player, true);
            }

            int menu = Menus.registerMenu(((player1, option) -> {
                switch (option) {
                    case 1 -> {
                        if (singleBlock.get(player)) {
                            singleBlock.put(player, false);
                        } else {
                            singleBlock.put(player, true);
                        }
                        openMenu(player);
                    }
                    case 3 -> BuildBlock.add(new BuildTicket(player, selectedBlock.get(player).block, singleBlock.get(player), false, selectedBlock.get(player).cost));
                    case 4 -> BuildBlock.findAndRemove(player);

                    case 5 -> {
                        selectedBlock.put(player, BlocksTypes.berylliumWall);
                        openMenu(player);
                    }
                    case 6 -> {
                        selectedBlock.put(player, BlocksTypes.berylliumWallLarge);
                        openMenu(player);
                    }
                    case 7 -> {
                        selectedBlock.put(player, BlocksTypes.thoriumWall);
                        openMenu(player);
                    }
                    case 8 -> {
                        selectedBlock.put(player, BlocksTypes.thoriumWallLarge);
                        openMenu(player);
                    }
                    case 9 -> {
                        selectedBlock.put(player, BlocksTypes.doorLarge);
                        openMenu(player);
                    }
                    case 10 -> {
                        selectedBlock.put(player, BlocksTypes.drill);
                        openMenu(player);
                    }
                    case 11 -> {
                        selectedBlock.put(player, BlocksTypes.laserDrill);
                        openMenu(player);
                    }
                    case 12 -> {
                        selectedBlock.put(player, BlocksTypes.powerNode);
                        openMenu(player);
                    }
                    case 13 -> {
                        selectedBlock.put(player, BlocksTypes.battery);
                        openMenu(player);
                    }
                    case 14 -> {
                        selectedBlock.put(player, BlocksTypes.combustion);
                        openMenu(player);
                    }
                    case 15 -> {
                        selectedBlock.put(player, BlocksTypes.rtg);
                        openMenu(player);
                    }
                    case 16 -> {
                        selectedBlock.put(player, BlocksTypes.repair);
                        openMenu(player);
                    }
                    case 17 -> {
                        selectedBlock.put(player, BlocksTypes.pump);
                        openMenu(player);
                    }
                }
            }));

            BlocksType sBlock = selectedBlock.get(player);

            String text = Bundle.format("menu.build.text", Bundle.findLocale(player.locale), sBlock.color, sBlock.name, sBlock.block.health, sBlock.cost);

            String[][] buttons = {
                    {
                            "[gray][ [cyan]<- [gray]]",
                            (singleBlock.get(player))? "[lime]" + Bundle.get("menu.build.single", player.locale) : "[scarlet]" + Bundle.get("menu.build.single", player.locale),
                            "[gray][ [cyan]-> [gray]]",

                    },
                    {
                            Bundle.get("menu.build.build", player.locale),
                            Bundle.get("menu.build.cancel", player.locale),
                    },
                    {BlocksTypes.berylliumWall.color + BlocksTypes.berylliumWall.name},
                    {BlocksTypes.berylliumWallLarge.color + BlocksTypes.berylliumWallLarge.name},
                    {BlocksTypes.thoriumWall.color + BlocksTypes.thoriumWallLarge.name},
                    {BlocksTypes.thoriumWallLarge.color + BlocksTypes.thoriumWallLarge.name},
                    {BlocksTypes.doorLarge.color + BlocksTypes.doorLarge.name},
                    {BlocksTypes.drill.color + BlocksTypes.drill.name},
                    {BlocksTypes.laserDrill.color + BlocksTypes.laserDrill.name},
                    {BlocksTypes.powerNode.color + BlocksTypes.powerNode.name},
                    {BlocksTypes.battery.color + BlocksTypes.battery.name},
                    {BlocksTypes.combustion.color + BlocksTypes.combustion.name},
                    {BlocksTypes.rtg.color + BlocksTypes.rtg.name},
                    {BlocksTypes.repair.color + BlocksTypes.repair.name},
                    {BlocksTypes.pump.color + BlocksTypes.pump.name}
            };
            menu(player.con, menu, text, buttons);
        }
    }

    private static void menu(NetConnection connection, int menu, String text, String[][] buttons) {
        Call.menu(connection, menu, Bundle.get("menu.build.title", connection.player.locale), text, buttons);
    }
}
