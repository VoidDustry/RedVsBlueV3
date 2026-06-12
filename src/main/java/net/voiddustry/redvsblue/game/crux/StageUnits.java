package net.voiddustry.redvsblue.game.crux;

import arc.struct.ObjectMap;
import mindustry.content.UnitTypes;
import mindustry.type.UnitType;

public class StageUnits {
    //String - Key
    public final static ObjectMap<UnitType, String> firstStage = ObjectMap.of(
            UnitTypes.crawler, "units.crux.menu.crawler",
            UnitTypes.dagger, "units.crux.menu.dagger"
    );

    public final static ObjectMap<UnitType, String> secondStage = ObjectMap.of(
            UnitTypes.crawler, "units.crux.menu.crawler",
            UnitTypes.dagger, "units.crux.menu.dagger",
            UnitTypes.mace, "units.crux.menu.mace"
    );

    public final static ObjectMap<UnitType, String> thirdStage = ObjectMap.of(
            UnitTypes.crawler, "units.crux.menu.crawler",
            UnitTypes.dagger, "units.crux.menu.dagger",
            UnitTypes.mace, "units.crux.menu.mace"
    );

    public final static ObjectMap<UnitType, String> fourthAndFifrhStage = ObjectMap.of(
            UnitTypes.crawler, "units.crux.menu.crawler",
            UnitTypes.merui, "units.crux.menu.merui",
            UnitTypes.dagger, "units.crux.menu.dagger",
            UnitTypes.mace, "units.crux.menu.mace",
            UnitTypes.stell, "units.crux.menu.stell"
    );

    public final static ObjectMap<UnitType, String> sixthStage = ObjectMap.of(
            UnitTypes.crawler, "units.crux.menu.crawler",
            UnitTypes.elude, "units.crux.menu.elude",
            UnitTypes.merui, "units.crux.menu.merui",
            UnitTypes.dagger, "units.crux.menu.dagger",
            UnitTypes.nova, "units.crux.menu.nova",
            UnitTypes.cleroi, "units.crux.menu.cleroi"
    );

    public final static ObjectMap<UnitType, String> sevenStage = ObjectMap.of(
            UnitTypes.cleroi, "units.crux.menu.cleroi",
            UnitTypes.flare, "units.crux.menu.flare",
            UnitTypes.horizon, "units.crux.menu.horizon",
            UnitTypes.elude, "units.crux.menu.elude"
    );

    public final static ObjectMap<UnitType, String> eighthAndNinthStage = ObjectMap.of(
            UnitTypes.fortress, "units.crux.menu.fortress",
            UnitTypes.cleroi, "units.crux.menu.cleroi",
            UnitTypes.locus, "units.crux.menu.locus"
    );

    public final static ObjectMap<UnitType, String> tenthStage = ObjectMap.of(
            UnitTypes.obviate, "units.crux.menu.obviate",
            UnitTypes.precept, "units.crux.menu.precept"
    );

    public final static ObjectMap<UnitType, String> eleventhStage = ObjectMap.of(
            UnitTypes.corvus, "units.crux.menu.corvus",
            UnitTypes.vanquish, "units.crux.menu.vanquish"
    );

    public final static ObjectMap<Integer, UnitType> bosses = ObjectMap.of(
            2, UnitTypes.horizon,
            3, UnitTypes.locus,
            4, UnitTypes.quasar,
            5, UnitTypes.avert,
            6, UnitTypes.spiroct,
            7, UnitTypes.precept,
            8, UnitTypes.tecta,
            9, UnitTypes.vanquish,
            10, UnitTypes.eclipse,
            11, UnitTypes.evoke,
            12, UnitTypes.navanax
    );
}
