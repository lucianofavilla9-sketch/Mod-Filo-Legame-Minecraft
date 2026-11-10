package com.legame;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

public class ThreadItem extends Item {
    private final int level;

    public ThreadItem(int level, Settings settings) {
        super(settings);
        this.level = level;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Livello " + level + " - " + CoupleManager.THREAD_NAMES[level - 1]).formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("Simbolo del vostro legame").formatted(Formatting.GRAY));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return level >= 8;
    }
}
