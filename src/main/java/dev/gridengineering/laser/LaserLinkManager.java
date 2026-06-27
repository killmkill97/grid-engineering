package dev.gridengineering.laser;

import dev.gridengineering.block.entity.LaserTransformerBlockEntity;
import dev.gridengineering.energy.LineLossCalculator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

public final class LaserLinkManager {
    private static final ResourceLocation TORCHMASTER_INVISIBLE_LIGHT =
            ResourceLocation.fromNamespaceAndPath("torchmaster", "invisible_light");
    private static final Map<ServerLevel, LevelLinks> LEVEL_LINKS = new WeakHashMap<>();

    private LaserLinkManager() {
    }

    public static void register(ServerLevel level, LaserTransformerBlockEntity transformer) {
        LevelLinks links = links(level);
        if (transformer.role() == LaserRole.SENDER) {
            links.knownSenders.add(transformer.getBlockPos().asLong());
            transformer.markLinkDirty();
        } else {
            links.knownReceivers.add(transformer.getBlockPos().asLong());
            invalidateAt(level, transformer.getBlockPos());
        }
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        LevelLinks links = links(level);
        links.knownSenders.remove(pos.asLong());
        links.knownReceivers.remove(pos.asLong());
        removeLink(level, links, pos.asLong());

        Long senderKey = links.receiverToSender.remove(pos.asLong());
        if (senderKey != null) {
            removeLink(level, links, senderKey);
        }
        invalidateAt(level, pos);
    }

    public static void invalidateAt(ServerLevel level, BlockPos changedPos) {
        LevelLinks links = links(level);
        Set<Long> affectedSenders = new HashSet<>();
        Long beamOwner = links.beamCells.get(changedPos.asLong());
        if (beamOwner != null) {
            affectedSenders.add(beamOwner);
        }

        for (long senderKey : Set.copyOf(links.knownSenders)) {
            BlockPos senderPos = BlockPos.of(senderKey);
            BlockEntity blockEntity = getLoadedBlockEntity(level, senderPos);
            if (!(blockEntity instanceof LaserTransformerBlockEntity sender)
                    || sender.role() != LaserRole.SENDER) {
                links.knownSenders.remove(senderKey);
                continue;
            }
            if (senderPos.equals(changedPos)
                    || isOnForwardRay(sender, changedPos, sender.tier().maxDistance())) {
                affectedSenders.add(senderKey);
            }
        }

        for (long senderKey : affectedSenders) {
            removeLink(level, links, senderKey);
            if (getLoadedBlockEntity(level, BlockPos.of(senderKey))
                    instanceof LaserTransformerBlockEntity sender) {
                sender.markLinkDirty();
            }
        }
    }

    public static void invalidateChunk(ServerLevel level, ChunkPos chunkPos) {
        LevelLinks links = links(level);
        Set<Long> affectedSenders = new HashSet<>();
        for (Map.Entry<Long, Link> entry : Set.copyOf(links.senderLinks.entrySet())) {
            if (crossesChunk(entry.getValue(), chunkPos)) {
                affectedSenders.add(entry.getKey());
            }
        }

        for (long senderKey : affectedSenders) {
            removeLink(level, links, senderKey);
            if (getLoadedBlockEntity(level, BlockPos.of(senderKey))
                    instanceof LaserTransformerBlockEntity sender) {
                sender.markLinkDirty();
            }
        }
    }

    public static void invalidateAll(ServerLevel level) {
        LevelLinks links = links(level);
        for (Link link : Set.copyOf(links.senderLinks.values())) {
            clearLinkedPositions(level, link);
        }
        links.senderLinks.clear();
        links.receiverToSender.clear();
        links.beamCells.clear();

        links.knownSenders.removeIf(senderKey -> {
            BlockEntity blockEntity = getLoadedBlockEntity(level, BlockPos.of(senderKey));
            if (blockEntity instanceof LaserTransformerBlockEntity transformer
                    && transformer.role() == LaserRole.SENDER) {
                transformer.markLinkDirty();
                return false;
            }
            return true;
        });
        links.knownReceivers.removeIf(receiverKey -> {
            BlockEntity blockEntity = getLoadedBlockEntity(level, BlockPos.of(receiverKey));
            return !(blockEntity instanceof LaserTransformerBlockEntity transformer)
                    || transformer.role() != LaserRole.RECEIVER;
        });
    }

