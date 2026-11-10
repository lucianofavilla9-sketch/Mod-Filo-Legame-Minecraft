package com.legame;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class CoupleCommands {

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("coppia")
            .executes(CoupleCommands::help)
            .then(CommandManager.literal("richiedi").then(CommandManager.argument("giocatore", EntityArgumentType.player()).executes(CoupleCommands::request)))
            .then(CommandManager.literal("accetta").executes(CoupleCommands::accept))
            .then(CommandManager.literal("rifiuta").executes(CoupleCommands::deny))
            .then(CommandManager.literal("rompi").executes(CoupleCommands::breakUp))
            .then(CommandManager.literal("info").executes(CoupleCommands::info))
            .then(CommandManager.literal("missioni").executes(CoupleCommands::missions))
            .then(CommandManager.literal("premi").executes(CoupleCommands::rewards))
            .then(CommandManager.literal("tradisci").then(CommandManager.argument("giocatore", EntityArgumentType.player()).executes(CoupleCommands::cheat)))
            .then(CommandManager.literal("uccidi").executes(CoupleCommands::kill)));
    }

    static int help(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        String[] lines = {
            "=== LEGAME - Comandi ===",
            "/coppia richiedi <giocatore>  - chiedi di diventare coppia",
            "/coppia accetta | rifiuta",
            "/coppia info | missioni | premi",
            "/coppia rompi  - lascia il partner",
            "/coppia tradisci <giocatore>  - tradisci il partner",
            "/coppia uccidi  - uccidi il partner (animazione!)",
            "Click destro sul partner: mano vuota = abbraccio, sneak = bacio, cibo = imbocchi"
        };
        for (String l : lines) CoupleManager.msg(p, l, Formatting.LIGHT_PURPLE);
        return 1;
    }

    static int request(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "giocatore");
        MinecraftServer s = ctx.getSource().getServer();
        CoupleData d = CoupleData.get(s);
        if (t == p) { CoupleManager.msg(p, "Non puoi fare coppia con te stesso.", Formatting.RED); return 0; }
        if (d.byPlayer.containsKey(p.getUuid())) { CoupleManager.msg(p, "Hai gia' un partner!", Formatting.RED); return 0; }
        if (d.byPlayer.containsKey(t.getUuid())) { CoupleManager.msg(p, t.getGameProfile().getName() + " ha gia' un partner.", Formatting.RED); return 0; }
        CoupleManager.pending.put(t.getUuid(), p.getUuid());
        CoupleManager.pendingTick.put(t.getUuid(), s.getTicks());
        CoupleManager.msg(p, "Richiesta inviata a " + t.getGameProfile().getName() + " \u2764", Formatting.LIGHT_PURPLE);
        t.sendMessage(Text.literal("\u2764 " + p.getGameProfile().getName() + " vuole fare coppia con te! Clicca qui o scrivi /coppia accetta")
            .formatted(Formatting.LIGHT_PURPLE)
            .styled(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/coppia accetta"))), false);
        return 1;
    }

    static int accept(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        MinecraftServer s = ctx.getSource().getServer();
        UUID req = CoupleManager.pending.get(p.getUuid());
        Integer when = CoupleManager.pendingTick.get(p.getUuid());
        if (req == null || when == null || s.getTicks() - when > 1200) {
            CoupleManager.msg(p, "Nessuna richiesta valida.", Formatting.RED);
            return 0;
        }
        ServerPlayerEntity r = CoupleManager.online(s, req);
        CoupleData d = CoupleData.get(s);
        CoupleManager.pending.remove(p.getUuid());
        if (r == null) { CoupleManager.msg(p, "Il giocatore non e' online.", Formatting.RED); return 0; }
        if (d.byPlayer.containsKey(p.getUuid()) || d.byPlayer.containsKey(r.getUuid())) {
            CoupleManager.msg(p, "Uno di voi ha gia' un partner.", Formatting.RED);
            return 0;
        }
        CoupleManager.createCouple(s, r, p);
        return 1;
    }

    static int deny(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        UUID req = CoupleManager.pending.remove(p.getUuid());
        if (req != null) {
            ServerPlayerEntity r = CoupleManager.online(ctx.getSource().getServer(), req);
            if (r != null) CoupleManager.msg(r, p.getGameProfile().getName() + " ha rifiutato la richiesta.", Formatting.RED);
        }
        CoupleManager.msg(p, "Richiesta rifiutata.", Formatting.GRAY);
        return 1;
    }

    static CoupleData.Couple mine(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity p) {
        CoupleData.Couple c = CoupleData.get(ctx.getSource().getServer()).byPlayer.get(p.getUuid());
        if (c == null) CoupleManager.msg(p, "Non hai un partner. Usa /coppia richiedi <giocatore>.", Formatting.RED);
        return c;
    }

    static int breakUp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        MinecraftServer s = ctx.getSource().getServer();
        CoupleData.Couple c = mine(ctx, p);
        if (c == null) return 0;
        ServerPlayerEntity o = CoupleManager.online(s, c.other(p.getUuid()));
        CoupleManager.removeCouple(s, c);
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1.5, p.getZ(), 15, 0.4, 0.3, 0.4, 0.1);
        CoupleManager.title(p, "\uD83D\uDC94 FILO SPEZZATO", "Non siete piu' una coppia", Formatting.GRAY);
        if (o != null) {
            CoupleManager.title(o, "\uD83D\uDC94 FILO SPEZZATO", p.getGameProfile().getName() + " ti ha lasciato", Formatting.GRAY);
        }
        return 1;
    }

    static int info(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        CoupleData.Couple c = mine(ctx, p);
        if (c == null) return 0;
        int lvl = c.level();
        String next = lvl < 10 ? (CoupleData.THRESH[lvl] - c.xp) + " XP al prossimo livello" : "LIVELLO MASSIMO";
        CoupleManager.msg(p, "\u2764 Partner: " + c.nameOf(c.other(p.getUuid())), Formatting.LIGHT_PURPLE);
        CoupleManager.msg(p, "Filo: " + CoupleManager.THREAD_NAMES[lvl - 1] + " (livello " + lvl + "/10)", Formatting.GOLD);
        CoupleManager.msg(p, "XP: " + c.xp + "  -  " + next, Formatting.YELLOW);
        CoupleManager.msg(p, "Lunghezza filo: " + (8 + lvl * 4) + " blocchi", Formatting.AQUA);
        return 1;
    }

    static int missions(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        CoupleData.Couple c = mine(ctx, p);
        if (c == null) return 0;
        CoupleManager.msg(p, "=== Missioni di coppia ===", Formatting.LIGHT_PURPLE);
        for (int i = 0; i < Missions.COUNT; i++) {
            CoupleManager.msg(p, Missions.NAMES[i] + ": " + c.progress[i] + "/" + Missions.TARGET[i]
                + "  (+" + Missions.REWARD[i] + " XP, completate " + c.done[i] + "x)", Formatting.WHITE);
        }
        return 1;
    }

    static int rewards(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        CoupleData.Couple c = mine(ctx, p);
        if (c == null) return 0;
        CoupleManager.giveRewards(p, c);
        return 1;
    }

    static int cheat(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        MinecraftServer s = ctx.getSource().getServer();
        ServerPlayerEntity t = EntityArgumentType.getPlayer(ctx, "giocatore");
        CoupleData.Couple c = mine(ctx, p);
        if (c == null) return 0;
        UUID pid = c.other(p.getUuid());
        if (t == p || t.getUuid().equals(pid)) { CoupleManager.msg(p, "Scegli un'altra persona per tradire...", Formatting.RED); return 0; }
        if (t.getWorld() != p.getWorld() || t.distanceTo(p) > 6) { CoupleManager.msg(p, "Devi stare vicino a " + t.getGameProfile().getName() + " (max 6 blocchi).", Formatting.RED); return 0; }
        if (!CoupleManager.ready(s, c, "cheat", 2400)) { CoupleManager.msg(p, "Calma, sei appena stato scoperto. Aspetta un po'.", Formatting.RED); return 0; }

        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.HEART, t.getX(), t.getY() + 2, t.getZ(), 10, 0.4, 0.3, 0.4, 0.1);
        w.spawnParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 25, 0.4, 0.6, 0.4, 0.05);
        CoupleManager.msg(p, "Hai tradito " + c.nameOf(pid) + " con " + t.getGameProfile().getName() + "... il filo si incrina. (-200 XP)", Formatting.DARK_RED);
        CoupleManager.msg(t, p.getGameProfile().getName() + " ti ha fatto delle avance...", Formatting.YELLOW);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 1200));
        CoupleManager.give(p, new ItemStack(ModItems.BETRAYAL_DAGGER));

        ServerPlayerEntity partner = CoupleManager.online(s, pid);
        if (partner != null) {
            CoupleManager.title(partner, "\uD83D\uDC94 TI HA TRADITO", p.getGameProfile().getName() + " e " + t.getGameProfile().getName() + "...", Formatting.DARK_RED);
            partner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
            partner.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0));
            partner.getServerWorld().spawnParticles(ParticleTypes.DAMAGE_INDICATOR, partner.getX(), partner.getY() + 1.5, partner.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
        }
        s.getPlayerManager().broadcast(Text.literal("\uD83D\uDC94 " + p.getGameProfile().getName() + " ha tradito " + c.nameOf(pid) + "!").formatted(Formatting.RED), false);
        CoupleManager.addXp(s, c, -200);
        return 1;
    }

    static int kill(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        MinecraftServer s = ctx.getSource().getServer();
        CoupleData.Couple c = mine(ctx, p);
        if (c == null) return 0;
        ServerPlayerEntity v = CoupleManager.online(s, c.other(p.getUuid()));
        if (v == null || v.getWorld() != p.getWorld() || v.distanceTo(p) > 8) {
            CoupleManager.msg(p, "Il partner deve essere online e a meno di 8 blocchi.", Formatting.RED);
            return 0;
        }
        CoupleManager.murder(s, p, v, c);
        return 1;
    }
}
