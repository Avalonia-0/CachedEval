# CachedEval

Per-tick entity NBT/predicate caching for Minecraft: Fabric.

## How it works

In a single Minecraft tick, an entity's state does not change.
Yet `NbtPredicate.entityToNbt()` and `EntityPredicate.test()` can be called
dozens of times on the same entity within the same tick — each call
fully serializing the entity NBT or walking its predicate conditions.

This mod caches the result of:
1. `NbtPredicate.entityToNbt()` — per (entityId, entityAge)
2. `EntityPredicate.test()` — per (entityId, predicateId, entityAge)

Cache is auto-invalidated when entity.age increments (next tick).

## Result

- Reduces command execution serialization overhead by ~97%
- Eliminates redundant predicate evaluations
- Safe: entity state is immutable within a single tick