    public static void clearLevel(ServerLevel level) {
        LEVEL_LINKS.remove(level);
    }

    @Nullable
    public static Link ensureLink(
            ServerLevel level,
            LaserTransformerBlockEntity sender
    ) {
        if (sender.role() != LaserRole.SENDER) {
            return null;
        }

        LevelLinks links = links(level);
        long senderKey = sender.getBlockPos().asLong();
        links.knownSenders.add(senderKey);
        Link existing = links.senderLinks.get(senderKey);
        if (!sender.isLinkDirty()) {
            if (existing == null) {
                return null;
            }
            if (isLinkLoadedAndValid(level, existing)) {
                return existing;
            }
        }

        removeLink(level, links, senderKey);
        Link found = findLink(level, links, sender);
        if (found == null) {
            sender.setLinkedPos(null);
            return null;
        }

        links.senderLinks.put(senderKey, found);
        links.receiverToSender.put(found.receiver().asLong(), senderKey);
        forEachBeamCell(found, pos -> links.beamCells.put(pos.asLong(), senderKey));
        sender.setLinkedPos(found.receiver());
        if (getLoadedBlockEntity(level, found.receiver())
                instanceof LaserTransformerBlockEntity receiver) {
            receiver.setLinkedPos(found.sender());
        }
        return found;
    }

    @Nullable
    public static Link linkFor(
            ServerLevel level,
            LaserTransformerBlockEntity sender
    ) {
        return ensureLink(level, sender);
    }

    public static long transmit(
            ServerLevel level,
            LaserTransformerBlockEntity sender,
            long amount,
            long voltage,
            boolean simulate
    ) {
        Link link = ensureLink(level, sender);
        if (link == null || amount <= 0L || voltage <= 0L) {
            return 0L;
        }
        if (!(getLoadedBlockEntity(level, link.receiver())
                instanceof LaserTransformerBlockEntity receiver)) {
            removeLink(level, links(level), sender.getBlockPos().asLong());
            sender.markLinkDirty();
            return 0L;
        }
        if (voltage > sender.tier().maxVoltage()) {
            if (!simulate) {
                sender.failFromOvervoltage();
            }
            return amount;
        }
        if (voltage > receiver.tier().maxVoltage()) {
            if (!simulate) {
                receiver.failFromOvervoltage();
            }
            return amount;
        }

        long calculatedLoss = LineLossCalculator.calculateLoss(
                amount,
                voltage,
                link.routeLossPerAmpPpm()
        );
        long delivered = amount - calculatedLoss;
        long receiverAccepted = receiver.offerLaserPower(delivered, voltage, simulate);
        if (receiverAccepted < delivered) {
            return 0L;
        }
        return amount;
    }

