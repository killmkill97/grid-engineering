package dev.gridengineering.entanglement;

import dev.gridengineering.block.entity.EntanglementLinkBlockEntity;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks loaded Entanglement Links by Entangled Electron UUID.
 *
 * <p>The manager only tracks loaded block entities. If a paired link is in an
 * unloaded chunk or unloaded dimension, the pair waits safely instead of force
 * loading the world.</p>
 */
public final class EntanglementLinkManager {
    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();

    private EntanglementLinkManager() {
    }

    /**
     * Registers or updates a loaded link in the server-local pairing index.
     */
    public static void refresh(ServerLevel level, EntanglementLinkBlockEntity link) {
        ServerState state = state(level);
        LinkReference reference = LinkReference.of(level, link.getBlockPos());
        UUID previous = state.positionToId.get(reference);
        UUID current = link.entanglementId().orElse(null);

        if (previous != null && !previous.equals(current)) {
            removeReference(state, previous, reference);
        }
        if (current == null) {
            state.positionToId.remove(reference);
            return;
        }

        state.positionToId.put(reference, current);
        state.idToPositions.computeIfAbsent(current, ignored -> new LinkedHashSet<>())
                .add(reference);
    }

    /**
     * Removes a link position from the pairing index.
     */
    public static void unregister(ServerLevel level, BlockPos pos) {
        ServerState state = state(level);
        LinkReference reference = LinkReference.of(level, pos);
        UUID id = state.positionToId.remove(reference);
        if (id != null) {
            removeReference(state, id, reference);
        }
    }

    /**
     * Resolves the other loaded link in the same UUID group.
     */
    public static ResolvedLink resolve(ServerLevel level, EntanglementLinkBlockEntity link) {
        refresh(level, link);
        UUID id = link.entanglementId().orElse(null);
        if (id == null) {
            return ResolvedLink.noPartner(EntanglementLinkStatus.NO_ELECTRON);
        }

        ServerState state = state(level);
        Set<LinkReference> references = state.idToPositions.get(id);
        if (references == null || references.isEmpty()) {
            return ResolvedLink.noPartner(EntanglementLinkStatus.WAITING_FOR_PAIR);
        }

        cleanGroup(level.getServer(), state, id, references);
        if (references.size() < 2) {
            return ResolvedLink.noPartner(EntanglementLinkStatus.WAITING_FOR_PAIR);
        }
        if (references.size() > 2) {
            return ResolvedLink.noPartner(EntanglementLinkStatus.DUPLICATE_ELECTRONS);
        }

        EntanglementLinkBlockEntity other = null;
        LinkReference self = LinkReference.of(level, link.getBlockPos());
        for (LinkReference reference : references) {
            if (!reference.equals(self)) {
                other = resolveReference(level.getServer(), reference);
                break;
            }
        }
        if (other == null) {
            return ResolvedLink.noPartner(EntanglementLinkStatus.WAITING_FOR_PAIR);
        }
        if (other.getMode() == link.getMode()) {
            return new ResolvedLink(EntanglementLinkStatus.ROLE_CONFLICT, other);
        }
        return new ResolvedLink(EntanglementLinkStatus.CONNECTED, other);
    }

    private static ServerState state(ServerLevel level) {
        return STATES.computeIfAbsent(level.getServer(), ignored -> new ServerState());
    }

    private static void cleanGroup(
            MinecraftServer server,
            ServerState state,
            UUID id,
            Set<LinkReference> references
    ) {
        Iterator<LinkReference> iterator = references.iterator();
        while (iterator.hasNext()) {
            LinkReference reference = iterator.next();
            EntanglementLinkBlockEntity link = resolveReference(server, reference);
            if (link == null || link.entanglementId().map(current -> !current.equals(id)).orElse(true)) {
                iterator.remove();
                state.positionToId.remove(reference);
            }
        }
        if (references.isEmpty()) {
            state.idToPositions.remove(id);
        }
    }

    @Nullable
    private static EntanglementLinkBlockEntity resolveReference(
            MinecraftServer server,
            LinkReference reference
    ) {
        ServerLevel level = server.getLevel(reference.dimension());
        if (level == null || !level.isLoaded(reference.pos())) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(reference.pos());
        return blockEntity instanceof EntanglementLinkBlockEntity link ? link : null;
    }

    private static void removeReference(
            ServerState state,
            UUID id,
            LinkReference reference
    ) {
        Set<LinkReference> references = state.idToPositions.get(id);
        if (references != null) {
            references.remove(reference);
            if (references.isEmpty()) {
                state.idToPositions.remove(id);
            }
        }
    }

    /**
     * Result of resolving a link's UUID group.
     */
    public record ResolvedLink(
            EntanglementLinkStatus status,
            @Nullable EntanglementLinkBlockEntity other
    ) {
        public static ResolvedLink noPartner(EntanglementLinkStatus status) {
            return new ResolvedLink(status, null);
        }

        public boolean connected() {
            return this.status == EntanglementLinkStatus.CONNECTED && this.other != null;
        }
    }

    private record LinkReference(ResourceKey<Level> dimension, BlockPos pos) {
        private static LinkReference of(ServerLevel level, BlockPos pos) {
            return new LinkReference(level.dimension(), pos.immutable());
        }
    }

    private static final class ServerState {
        private final Map<LinkReference, UUID> positionToId = new java.util.HashMap<>();
        private final Map<UUID, Set<LinkReference>> idToPositions = new java.util.HashMap<>();
    }
}
