package com.legame;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

public class CoupleWeapons {

    /** Bonus di danno quando il partner e' vicino. */
    static void bonus(LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayerEntity p) || p.getWorld().isClient) return;
        MinecraftServer server = p.getServer();
        if (server == null) return;
        CoupleData.Couple c = CoupleData.get(server).byPlayer.get(p.getUuid());
        if (c == null) return;
        ServerPlayerEntity partner = server.getPlayerManager().getPlayer(c.other(p.getUuid()));
        if (partner == null || partner == target || partner.getWorld() != p.getWorld() || partner.distanceTo(p) > 12) return;
        float extra = 2f + c.level() * 0.5f;
        target.timeUntilRegen = 0;
        target.damage(p.getDamageSources().playerAttack(p), extra);
        ((ServerWorld) p.getWorld()).spawnParticles(ParticleTypes.HEART, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
    }

    public static class CoupleSword extends SwordItem {
        public CoupleSword(Settings s) { super(ToolMaterials.NETHERITE, 3, -2.4f, s); }

        @Override
        public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            bonus(target, attacker);
            return super.postHit(stack, target, attacker);
        }

        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {
            tooltip.add(Text.literal("Piu' danno se il partner e' entro 12 blocchi").formatted(Formatting.LIGHT_PURPLE));
        }
    }

    public static class CoupleAxe extends AxeItem {
        public CoupleAxe(Settings s) { super(ToolMaterials.NETHERITE, 5f, -3.0f, s); }

        @Override
        public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            bonus(target, attacker);
            return super.postHit(stack, target, attacker);
        }

        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {
            tooltip.add(Text.literal("Piu' danno se il partner e' entro 12 blocchi").formatted(Formatting.LIGHT_PURPLE));
        }
    }

    public static class BetrayalDagger extends SwordItem {
        public BetrayalDagger(Settings s) { super(ToolMaterials.IRON, 2, -1.6f, s); }

        @Override
        public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1), attacker);
            return super.postHit(stack, target, attacker);
        }

        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext ctx) {
            tooltip.add(Text.literal("Il pugnale di chi ha tradito. Avvelena col Wither.").formatted(Formatting.DARK_RED));
        }
    }
}