    private static Link findLink(
            ServerLevel level,
            LevelLinks links,
            LaserTransformerBlockEntity sender
    ) {
        BlockPos senderPos = sender.getBlockPos();
        Direction direction = sender.facing();
        int maximumDistance = maximumSearchDistance(level, senderPos, direction, sender.tier().maxDistance());
        LaserTransformerBlockEntity receiver = null;
        BlockPos receiverPos = null;
        int receiverDistance = Integer.MAX_VALUE;

        for (long receiverKey : Set.copyOf(links.knownReceivers)) {
            BlockPos candidatePos = BlockPos.of(receiverKey);
            BlockEntity blockEntity = getLoadedBlockEntity(level, candidatePos);
            if (!(blockEntity instanceof LaserTransformerBlockEntity candidate)
                    || candidate.role() != LaserRole.RECEIVER) {
                links.knownReceivers.remove(receiverKey);
                continue;
            }

            int distance = forwardDistance(senderPos, candidatePos, direction);
            if (distance <= 0
                    || distance > maximumDistance
                    || distance >= receiverDistance
                    || distance > candidate.tier().maxDistance()
                    || candidate.facing() != direction.getOpposite()
                    || links.receiverToSender.containsKey(receiverKey)) {
                continue;
            }
            receiver = candidate;
            receiverPos = candidatePos;
            receiverDistance = distance;
        }

        if (receiver == null || receiverPos == null) {
            return null;
        }

        for (int distance = 1; distance < receiverDistance; distance++) {
            BlockPos pathPos = senderPos.relative(direction, distance);
            long occupiedBy = links.beamCells.getOrDefault(pathPos.asLong(), Long.MIN_VALUE);
            if (occupiedBy != Long.MIN_VALUE && occupiedBy != senderPos.asLong()) {
                return null;
            }
            if (level.hasChunkAt(pathPos)
                    && !canBeamPassThrough(level.getBlockState(pathPos))) {
                return null;
            }
        }

        long maximumVoltage = Math.min(
                sender.tier().maxVoltage(),
                receiver.tier().maxVoltage()
        );
        long lossPerBlockPerAmpPpm = Math.max(
                sender.tier().lossPerBlockPerAmpPpm(),
                receiver.tier().lossPerBlockPerAmpPpm()
        );
        return new Link(
                senderPos.immutable(),
                receiverPos.immutable(),
                direction,
                receiverDistance,
                maximumVoltage,
                saturatedMultiply(lossPerBlockPerAmpPpm, receiverDistance)
        );
    }

    private static int forwardDistance(
            BlockPos source,
            BlockPos target,
            Direction direction
    ) {
        int deltaX = target.getX() - source.getX();
        int deltaY = target.getY() - source.getY();
        int deltaZ = target.getZ() - source.getZ();
        return switch (direction.getAxis()) {
            case X -> deltaY == 0 && deltaZ == 0
                    ? deltaX * direction.getStepX()
                    : -1;
            case Y -> deltaX == 0 && deltaZ == 0
                    ? deltaY * direction.getStepY()
                    : -1;
            case Z -> deltaX == 0 && deltaY == 0
                    ? deltaZ * direction.getStepZ()
                    : -1;
        };
    }

    private static int maximumSearchDistance(
            ServerLevel level,
            BlockPos senderPos,
            Direction direction,
            int configuredMaximumDistance
    ) {
        if (direction.getAxis() != Direction.Axis.Y) {
            return configuredMaximumDistance;
        }

        int worldHeightDistance = direction == Direction.UP
                ? level.getMaxBuildHeight() - 1 - senderPos.getY()
                : senderPos.getY() - level.getMinBuildHeight();
        return Math.max(0, Math.min(configuredMaximumDistance, worldHeightDistance));
    }

    private static boolean isLinkLoadedAndValid(ServerLevel level, Link link) {
        if (!(getLoadedBlockEntity(level, link.sender())
                instanceof LaserTransformerBlockEntity sender)
                || !(getLoadedBlockEntity(level, link.receiver())
                instanceof LaserTransformerBlockEntity receiver)) {
            return false;
        }
        return sender.role() == LaserRole.SENDER
                && receiver.role() == LaserRole.RECEIVER
                && sender.facing() == link.direction()
                && receiver.facing() == link.direction().getOpposite();
    }

    private static boolean canBeamPassThrough(BlockState state) {
        return state.isAir()
                || BuiltInRegistries.BLOCK.getKey(state.getBlock())
                .equals(TORCHMASTER_INVISIBLE_LIGHT);
    }

    private static void removeLink(ServerLevel level, LevelLinks links, long senderKey) {
        Link removed = links.senderLinks.remove(senderKey);
        if (removed == null) {
            return;
        }
        links.receiverToSender.remove(removed.receiver().asLong());
        forEachBeamCell(removed, pos -> links.beamCells.remove(pos.asLong()));
        clearLinkedPositions(level, removed);
    }

