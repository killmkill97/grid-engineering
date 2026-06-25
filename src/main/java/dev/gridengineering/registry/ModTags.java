package dev.gridengineering.registry;

import dev.gridengineering.GridEngineering;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Block> WIRES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(GridEngineering.MOD_ID, "wires")
    );

    private ModTags() {
    }
}
