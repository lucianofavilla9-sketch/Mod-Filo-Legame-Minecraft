package com.legame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.*;

public class CoupleData extends PersistentState {
    public static final int[] THRESH = {0, 100, 250, 450, 700, 1000, 1400, 1900, 2500, 3200};

    public static class Couple {
        public UUID a, b;
        public String nameA = "?", nameB = "?";
        public int xp = 0;
        public int rewardedA = 0, rewardedB = 0;
        public int[] progress = new int[Missions.COUNT];
        public int[] done = new int[Missions.COUNT];

        public UUID other(UUID id) { return id.equals(a) ? b : a; }
        public String nameOf(UUID id) { return id.equals(a) ? nameA : nameB; }

        public int level() {
            int l = 1;
            for (int i = 0; i < THRESH.length; i++) if (xp >= THRESH[i]) l = i + 1;
            return l;
        }
    }

    public final Map<UUID, Couple> byPlayer = new HashMap<>();

    public List<Couple> couples() {
        return new ArrayList<>(new LinkedHashSet<>(byPlayer.values()));
    }

    public static CoupleData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
            .getOrCreate(CoupleData::fromNbt, CoupleData::new, "legame_couples");
    }

    public static CoupleData fromNbt(NbtCompound nbt) {
        CoupleData d = new CoupleData();
        NbtList list = nbt.getList("couples", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound n = list.getCompound(i);
            Couple c = new Couple();
            c.a = n.getUuid("a");
            c.b = n.getUuid("b");
            c.nameA = n.getString("na");
            c.nameB = n.getString("nb");
            c.xp = n.getInt("xp");
            c.rewardedA = n.getInt("ra");
            c.rewardedB = n.getInt("rb");
            int[] p = n.getIntArray("progress");
            int[] dn = n.getIntArray("done");
            for (int k = 0; k < Missions.COUNT; k++) {
                if (k < p.length) c.progress[k] = p[k];
                if (k < dn.length) c.done[k] = dn[k];
            }
            d.byPlayer.put(c.a, c);
            d.byPlayer.put(c.b, c);
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Couple c : couples()) {
            NbtCompound n = new NbtCompound();
            n.putUuid("a", c.a);
            n.putUuid("b", c.b);
            n.putString("na", c.nameA);
            n.putString("nb", c.nameB);
            n.putInt("xp", c.xp);
            n.putInt("ra", c.rewardedA);
            n.putInt("rb", c.rewardedB);
            n.putIntArray("progress", c.progress);
            n.putIntArray("done", c.done);
            list.add(n);
        }
        nbt.put("couples", list);
        return nbt;
    }
}