    private static void clearLinkedPositions(ServerLevel level, Link link) {
        if (getLoadedBlockEntity(level, link.sender())
                instanceof LaserTransformerBlockEntity sender) {
            sender.setLinkedPos(null);
            sender.markLinkDirty();
        }
        if (getLoadedBlockEntity(level, link.receiver())
                instanceof LaserTransformerBlockEntity receiver) {
            receiver.setLinkedPos(null);
        }
    }

    private static boolean crossesChunk(Link link, ChunkPos chunkPos) {
        int minimumX = chunkPos.getMinBlockX();
        int maximumX = chunkPos.getMaxBlockX();
        int minimumZ = chunkPos.getMinBlockZ();
        int maximumZ = chunkPos.getMaxBlockZ();
        BlockPos sender = link.sender();
        BlockPos receiver = link.receiver();

        return switch (link.direction().getAxis()) {
            case X -> sender.getZ() >= minimumZ
                    && sender.getZ() <= maximumZ
                    && rangesOverlap(
                            Math.min(sender.getX(), receiver.getX()),
                            Math.max(sender.getX(), receiver.getX()),
                            minimumX,
                            maximumX
                    );
            case Y -> sender.getX() >= minimumX
                    && sender.getX() <= maximumX
                    && sender.getZ() >= minimumZ
                    && sender.getZ() <= maximumZ;
            case Z -> sender.getX() >= minimumX
                    && sender.getX() <= maximumX
                    && rangesOverlap(
                            Math.min(sender.getZ(), receiver.getZ()),
                            Math.max(sender.getZ(), receiver.getZ()),
                            minimumZ,
                            maximumZ
                    );
        };
    }

    private static boolean rangesOverlap(
            int firstMinimum,
            int firstMaximum,
            int secondMinimum,
            int secondMaximum
    ) {
        return firstMaximum >= secondMinimum && secondMaximum >= firstMinimum;
    }

    @Nullable
    private static BlockEntity getLoadedBlockEntity(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    private static void forEachBeamCell(Link link, java.util.function.Consumer<BlockPos> consumer) {
        for (int distance = 1; distance < link.distance(); distance++) {
            consumer.accept(link.sender().relative(link.direction(), distance));
        }
    }

    private static boolean isOnForwardRay(
            LaserTransformerBlockEntity sender,
            BlockPos target,
            int maximumDistance
    ) {
        BlockPos source = sender.getBlockPos();
        int deltaX = target.getX() - source.getX();
        int deltaY = target.getY() - source.getY();
        int deltaZ = target.getZ() - source.getZ();
        Direction direction = sender.facing();
        int distance = switch (direction.getAxis()) {
            case X -> deltaX * direction.getStepX();
            case Y -> deltaY * direction.getStepY();
            case Z -> deltaZ * direction.getStepZ();
        };
        if (distance <= 0 || distance > maximumDistance) {
            return false;
        }
        return switch (direction.getAxis()) {
            case X -> deltaY == 0 && deltaZ == 0;
            case Y -> deltaX == 0 && deltaZ == 0;
            case Z -> deltaX == 0 && deltaY == 0;
        };
    }

    private static LevelLinks links(ServerLevel level) {
        return LEVEL_LINKS.computeIfAbsent(level, ignored -> new LevelLinks());
    }

    private static long saturatedMultiply(long first, int second) {
        if (first <= 0L || second <= 0) {
            return 0L;
        }
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }

    public record Link(
            BlockPos sender,
            BlockPos receiver,
            Direction direction,
            int distance,
            long maximumVoltage,
            long routeLossPerAmpPpm
    ) {
    }

    private static final class LevelLinks {
        private final Set<Long> knownSenders = new HashSet<>();
        private final Set<Long> knownReceivers = new HashSet<>();
        private final Map<Long, Link> senderLinks = new HashMap<>();
        private final Map<Long, Long> receiverToSender = new HashMap<>();
        private final Map<Long, Long> beamCells = new HashMap<>();
    }
}
