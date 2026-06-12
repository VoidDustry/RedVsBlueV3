package net.voiddustry.redvsblue.util;

import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
public class MapVote {

    private static final Map<String, Integer> playersVotesMap = new HashMap<>();
    private static Seq<Integer> avaibleMapnumbers = new Seq<Integer>();
    public static Seq<String> pinnedMaps = new Seq<String>();

    public static void callMapVoting() {
        if (!Utils.voting) {
            Random rand = new Random();
            avaibleMapnumbers.clear();
            for (int mapnum = 0; mapnum < 6; mapnum++) {
                avaibleMapnumbers.add(rand.nextInt(getMaps().size));
            }
            playersVotesMap.clear();
            Utils.voting = true;
            Utils.gameRun = false;
            int[] i = {61};
            Events.fire("rvsb-world-reload");

            Timer.Task task = new Timer.Task() {
                @Override
                public void run() {
                    Seq<mindustry.maps.Map> maps = getMaps();

                    StringBuilder mapsList = new StringBuilder();

                    int[] mapVotes = getMapVotes();
                    for (int j = 0; j < getMaps().size; j++) {
                        if (avaibleMapnumbers.contains(j)) {
                            int mapNumber = j + 1;
                            mapsList.append("\n[lightgray]").append(mapNumber).append(" [gray]| [gold]").append(mapVotes[j]).append(" [gray]| ").append(maps.get(j).file.name().replace(".msav", ""));
                        } else if (pinnedMaps.contains(maps.get(j).file.name())) {
                            int mapNumber = j + 1;
                            mapsList.append("\n[lightgray]").append(mapNumber).append(" [gray]| [gold]").append(mapVotes[j]).append(" [gray]| ").append("[blue]").append(maps.get(j).file.name().replace(".msav", ""));
                        }
                    }
                    

                    Groups.player.each(p -> Call.infoPopup(p.con, "[gray][ [royal]Vote []]\n" + mapsList + "\n\n[gold]Time left: " + Arrays.toString(i), 1, 0, 0, (p.con.mobile)? 300 : 600, 0, 0));

                    i[0]--;

                    if (i[0] == 0) {
                        this.cancel();
                        reloadWorld(() -> {
                            mindustry.maps.Map target = getMostVotedMap();
                            Vars.world.loadMap(target, target.applyRules(Vars.state.rules.mode()));
                        });
                    }
                }
            };
            Timer timer = new Timer();
            timer.scheduleTask(task, 0, 1);
        }
    }

    private static mindustry.maps.Map getMostVotedMap() {
        Seq<mindustry.maps.Map> maps = getMaps();
        int[] votes = getMapVotes();
        int largest = 0;
        Random rand = new Random();

        for (int i = 0; i < votes.length; i++) {
            largest = votes[i] > votes[largest] ? i : largest;
        }
        
        if (votes[largest] == 0) {
            largest = rand.nextInt(getMaps().size-1);
        }

        return maps.get(largest);

    }

    private static int[] getMapVotes() {
        int[] votes = new int[getMaps().size];
        playersVotesMap.forEach((key, voteNumber) -> votes[voteNumber]++);
        return votes;
    }

    public static void registerVote(Player player, Integer voteNumber) {
        if (!playersVotesMap.containsKey(player.uuid())) {
            if (avaibleMapnumbers.contains(voteNumber-1) || pinnedMaps.contains(getMaps().get(voteNumber-1).file.name())) {
                playersVotesMap.put(player.uuid(), voteNumber-1);
            }
        }
    }

    public static Seq<mindustry.maps.Map> getMaps() {
        return Vars.maps.customMaps();
    }

    public static void reloadWorld(Runnable runnable) {
        try {
            var reloader = new WorldReloader();
            reloader.begin();

            runnable.run();
            Vars.state.rules = Vars.state.map.applyRules(Vars.state.rules.mode());
            Vars.logic.play();

            reloader.end();
        } catch (MapException e) {
            Log.err("@: @", e.map.name(), e.getMessage());
        }
    }

}
