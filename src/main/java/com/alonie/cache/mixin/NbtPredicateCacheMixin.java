package com.alonie.cache.mixin;

import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-tick caching for NbtPredicate.getEntityTagToCompare().
 */
@Mixin(NbtPredicate.class)
public abstract class NbtPredicateCacheMixin {

    @Unique
    private static final Map<Integer, CacheEntry> scanner$nbtCache = new HashMap<>();

    @Inject(method = "getEntityTagToCompare", at = @At("HEAD"), cancellable = true)
    private static void scanner$cacheGetEntityTag(Entity entity, CallbackInfoReturnable<CompoundTag> cir) {
        int entityId = entity.getId();
        int currentTick = entity.tickCount;

        CacheEntry entry = scanner$nbtCache.get(entityId);
        if (entry != null && entry.tick == currentTick) {
            cir.setReturnValue(entry.nbt.copy());
        }
    }

    @Inject(method = "getEntityTagToCompare", at = @At("RETURN"))
    private static void scanner$cacheGetEntityTagReturn(Entity entity, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag nbt = cir.getReturnValue();
        if (nbt != null) {
            scanner$nbtCache.put(entity.getId(), new CacheEntry(entity.tickCount, nbt.copy()));
        }
    }

    @Unique
    private record CacheEntry(int tick, CompoundTag nbt) {}
}
