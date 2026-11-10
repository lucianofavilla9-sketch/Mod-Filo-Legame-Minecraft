package com.legame;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.*;

public class CoupleManager {
    public static final int[] COLORS = {
        0x9E9E9E, 0xFF8FB8, 0xE53935, 0xFB8C00, 0xFDD835,
        0x43A047, 0x00ACC1, 0x1E53E5, 0x8E24AA, 0xFFD700
    };
    public static final String[] THREAD_NAMES = {
        "Filo Grigio", "Filo Rosa", "Filo Rosso", "Filo Arancione", "Filo Giallo",
        "Filo Verde", "Filo Ciano", "Filo Blu", "Filo Viola", "Filo Arcobaleno Dorato"
    };

    static final Map<UUID, UUID> pending = new HashMap<>();
    static final Map<UUID, Integer> pendingTick = new HashMap<>();
    static final Map<String, Integer> cooldowns = new HashMap<>();
    static final Map<UUID, Vec3d> lastPos = new HashMap<>();
    static final Map<UUID, Boolean> wasSneak = new HashMap<>();
    static final Map<UUID, Integer> toggles = new HashMap<>();
    static final Set<CoupleData.Couple> sleepCredited = new HashSet<>();
    static final List<Task> tasks = new ArrayList<>();

    static class Task {
        int delay;
        Runnable run;
        Task(int d, Runnable r) { delay = d; run = r; }
    }

    public static void later(int ticks, Runnable r) { tasks.add(new Task(ticks, r)); }

    // ---------- utilita' ----------
    public static void msg(ServerPlayerEntity p, String text, Formatting f) {
        p.sendMessage(Text.literal(text).formatted(f), false);
    }

    public static void bar(ServerPlayerEntity p, String text, Formatting f) {
        p.sendMessage(Text.literal(text).formatted(f), true);
    }

