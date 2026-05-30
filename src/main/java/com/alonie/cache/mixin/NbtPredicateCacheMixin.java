package com.alonie.cache.mixin;

import com.google.common.collect.ImmutableMap;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.predicate.NbtPredicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-tick caching for NbtPredicate.entityToNbt().
 *
 * PROBLEM: Within a single tick, an entity's NBT does not change,
 * but predicates may call entityToNbt() 16+ times on the same entity.
 * Each call fully serializes the entity NBT at huge cost.
 *
 * FIX:
 * - Cache entityToNbt() output per (entityId, entityAge).
 *   Entity age increments once per tick, so within the same tick,
 *   repeated calls return the cached NBT immediately.
 * - LRU eviction via LinkedHashMap when cache exceeds SCANNER$MAX_CACHE_SIZE,
 *   preventing unbounded memory growth from short-lived entities.
 */
@Mixin(NbtPredicate.class)
public abstract class NbtPredicateCacheMixin {

	/**
	 * Maximum cache size. Once exceeded, the least-recently-accessed entry is evicted.
	 * 2048 entries covers a large server's active entities without wasting memory.
	 */
	@Unique
	private static final int SCANNER$MAX_CACHE_SIZE = 2048;

	@Unique
	private static final Map<Integer, CacheEntry> scanner$nbtCache = new LinkedHashMap<>(
			16, 0.75f, true /* accessOrder = true → LRU behavior */
	) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, CacheEntry> eldest) {
			return size() > SCANNER$MAX_CACHE_SIZE;
		}
	};

	@Inject(method = "entityToNbt", at = @At("HEAD"), cancellable = true)
	private static void scanner$cacheEntityToNbt(Entity entity, CallbackInfoReturnable<NbtCompound> cir) {
		int entityId = entity.getId();
		long ageTick = entity.age;

		CacheEntry entry = scanner$nbtCache.get(entityId);
		if (entry != null && entry.tick == ageTick) {
			cir.setReturnValue(entry.nbt.copy());
		}
		// else: let original method run, we cache on return
	}

	@Inject(method = "entityToNbt", at = @At("RETURN"))
	private static void scanner$cacheEntityToNbtReturn(Entity entity, CallbackInfoReturnable<NbtCompound> cir) {
		NbtCompound nbt = cir.getReturnValue();
		if (nbt != null) {
			scanner$nbtCache.put(entity.getId(), new CacheEntry(entity.age, nbt.copy()));
		}
	}

	@Unique
	private record CacheEntry(long tick, NbtCompound nbt) {}
}
