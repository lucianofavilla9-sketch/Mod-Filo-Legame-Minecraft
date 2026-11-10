package com.legame;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    public static final Item[] THREADS = new Item[10];
    public static Item COUPLE_SWORD, COUPLE_AXE, BETRAYAL_DAGGER;

    private static <T extends Item> T reg(String name, T item) {
        return Registry.register(Registries.ITEM, new Identifier(LegameMod.MOD_ID, name), item);
    }

    public static void register() {
        for (int i = 0; i < 10; i++) {
            Rarity r = i < 3 ? Rarity.COMMON : i < 6 ? Rarity.UNCOMMON : i < 9 ? Rarity.RARE : Rarity.EPIC;
            THREADS[i] = reg("thread_" + (i + 1), new ThreadItem(i + 1, new FabricItemSettings().maxCount(1).rarity(r)));
        }
        COUPLE_SWORD = reg("couple_sword", new CoupleWeapons.CoupleSword(new FabricItemSettings().rarity(Rarity.RARE)));
        COUPLE_AXE = reg("couple_axe", new CoupleWeapons.CoupleAxe(new FabricItemSettings().rarity(Rarity.RARE)));
        BETRAYAL_DAGGER = reg("betrayal_dagger", new CoupleWeapons.BetrayalDagger(new FabricItemSettings().rarity(Rarity.EPIC)));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            for (Item i : THREADS) e.add(i);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(COUPLE_SWORD);
            e.add(COUPLE_AXE);
            e.add(BETRAYAL_DAGGER);
        });
    }
}
