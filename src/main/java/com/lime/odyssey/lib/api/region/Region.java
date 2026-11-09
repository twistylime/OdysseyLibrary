package com.lime.odyssey.lib.api.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Axis-aligned box in one dimension (inclusive block bounds). */
public record Region(ResourceLocation dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static Region of(ResourceKey<Level> dim, BlockPos a, BlockPos b) {
        return new Region(dim.location(), Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /** A sphere-like area approximated as a cube of {@code radius} around {@code center}. */
    public static Region around(ResourceKey<Level> dim, BlockPos center, int radius) {
        return of(dim, center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius));
    }

    public boolean contains(ResourceKey<Level> dim, double x, double y, double z) {
        return dimension.equals(dim.location()) && x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    public boolean contains(ResourceKey<Level> dim, Vec3 pos) {
        return contains(dim, pos.x, pos.y, pos.z);
    }
}
