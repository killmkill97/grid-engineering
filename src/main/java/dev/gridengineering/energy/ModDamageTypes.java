package dev.gridengineering.energy;

import dev.gridengineering.GridEngineering;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> ELECTROCUTION = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GridEngineering.MOD_ID, "electrocution")
    );

    private ModDamageTypes() {
    }
}
