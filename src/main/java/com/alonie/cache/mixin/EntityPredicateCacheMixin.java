package com.alonie.cache.mixin;

import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-tick caching for EntityPredicate.matches() results.
 */
@Mixin(EntityPredicate.class)
public abstract class EntityPredicateCacheMixin {

    @Unique
    private static final Map<Long, Boolean> scanner$predicateCache = new HashMap<>();

    @Unique
    private static final int CLEANUP_INTERVAL = 200;

    @Unique
    private static int scanner$tickCounter;

    @Unique
    private static long scanner$key(int entityId, int predicateHash, int tickCount) {
        long mix = ((long) entityId << 32) | (predicateHash & 0xFFFFFFFFL);
        mix ^= (long) tickCount << 1;
        return mix;
    }

    @Unique
    private static void scanner$maybeCleanup() {
        scanner$tickCounter++;
        if (scanner$tickCounter >= CLEANUP_INTERVAL) {
            scanner$tickCounter = 0;
            scanner$predicateCache.clear();
        }
    }

    @Inject(
            method = "matches(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void scanner$cacheTest(ServerLevel world, Vec3 pos, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;
        long key = scanner$key(entity.getId(), System.identityHashCode(this), entity.tickCount);
        Boolean cached = scanner$predicateCache.get(key);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
            method = "matches(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN")
    )
    private void scanner$cacheTestReturn(ServerLevel world, Vec3 pos, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;
        long key = scanner$key(entity.getId(), System.identityHashCode(this), entity.tickCount);
        scanner$predicateCache.put(key, cir.getReturnValue());
        scanner$maybeCleanup();
    }

    @Inject(
            method = "matches(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void scanner$cacheTestPlayer(ServerPlayer player, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Entity targetEntity = entity != null ? entity : player;
        if (targetEntity == null) return;
        long key = scanner$key(targetEntity.getId(), System.identityHashCode(this), targetEntity.tickCount);
        Boolean cached = scanner$predicateCache.get(key);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
            method = "matches(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN")
    )
    private void scanner$cacheTestPlayerReturn(ServerPlayer player, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Entity targetEntity = entity != null ? entity : player;
        if (targetEntity == null) return;
        long key = scanner$key(targetEntity.getId(), System.identityHashCode(this), targetEntity.tickCount);
        scanner$predicateCache.put(key, cir.getReturnValue());
        scanner$maybeCleanup();
    }
}
