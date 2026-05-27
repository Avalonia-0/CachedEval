package com.alonie.cache.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-tick caching for EntityPredicate.test() results.
 *
 * PROBLEM: In a single tick, the same predicate is evaluated on the same entity
 * multiple times. Each evaluation walks entity NBT comparing conditions.
 *
 * FIX: Cache test() results per (entityId, predicateIdentity, entityAge).
 * entity.age increments once per tick → auto-invalidates stale entries.
 */
@Mixin(EntityPredicate.class)
public abstract class EntityPredicateCacheMixin {

	@Unique
	private static final Map<Long, Boolean> scanner$predicateCache = new HashMap<>();

	@Unique
	private static final int CLEANUP_INTERVAL = 200;

	@Unique
	private static int scanner$tickCounter;

	/**
	 * Build a cache key that includes entity.age to auto-invalidate across ticks.
	 * High 32 bits: entityId | entity.age (top 12 bits of age to avoid full collision)
	 * Low 32 bits: identityHashCode of the predicate instance
	 */
	@Unique
	private static long scanner$key(int entityId, int predicateHash, int entityAge) {
		// Mix entity age into the key to ensure different ticks = different keys
		long mix = ((long) entityId << 32) | (predicateHash & 0xFFFFFFFFL);
		mix ^= (long) entityAge << 1;
		return mix;
	}

	/**
	 * Periodically trim the cache to prevent unbounded growth.
	 * Called once per CLEANUP_INTERVAL ticks.
	 */
	@Unique
	private static void scanner$maybeCleanup() {
		scanner$tickCounter++;
		if (scanner$tickCounter >= CLEANUP_INTERVAL) {
			scanner$tickCounter = 0;
			// Simply clear the entire cache; next evaluations will repopulate.
			// With 4 predicates × 1 entity, this is at most ~800 entries per tick.
			scanner$predicateCache.clear();
		}
	}

	@Inject(method = "method_8909", at = @At("HEAD"), cancellable = true)
	private void scanner$cacheTest(ServerWorld world, Vec3d pos, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity == null) return;
		long key = scanner$key(entity.getId(), System.identityHashCode(this), entity.age);
		Boolean cached = scanner$predicateCache.get(key);
		if (cached != null) {
			cir.setReturnValue(cached);
		}
	}

	@Inject(method = "method_8909", at = @At("RETURN"))
	private void scanner$cacheTestReturn(ServerWorld world, Vec3d pos, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity == null) return;
		long key = scanner$key(entity.getId(), System.identityHashCode(this), entity.age);
		scanner$predicateCache.put(key, cir.getReturnValue());
		scanner$maybeCleanup();
	}

	@Inject(method = "method_8914", at = @At("HEAD"), cancellable = true)
	private void scanner$cacheTestPlayer(ServerPlayerEntity player, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Entity targetEntity = entity != null ? entity : player;
		if (targetEntity == null) return;
		long key = scanner$key(targetEntity.getId(), System.identityHashCode(this), targetEntity.age);
		Boolean cached = scanner$predicateCache.get(key);
		if (cached != null) {
			cir.setReturnValue(cached);
		}
	}

	@Inject(method = "method_8914", at = @At("RETURN"))
	private void scanner$cacheTestPlayerReturn(ServerPlayerEntity player, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Entity targetEntity = entity != null ? entity : player;
		if (targetEntity == null) return;
		long key = scanner$key(targetEntity.getId(), System.identityHashCode(this), targetEntity.age);
		scanner$predicateCache.put(key, cir.getReturnValue());
		scanner$maybeCleanup();
	}
}
