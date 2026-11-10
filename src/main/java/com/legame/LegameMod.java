package com.legame;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class LegameMod implements ModInitializer {
    public static final String MOD_ID = "legame";

    @Override
    public void onInitialize() {
        ModItems.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CoupleCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(CoupleManager::tick);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> CoupleManager.onUseEntity(player, world, hand, entity));
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(CoupleManager::onKill);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CoupleData.Couple c = CoupleData.get(server).byPlayer.get(handler.player.getUuid());
            if (c != null) CoupleManager.giveRewards(handler.player, c);
        });
    }
}