    public static void title(ServerPlayerEntity p, String title, String sub, Formatting f) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 50, 15));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(sub).formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title).formatted(f)));
    }

    static ParticleEffect dust(int rgb, float size) {
        return new DustParticleEffect(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), size);
    }

    static void sound(ServerWorld w, Entity e, SoundEvent s, float pitch) {
        w.playSound(null, e.getX(), e.getY(), e.getZ(), s, SoundCategory.PLAYERS, 1f, pitch);
    }

    static void hop(ServerPlayerEntity p, double y) {
        p.setVelocity(p.getVelocity().add(0, y, 0));
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    static void ring(ServerWorld w, Vec3d center, ParticleEffect eff, double radius, int n) {
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            w.spawnParticles(eff, center.x + Math.cos(ang) * radius, center.y, center.z + Math.sin(ang) * radius, 1, 0, 0.05, 0, 0.0);
        }
    }

    static boolean ready(MinecraftServer s, CoupleData.Couple c, String type, int ticks) {
        String k = c.a + type;
        Integer last = cooldowns.get(k);
        int now = s.getTicks();
        if (last != null && now - last < ticks) return false;
        cooldowns.put(k, now);
        return true;
    }

    public static ServerPlayerEntity online(MinecraftServer s, UUID id) {
        return s.getPlayerManager().getPlayer(id);
    }

    // ---------- gestione coppie ----------
    public static void createCouple(MinecraftServer s, ServerPlayerEntity p1, ServerPlayerEntity p2) {
        CoupleData d = CoupleData.get(s);
        CoupleData.Couple c = new CoupleData.Couple();
        c.a = p1.getUuid();
        c.b = p2.getUuid();
        c.nameA = p1.getGameProfile().getName();
        c.nameB = p2.getGameProfile().getName();
        d.byPlayer.put(c.a, c);
        d.byPlayer.put(c.b, c);
        d.markDirty();
        for (ServerPlayerEntity p : List.of(p1, p2)) {
            title(p, "\u2764 SIETE UNA COPPIA \u2764", c.nameOf(c.other(p.getUuid())) + " e' il tuo partner", Formatting.LIGHT_PURPLE);
            ServerWorld w = p.getServerWorld();
            w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 50, 0.5, 0.8, 0.5, 0.3);
            w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2, p.getZ(), 15, 0.6, 0.4, 0.6, 0.1);
            sound(w, p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f);
            giveRewards(p, c);
        }
    }

    public static void removeCouple(MinecraftServer s, CoupleData.Couple c) {
        CoupleData d = CoupleData.get(s);
        d.byPlayer.remove(c.a);
        d.byPlayer.remove(c.b);
        d.markDirty();
        sleepCredited.remove(c);
    }

    public static void give(ServerPlayerEntity p, ItemStack st) {
        p.getInventory().offerOrDrop(st);
    }

    public static void giveRewards(ServerPlayerEntity p, CoupleData.Couple c) {
        boolean isA = p.getUuid().equals(c.a);
        int from = isA ? c.rewardedA : c.rewardedB;
        int lvl = c.level();
        if (lvl <= from) return;
        for (int l = from + 1; l <= lvl; l++) {
            give(p, new ItemStack(ModItems.THREADS[l - 1]));
            if (l == 3) give(p, new ItemStack(ModItems.COUPLE_SWORD));
            if (l == 6) give(p, new ItemStack(ModItems.COUPLE_AXE));
        }
        if (isA) c.rewardedA = lvl; else c.rewardedB = lvl;
        CoupleData.get(p.getServer()).markDirty();
        msg(p, "\u2728 Hai ricevuto le ricompense di livello " + lvl + "!", Formatting.GOLD);
    }

    public static void addXp(MinecraftServer s, CoupleData.Couple c, int amount) {
        int old = c.level();
        c.xp = Math.max(0, c.xp + amount);
        CoupleData.get(s).markDirty();
        int nw = c.level();
        if (nw > old) {
            for (UUID id : List.of(c.a, c.b)) {
                ServerPlayerEntity p = online(s, id);
                if (p == null) continue;
                title(p, "\u2B50 LIVELLO " + nw + " \u2B50", THREAD_NAMES[nw - 1], Formatting.GOLD);
                ServerWorld w = p.getServerWorld();
                w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1.0, 0.6, 0.4);
                sound(w, p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f);
                giveRewards(p, c);
            }
        }
    }

    public static void addProgress(MinecraftServer s, CoupleData.Couple c, int m, int amount) {
        c.progress[m] += amount;
        boolean completed = false;
        while (c.progress[m] >= Missions.TARGET[m]) {
            c.progress[m] -= Missions.TARGET[m];
            c.done[m]++;
            completed = true;
            for (UUID id : List.of(c.a, c.b)) {
                ServerPlayerEntity p = online(s, id);
                if (p != null) {
                    msg(p, "\u2714 Missione completata: " + Missions.NAMES[m] + "  (+" + Missions.REWARD[m] + " XP)", Formatting.GREEN);
                    p.getServerWorld().spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 25, 0.5, 0.8, 0.5, 0.1);
                    sound(p.getServerWorld(), p, SoundEvents.ENTITY_PLAYER_LEVELUP, 1f);
                }
            }
            addXp(s, c, Missions.REWARD[m]);
        }
        if (!completed) CoupleData.get(s).markDirty();
    }

    // ---------- interazioni ----------
    public static ActionResult onUseEntity(PlayerEntity pl, World world, Hand hand, Entity entity) {
        if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(pl instanceof ServerPlayerEntity p) || !(entity instanceof ServerPlayerEntity other)) return ActionResult.PASS;
        MinecraftServer s = p.getServer();
        if (s == null) return ActionResult.PASS;
        CoupleData.Couple c = CoupleData.get(s).byPlayer.get(p.getUuid());
        if (c == null || !c.other(p.getUuid()).equals(other.getUuid())) return ActionResult.PASS;

        ItemStack held = p.getMainHandStack();
        ServerWorld w = p.getServerWorld();
        Vec3d mid = p.getPos().add(other.getPos()).multiply(0.5).add(0, 1.0, 0);

        if (!held.isEmpty()) {
            FoodComponent fc = held.getItem().getFoodComponent();
            if (fc == null) return ActionResult.PASS;
            if (!other.getHungerManager().isNotFull()) {
                bar(p, other.getGameProfile().getName() + " non ha fame!", Formatting.YELLOW);
                return ActionResult.SUCCESS;
            }
            other.getHungerManager().add(fc.getHunger(), fc.getSaturationModifier());
            if (!p.getAbilities().creativeMode) held.decrement(1);
            sound(w, other, SoundEvents.ENTITY_GENERIC_EAT, 1f);
            w.spawnParticles(ParticleTypes.HEART, other.getX(), other.getEyeY() + 0.4, other.getZ(), 5, 0.3, 0.2, 0.3, 0.05);
            bar(p, "Hai imboccato " + other.getGameProfile().getName() + " \u2764", Formatting.LIGHT_PURPLE);
            bar(other, p.getGameProfile().getName() + " ti ha imboccato \u2764", Formatting.LIGHT_PURPLE);
            if (ready(s, c, "feed", 60)) {
                addXp(s, c, 3);
                addProgress(s, c, Missions.FEED, 1);
            }
        } else if (p.isSneaking()) {
            ring(w, mid, ParticleTypes.HEART, 0.7, 12);
            w.spawnParticles(ParticleTypes.HEART, mid.x, mid.y + 0.6, mid.z, 10, 0.3, 0.3, 0.3, 0.1);
            sound(w, p, SoundEvents.ENTITY_VILLAGER_YES, 1.6f);
            bar(p, "\uD83D\uDC8B Bacio!", Formatting.RED);
            bar(other, "\uD83D\uDC8B " + p.getGameProfile().getName() + " ti ha dato un bacio", Formatting.RED);
            if (ready(s, c, "kiss", 60)) {
                addXp(s, c, 4);
                addProgress(s, c, Missions.KISS, 1);
            }
        } else {
            ring(w, mid, ParticleTypes.HEART, 0.9, 16);
            ring(w, mid.add(0, 0.4, 0), ParticleTypes.END_ROD, 0.6, 10);
            sound(w, p, SoundEvents.ENTITY_CAT_PURR, 1.2f);
            hop(p, 0.28);
            hop(other, 0.28);
            bar(p, "Abbraccio! \uD83E\uDD17", Formatting.LIGHT_PURPLE);
            bar(other, p.getGameProfile().getName() + " ti abbraccia \uD83E\uDD17", Formatting.LIGHT_PURPLE);
            if (ready(s, c, "hug", 60)) {
                addXp(s, c, 3);
                addProgress(s, c, Missions.HUG, 1);
            }
        }
        return ActionResult.SUCCESS;
    }

    public static void onKill(ServerWorld world, Entity entity, LivingEntity killed) {
        if (!(entity instanceof ServerPlayerEntity p) || !(killed instanceof HostileEntity)) return;
        MinecraftServer s = world.getServer();
        CoupleData.Couple c = CoupleData.get(s).byPlayer.get(p.getUuid());
        if (c == null) return;
        ServerPlayerEntity partner = online(s, c.other(p.getUuid()));
        if (partner == null || partner.getWorld() != p.getWorld() || partner.distanceTo(p) > 20) return;
        addProgress(s, c, Missions.HUNT, 1);
    }

    // ---------- tick ----------
    public static void tick(MinecraftServer s) {
        int t = s.getTicks();
        if (!tasks.isEmpty()) {
            for (Task k : new ArrayList<>(tasks)) {
                k.delay--;
                if (k.delay <= 0) {
                    tasks.remove(k);
                    k.run.run();
                }
            }
        }
        CoupleData d = CoupleData.get(s);
        if (d.byPlayer.isEmpty()) return;

        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (!d.byPlayer.containsKey(p.getUuid())) continue;
            boolean sn = p.isSneaking();
            Boolean was = wasSneak.put(p.getUuid(), sn);
            if (was != null && was != sn) toggles.merge(p.getUuid(), 1, Integer::sum);
        }
        if (t % 4 != 0) return;

        for (CoupleData.Couple c : d.couples()) {
            ServerPlayerEntity a = online(s, c.a);
            ServerPlayerEntity b = online(s, c.b);
            if (a == null || b == null || a.getWorld() != b.getWorld()) continue;
            ServerWorld w = a.getServerWorld();
            int lvl = c.level();
            double dist = a.distanceTo(b);
            double range = 8 + lvl * 4;

            if (dist > range) {
                double over = dist - range;
                double str = Math.min(0.6, 0.1 + over * 0.03);
                pull(a, b, str);
                pull(b, a, str);
            }
            if (dist < 64) {
                drawThread(w, a.getPos().add(0, 1.0, 0), b.getPos().add(0, 1.0, 0), lvl, t);
            }

            if (t % 20 == 0) {
                if (dist <= 8) {
                    for (ServerPlayerEntity p : List.of(a, b)) perks(p, lvl);
                }
                // camminare insieme
                Vec3d prev = lastPos.put(a.getUuid(), a.getPos());
                if (prev != null && dist <= 8) {
                    double dx = a.getX() - prev.x, dz = a.getZ() - prev.z;
                    double hd = Math.sqrt(dx * dx + dz * dz);
                    if (hd > 0.5 && hd < 20) addProgress(s, c, Missions.WALK, (int) Math.round(hd));
                }
                // dormire insieme
                if (a.isSleeping() && b.isSleeping() && dist <= 6) {
                    if (sleepCredited.add(c)) {
                        for (ServerPlayerEntity p : List.of(a, b)) {
                            w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                            bar(p, "Dormite vicini \uD83D\uDCA4\u2764", Formatting.LIGHT_PURPLE);
                        }
                        addXp(s, c, 5);
                        addProgress(s, c, Missions.SLEEP, 1);
                    }
                } else if (!a.isSleeping() && !b.isSleeping()) {
                    sleepCredited.remove(c);
                }
                // ballare insieme
                if (t % 40 == 0) {
                    int ta = toggles.getOrDefault(a.getUuid(), 0);
                    int tb = toggles.getOrDefault(b.getUuid(), 0);
                    if (ta >= 4 && tb >= 4 && dist <= 5) {
                        for (ServerPlayerEntity p : List.of(a, b)) {
                            w.spawnParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.2, p.getZ(), 5, 0.4, 0.2, 0.4, 1.0);
                            bar(p, "\uD83D\uDC83 State ballando insieme!", Formatting.AQUA);
                        }
                        addProgress(s, c, Missions.DANCE, 1);
                    }
                    toggles.put(a.getUuid(), 0);
                    toggles.put(b.getUuid(), 0);
                }
            }
        }
    }

    static void pull(ServerPlayerEntity from, ServerPlayerEntity to, double strength) {
        Vec3d dir = to.getPos().subtract(from.getPos()).normalize().multiply(strength);
        from.setVelocity(from.getVelocity().add(dir.x, 0.08, dir.z));
        from.velocityModified = true;
        from.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(from));
    }

    static void perks(ServerPlayerEntity p, int lvl) {
        if (lvl >= 3) p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false, true));
        if (lvl >= 6) p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false, true));
        if (lvl >= 9) p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, true, false, true));
        if (lvl >= 10) p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 60, 1, true, false, true));
    }

    static void drawThread(ServerWorld w, Vec3d from, Vec3d to, int level, int tick) {
        double dist = from.distanceTo(to);
        int n = (int) Math.min(50, Math.max(2, dist / 0.5));
        double sag = Math.min(0.7, dist * 0.06);
        float size = 0.8f + level * 0.07f;
        int count = 1 + level / 4;
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            Vec3d p = from.lerp(to, f);
            double y = p.y - Math.sin(f * Math.PI) * sag;
            int rgb = level >= 10 ? MathHelper.hsvToRgb(((tick * 4 + i * 9) % 360) / 360f, 1f, 1f) : COLORS[level - 1];
            w.spawnParticles(dust(rgb, size), p.x, y, p.z, count, 0.03, 0.03, 0.03, 0.0);
            if (level >= 7 && i % 6 == 0) w.spawnParticles(ParticleTypes.END_ROD, p.x, y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    // ---------- omicidio con animazione ----------
    public static void murder(MinecraftServer s, ServerPlayerEntity killer, ServerPlayerEntity victim, CoupleData.Couple c) {
        UUID kid = killer.getUuid(), vid = victim.getUuid();
        String kn = killer.getGameProfile().getName(), vn = victim.getGameProfile().getName();
        removeCouple(s, c);
        ServerWorld w = killer.getServerWorld();

        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 6));
        title(victim, "\uD83D\uDC94 TRADITO", "Il filo si spezza...", Formatting.DARK_RED);
        title(killer, "\uD83D\uDD2A IL FILO SI SPEZZA", "Addio, " + vn, Formatting.DARK_RED);
        sound(w, victim, SoundEvents.ENTITY_WITHER_SPAWN, 0.6f);

        Vec3d from = killer.getPos().add(0, 1, 0);
        Vec3d to = victim.getPos().add(0, 1, 0);
        for (int i = 0; i <= 20; i++) {
            Vec3d p = from.lerp(to, i / 20.0);
            w.spawnParticles(dust(0x330000, 2.0f), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        }

        later(30, () -> {
            ServerPlayerEntity v = online(s, vid);
            if (v == null) return;
            ServerWorld vw = v.getServerWorld();
            vw.spawnParticles(ParticleTypes.SOUL, v.getX(), v.getY() + 1, v.getZ(), 50, 0.5, 0.9, 0.5, 0.05);
            ring(vw, v.getPos().add(0, 0.2, 0), ParticleTypes.SMOKE, 1.5, 24);
            sound(vw, v, SoundEvents.ENTITY_ITEM_BREAK, 0.5f);
        });
        later(60, () -> {
            ServerPlayerEntity v = online(s, vid);
            if (v == null) return;
            ServerWorld vw = v.getServerWorld();
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(vw);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(v.getX(), v.getY(), v.getZ());
                bolt.setCosmetic(true);
                vw.spawnEntity(bolt);
            }
        });
        later(70, () -> {
            ServerPlayerEntity v = online(s, vid);
            ServerPlayerEntity k = online(s, kid);
            if (v == null) return;
            ServerWorld vw = v.getServerWorld();
            vw.spawnParticles(ParticleTypes.CRIT, v.getX(), v.getY() + 1, v.getZ(), 60, 0.5, 0.8, 0.5, 0.4);
            vw.spawnParticles(ParticleTypes.SWEEP_ATTACK, v.getX(), v.getY() + 1, v.getZ(), 1, 0, 0, 0, 0);
            v.removeStatusEffect(StatusEffects.SLOWNESS);
            if (k != null) {
                v.damage(vw.getDamageSources().playerAttack(k), 10000f);
                give(k, new ItemStack(ModItems.BETRAYAL_DAGGER));
            } else {
                v.damage(vw.getDamageSources().magic(), 10000f);
            }
            s.getPlayerManager().broadcast(Text.literal("\uD83D\uDC80 " + kn + " ha ucciso il proprio partner " + vn + "!").formatted(Formatting.DARK_RED), false);
        });
    }
}
