package dev.gridengineering.energy;

import dev.gridengineering.block.WireBlock;
import dev.gridengineering.block.WirePortMode;
import dev.gridengineering.block.entity.GridControllerBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class WireEnergyTransfer {
    public static final long MICRO_AMPS_PER_AMP = 1_000_000L;
    private static final long DEFAULT_FE_VOLTAGE = 65_536L;
    private static final int MAX_NETWORK_WIRES = 4_096;
    private static final Map<ServerLevel, LevelRuntime> LEVEL_RUNTIMES = new WeakHashMap<>();

    private WireEnergyTransfer() {
    }

    public static void tickFromWire(ServerLevel level, BlockPos wirePos, BlockState wireState) {
        for (Direction direction : Direction.values()) {
            if (!WireBlock.isConnected(wireState, direction)
                    || WireBlock.getPortMode(level, wirePos, direction) == WirePortMode.INPUT) {
                continue;
            }

            BlockPos endpointPos = wirePos.relative(direction);
            if (level.getBlockState(endpointPos).getBlock() instanceof WireBlock) {
                continue;
            }

            Direction sourceSide = direction.getOpposite();
            PowerEndpoint source = PowerEndpointAccess.find(level, endpointPos, sourceSide);
            BlockEntity sourceBlockEntity = level.getBlockEntity(endpointPos);
            boolean canTransferFromMultipleFaces = sourceBlockEntity instanceof GridControllerBlockEntity;
            if (source == null
                    || !source.canExtract()
                    || (!canTransferFromMultipleFaces && alreadyTransferredThisTick(level, endpointPos))) {
                continue;
            }

            SourceProfile profile = sourceProfile(level, endpointPos, sourceSide, source);
            if (profile.availablePower() > 0L) {
                transferFromSource(level, wirePos, endpointPos, source, profile);
            }
        }
    }

    public static NetworkSnapshot inspectNetwork(ServerLevel level, BlockPos wirePos) {
        NetworkScan network = scanNetwork(level, wirePos, null, false);
        NetworkTickState tickState = runtime(level).networkStates.get(network.networkId());
        long gameTime = level.getGameTime();
        long inspectedWireMaxAmps = inspectedWireMaxAmps(level, wirePos);

        if (tickState == null || tickState.tick < gameTime - 1L || tickState.tick > gameTime) {
            return new NetworkSnapshot(
                    network.networkId(),
                    network.wireCount(),
                    0L,
                    network.voltageTierName(),
                    network.maxVoltage(),
                    0L,
                    network.maxAmps(),
                    0L,
                    inspectedWireMaxAmps,
                    List.copyOf(network.gridControllers()),
                    0L,
                    0L,
                    0L,
                    List.of()
            );
        }

        return new NetworkSnapshot(
                network.networkId(),
                network.wireCount(),
                tickState.activeVoltage,
                network.voltageTierName(),
                network.maxVoltage(),
                tickState.usedMicroAmps,
                network.maxAmps(),
                tickState.wireLoad(wirePos),
                inspectedWireMaxAmps,
                List.copyOf(network.gridControllers()),
                tickState.inputPower,
                tickState.outputPower,
                tickState.lossPower,
                List.copyOf(tickState.transfers)
        );
    }

    private static long inspectedWireMaxAmps(ServerLevel level, BlockPos wirePos) {
        return level.getBlockState(wirePos).getBlock() instanceof WireBlock wireBlock
                ? wireBlock.maxAmps()
                : 0L;
    }

    public static boolean isPowerFlowing(ServerLevel level, BlockPos wirePos) {
        NetworkScan network = scanNetwork(level, wirePos, null, false);
        NetworkTickState tickState = runtime(level).networkStates.get(network.networkId());
        if (tickState == null) {
            return false;
        }

        long gameTime = level.getGameTime();
        return tickState.tick >= gameTime - 1L
                && tickState.tick <= gameTime
                && tickState.inputPower > 0L;
    }

    private static void transferFromSource(
            ServerLevel level,
            BlockPos startWirePos,
            BlockPos sourcePos,
            PowerEndpoint source,
            SourceProfile profile
    ) {
        NetworkScan network = scanNetwork(level, startWirePos, sourcePos, true);
        if (network.sinks().isEmpty()) {
            return;
        }

        long potentialTransfer = simulateAccepted(
                network.sinks(),
                profile.availablePower(),
                profile.voltage()
        );
        if (potentialTransfer <= 0L) {
            return;
        }

        NetworkTickState tickState = networkTickState(level, network.networkId());

        long remaining = profile.availablePower();
        long[] capacities = new long[network.sinks().size()];
        long[] existingLoads = new long[network.sinks().size()];
        for (int index = 0; index < network.sinks().size(); index++) {
            EnergyEndpoint sink = network.sinks().get(index);
            capacities[index] = sink.storage().receive(remaining, profile.voltage(), true);
            existingLoads[index] = tickState.destinationLoad(sink.pos());
        }
        long[] allocations = PowerOfTwoChoices.distribute(
                remaining,
                capacities,
                existingLoads,
                level.random
        );

        boolean routeBasedLoads = network.hasGridController();
        if (routeBasedLoads) {
            Long2LongOpenHashMap plannedWireLoads = new Long2LongOpenHashMap();
            plannedWireLoads.defaultReturnValue(0L);
            for (int index = 0; index < allocations.length; index++) {
                EnergyEndpoint sink = network.sinks().get(index);
                long planned = Math.min(remaining, allocations[index]);
                if (planned <= 0L) {
                    continue;
                }

                long plannedMicroAmps = toMicroAmps(planned, profile.voltage());
                BlockPos overloadedWire = firstOverloadedWire(
                        network,
                        tickState,
                        plannedWireLoads,
                        sink,
                        profile.voltage(),
                        plannedMicroAmps
                );
                if (overloadedWire != null) {
                    igniteWire(level, overloadedWire, tickState);
                    return;
                }
                for (BlockPos routeWire : sink.routeWires()) {
                    plannedWireLoads.addTo(routeWire.asLong(), plannedMicroAmps);
                }
            }
        } else {
            if (profile.voltage() > network.maxVoltage()) {
                igniteOverloadedWire(level, network, tickState);
                return;
            }

            long plannedMicroAmps = 0L;
            long networkMaxMicroAmps = maxMicroAmps(network.maxAmps());
            for (long allocation : allocations) {
                long planned = Math.min(remaining, allocation);
                if (planned <= 0L) {
                    continue;
                }

                long addition = toMicroAmps(planned, profile.voltage());
                if (wouldExceed(tickState.usedMicroAmps, saturatedAdd(plannedMicroAmps, addition), networkMaxMicroAmps)) {
                    igniteOverloadedWire(level, network, tickState);
                    return;
                }
                plannedMicroAmps = saturatedAdd(plannedMicroAmps, addition);
            }
        }

        for (int index = 0; index < allocations.length && remaining > 0L; index++) {
            EnergyEndpoint sink = network.sinks().get(index);
            long planned = Math.min(remaining, allocations[index]);
            if (planned <= 0L) {
                continue;
            }

            long extracted = source.extract(planned, false);
            if (extracted <= 0L) {
                break;
            }

            long calculatedLoss = LineLossCalculator.calculateLoss(
                    extracted,
                    profile.voltage(),
                    sink.routeLossPerAmpPpm()
            );
            long deliverable = extracted - calculatedLoss;
            long inserted = deliverable > 0L
                    ? sink.storage().receive(deliverable, profile.voltage(), false)
                    : 0L;
            long returned = 0L;
            if (inserted < deliverable && source.canReceive()) {
                returned = source.receive(deliverable - inserted, false);
            }
            long consumedInput = extracted - returned;
            if (consumedInput <= 0L) {
                continue;
            }

            long actualLoss = consumedInput - inserted;
            long usedMicroAmps = toMicroAmps(consumedInput, profile.voltage());
            tickState.recordTransfer(
                    sourcePos,
                    sink.pos(),
                    profile.voltage(),
                    consumedInput,
                    inserted,
                    actualLoss,
                    usedMicroAmps,
                    sink.wireDistance(),
                    routeBasedLoads ? sink.routeWires() : network.wires()
            );
            remaining -= extracted;
        }
    }

    private static long simulateAccepted(
            List<EnergyEndpoint> sinks,
            long offeredPower,
            long voltage
    ) {
        long remaining = offeredPower;
        long acceptedTotal = 0L;

        for (EnergyEndpoint sink : sinks) {
            if (remaining <= 0L) {
                break;
            }
            long accepted = sink.storage().receive(remaining, voltage, true);
            if (accepted > 0L) {
                acceptedTotal = Math.addExact(acceptedTotal, accepted);
                remaining -= accepted;
            }
        }
        return acceptedTotal;
    }

    private static void igniteOverloadedWire(
            ServerLevel level,
            NetworkScan network,
            NetworkTickState tickState
    ) {
        if (tickState.burned || network.wires().isEmpty()) {
            return;
        }
        BlockPos burnedWire = network.wires().get(level.random.nextInt(network.wires().size()));
        igniteWire(level, burnedWire, tickState);
    }

    private static void igniteWire(
            ServerLevel level,
            BlockPos burnedWire,
            NetworkTickState tickState
    ) {
        if (tickState.burned) {
            return;
        }
        tickState.burned = true;
        level.setBlockAndUpdate(burnedWire, BaseFireBlock.getState(level, burnedWire));
        level.playSound(
                null,
                burnedWire,
                SoundEvents.FIRECHARGE_USE,
                SoundSource.BLOCKS,
                1.0F,
                0.7F + level.random.nextFloat() * 0.25F
        );
    }

    @Nullable
    private static BlockPos firstOverloadedWire(
            NetworkScan network,
            NetworkTickState tickState,
            Long2LongOpenHashMap plannedWireLoads,
            EnergyEndpoint sink,
            long voltage,
            long microAmps
    ) {
        for (BlockPos routeWire : sink.routeWires()) {
            long wireKey = routeWire.asLong();
            if (voltage > network.wireMaxVoltages().get(wireKey)) {
                return routeWire;
            }

            long existingLoad = tickState.wireLoad(routeWire);
            long plannedLoad = plannedWireLoads.get(wireKey);
            long currentLoad = saturatedAdd(existingLoad, plannedLoad);
            long maximumLoad = network.wireMaxMicroAmps().get(wireKey);
            if (wouldExceed(currentLoad, microAmps, maximumLoad)) {
                return routeWire;
            }
        }
        return null;
    }

    private static SourceProfile sourceProfile(
            ServerLevel level,
            BlockPos sourcePos,
            Direction sourceSide,
            PowerEndpoint source
    ) {
        BlockEntity blockEntity = level.getBlockEntity(sourcePos);
        if (blockEntity instanceof ConfiguredPowerSource configured
                && configured.canOutputToGrid(sourceSide)) {
            return new SourceProfile(
                    Math.max(1L, configured.getOutputVoltage()),
                    Math.max(0L, configured.getAvailableOutputPower())
            );
        }

        return new SourceProfile(
                DEFAULT_FE_VOLTAGE,
                Math.max(0L, source.extract(Long.MAX_VALUE, true))
        );
    }

    private static NetworkScan scanNetwork(
            ServerLevel level,
            BlockPos startWirePos,
            @Nullable BlockPos sourcePos,
            boolean collectSinks
    ) {
        PriorityQueue<WireVisit> queue = new PriorityQueue<>(
                Comparator.comparingLong(WireVisit::pathLossPerAmpPpm)
                        .thenComparingInt(WireVisit::wireDistance)
                        .thenComparingLong(visit -> visit.pos().asLong())
        );
        Map<Long, PathCost> bestPaths = new HashMap<>();
        Map<Long, Long> previousWires = new HashMap<>();
        LongOpenHashSet visitedWires = new LongOpenHashSet();
        LongOpenHashSet visitedEndpoints = new LongOpenHashSet();
        LongOpenHashSet visitedGridControllers = new LongOpenHashSet();
        List<BlockPos> wires = new ArrayList<>();
        List<EnergyEndpoint> sinks = new ArrayList<>();
        List<BlockPos> gridControllers = new ArrayList<>();
        Long2LongOpenHashMap wireMaxMicroAmps = new Long2LongOpenHashMap();
        Long2LongOpenHashMap wireMaxVoltages = new Long2LongOpenHashMap();
        wireMaxMicroAmps.defaultReturnValue(0L);
        wireMaxVoltages.defaultReturnValue(0L);
        long networkId = startWirePos.asLong();
        long maxVoltage = Long.MAX_VALUE;
        long maxAmps = Long.MAX_VALUE;
        String voltageTierName = "";

        BlockState startState = level.getBlockState(startWirePos);
        if (!(startState.getBlock() instanceof WireBlock startWire)) {
            return new NetworkScan(
                    networkId,
                    wires,
                    sinks,
                    gridControllers,
                    wireMaxMicroAmps,
                    wireMaxVoltages,
                    "LV",
                    DEFAULT_FE_VOLTAGE,
                    1L
            );
        }

        long startingLoss = startWire.lossPerMeterPerAmpPpm();
        queue.add(new WireVisit(startWirePos.immutable(), startingLoss, 1));
        bestPaths.put(startWirePos.asLong(), new PathCost(startingLoss, 1));

        while (!queue.isEmpty() && visitedWires.size() < MAX_NETWORK_WIRES) {
            WireVisit visit = queue.remove();
            BlockPos wirePos = visit.pos();
            long wireKey = wirePos.asLong();
            PathCost bestPath = bestPaths.get(wireKey);
            if (bestPath == null
                    || bestPath.pathLossPerAmpPpm() != visit.pathLossPerAmpPpm()
                    || bestPath.wireDistance() != visit.wireDistance()) {
                continue;
            }
            if (!visitedWires.add(wireKey)) {
                continue;
            }

            BlockState wireState = level.getBlockState(wirePos);
            if (!(wireState.getBlock() instanceof WireBlock wireBlock)) {
                continue;
            }

            wires.add(wirePos.immutable());
            if (Long.compareUnsigned(wireKey, networkId) < 0) {
                networkId = wireKey;
            }
            if (wireBlock.ratedVoltage() < maxVoltage) {
                maxVoltage = wireBlock.ratedVoltage();
                voltageTierName = wireBlock.voltageTierName();
            }
            maxAmps = Math.min(maxAmps, wireBlock.maxAmps());
            wireMaxMicroAmps.put(wireKey, maxMicroAmps(wireBlock.maxAmps()));
            wireMaxVoltages.put(wireKey, wireBlock.ratedVoltage());

            for (Direction direction : Direction.values()) {
                if (!WireBlock.isConnected(wireState, direction)) {
                    continue;
                }

                BlockPos neighborPos = wirePos.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getBlock() instanceof WireBlock neighborWire) {
                    if (WireBlock.isConnected(neighborState, direction.getOpposite())) {
                        long candidateLoss = saturatedAdd(
                                visit.pathLossPerAmpPpm(),
                                neighborWire.lossPerMeterPerAmpPpm()
                        );
                        int candidateDistance = visit.wireDistance() == Integer.MAX_VALUE
                                ? Integer.MAX_VALUE
                                : visit.wireDistance() + 1;
                        PathCost candidate = new PathCost(candidateLoss, candidateDistance);
                        long neighborKey = neighborPos.asLong();
                        PathCost previous = bestPaths.get(neighborKey);
                        if (previous == null || candidate.isBetterThan(previous)) {
                            bestPaths.put(neighborKey, candidate);
                            previousWires.put(neighborKey, wireKey);
                            queue.add(
                                    new WireVisit(
                                            neighborPos.immutable(),
                                            candidateLoss,
                                            candidateDistance
                                    )
                            );
                        }
                    }
                    continue;
                }

                long endpointKey = neighborPos.asLong();
                if (level.getBlockEntity(neighborPos) instanceof GridControllerBlockEntity
                        && visitedGridControllers.add(endpointKey)) {
                    gridControllers.add(neighborPos.immutable());
                }

                if (!collectSinks) {
                    continue;
                }

                if (neighborPos.equals(sourcePos)
                        || WireBlock.getPortMode(level, wirePos, direction) == WirePortMode.OUTPUT
                        || !visitedEndpoints.add(endpointKey)) {
                    continue;
                }

                PowerEndpoint sink = PowerEndpointAccess.find(
                        level,
                        neighborPos,
                        direction.getOpposite()
                );
                if (sink != null && sink.canReceive()) {
                    sinks.add(
                            new EnergyEndpoint(
                                    neighborPos.immutable(),
                                    sink,
                                    visit.pathLossPerAmpPpm(),
                                    visit.wireDistance(),
                                    routeTo(previousWires, visit.pos())
                            )
                    );
                }
            }
        }

        if (maxVoltage == Long.MAX_VALUE) {
            maxVoltage = DEFAULT_FE_VOLTAGE;
            voltageTierName = "LV";
        }
        if (maxAmps == Long.MAX_VALUE) {
            maxAmps = 1L;
        }

        return new NetworkScan(
                networkId,
                wires,
                sinks,
                gridControllers,
                wireMaxMicroAmps,
                wireMaxVoltages,
                voltageTierName,
                maxVoltage,
                maxAmps
        );
    }

    private static List<BlockPos> routeTo(Map<Long, Long> previousWires, BlockPos endWirePos) {
        List<BlockPos> route = new ArrayList<>();
        long wireKey = endWirePos.asLong();
        int guard = 0;
        while (guard++ < MAX_NETWORK_WIRES) {
            route.add(BlockPos.of(wireKey));
            Long previous = previousWires.get(wireKey);
            if (previous == null) {
                break;
            }
            wireKey = previous;
        }
        return route;
    }

    public static long toMicroAmps(long power, long voltage) {
        if (power <= 0L || voltage <= 0L) {
            return 0L;
        }

        BigInteger numerator = BigInteger.valueOf(power)
                .multiply(BigInteger.valueOf(MICRO_AMPS_PER_AMP));
        BigInteger denominator = BigInteger.valueOf(voltage);
        BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(denominator);
        BigInteger result = quotientAndRemainder[0];
        if (quotientAndRemainder[1].signum() != 0) {
            result = result.add(BigInteger.ONE);
        }
        return result.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    private static boolean wouldExceed(long current, long addition, long maximum) {
        return addition > maximum || current > maximum - addition;
    }

    private static long maxMicroAmps(long amps) {
        if (amps <= 0L) {
            return 0L;
        }
        return amps > Long.MAX_VALUE / MICRO_AMPS_PER_AMP
                ? Long.MAX_VALUE
                : amps * MICRO_AMPS_PER_AMP;
    }

    private static long saturatedAdd(long first, long second) {
        if (first < 0L || second < 0L || first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static boolean alreadyTransferredThisTick(ServerLevel level, BlockPos sourcePos) {
        LevelRuntime runtime = runtime(level);
        long sourceKey = sourcePos.asLong();
        long gameTime = level.getGameTime();
        if (runtime.sourceTransferTicks.get(sourceKey) == gameTime) {
            return true;
        }

        runtime.sourceTransferTicks.put(sourceKey, gameTime);
        return false;
    }

    private static NetworkTickState networkTickState(ServerLevel level, long networkId) {
        LevelRuntime runtime = runtime(level);
        NetworkTickState state = runtime.networkStates.computeIfAbsent(networkId, ignored -> new NetworkTickState());
        long gameTime = level.getGameTime();
        if (state.tick != gameTime) {
            state.reset(gameTime);
        }
        return state;
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return LEVEL_RUNTIMES.computeIfAbsent(level, ignored -> new LevelRuntime());
    }

    public record NetworkSnapshot(
            long networkId,
            int wireCount,
            long activeVoltage,
            String voltageTierName,
            long maxVoltage,
            long currentMicroAmps,
            long maxAmps,
            long inspectedWireMicroAmps,
            long inspectedWireMaxAmps,
            List<BlockPos> gridControllers,
            long inputPower,
            long outputPower,
            long lossPower,
            List<TransferRecord> transfers
    ) {
    }

    public record TransferRecord(
            BlockPos source,
            BlockPos destination,
            long voltage,
            long inputPower,
            long outputPower,
            long lossPower,
            int wireDistance
    ) {
    }

    private record SourceProfile(long voltage, long availablePower) {
    }

    private record NetworkScan(
            long networkId,
            List<BlockPos> wires,
            List<EnergyEndpoint> sinks,
            List<BlockPos> gridControllers,
            Long2LongOpenHashMap wireMaxMicroAmps,
            Long2LongOpenHashMap wireMaxVoltages,
            String voltageTierName,
            long maxVoltage,
            long maxAmps
    ) {
        private int wireCount() {
            return this.wires.size();
        }

        private boolean hasGridController() {
            return !this.gridControllers.isEmpty();
        }
    }

    private record EnergyEndpoint(
            BlockPos pos,
            PowerEndpoint storage,
            long routeLossPerAmpPpm,
            int wireDistance,
            List<BlockPos> routeWires
    ) {
    }

    private record WireVisit(BlockPos pos, long pathLossPerAmpPpm, int wireDistance) {
    }

    private record PathCost(long pathLossPerAmpPpm, int wireDistance) {
        private boolean isBetterThan(PathCost other) {
            return this.pathLossPerAmpPpm < other.pathLossPerAmpPpm
                    || this.pathLossPerAmpPpm == other.pathLossPerAmpPpm
                    && this.wireDistance < other.wireDistance;
        }
    }

    private static final class LevelRuntime {
        private final Long2LongOpenHashMap sourceTransferTicks = new Long2LongOpenHashMap();
        private final Map<Long, NetworkTickState> networkStates = new HashMap<>();

        private LevelRuntime() {
            this.sourceTransferTicks.defaultReturnValue(Long.MIN_VALUE);
        }
    }

    private static final class NetworkTickState {
        private long tick = Long.MIN_VALUE;
        private long activeVoltage;
        private long usedMicroAmps;
        private long inputPower;
        private long outputPower;
        private long lossPower;
        private boolean burned;
        private final Long2LongOpenHashMap destinationLoads = new Long2LongOpenHashMap();
        private final Long2LongOpenHashMap wireLoads = new Long2LongOpenHashMap();
        private final List<TransferRecord> transfers = new ArrayList<>();

        private NetworkTickState() {
            this.destinationLoads.defaultReturnValue(0L);
            this.wireLoads.defaultReturnValue(0L);
        }

        private void reset(long gameTime) {
            this.tick = gameTime;
            this.activeVoltage = 0L;
            this.usedMicroAmps = 0L;
            this.inputPower = 0L;
            this.outputPower = 0L;
            this.lossPower = 0L;
            this.burned = false;
            this.destinationLoads.clear();
            this.wireLoads.clear();
            this.transfers.clear();
        }

        private long destinationLoad(BlockPos destination) {
            return this.destinationLoads.get(destination.asLong());
        }

        private long wireLoad(BlockPos wirePos) {
            return this.wireLoads.get(wirePos.asLong());
        }

        private void recordTransfer(
                BlockPos source,
                BlockPos destination,
                long voltage,
                long inputAmount,
                long outputAmount,
                long lostAmount,
                long microAmps,
                int wireDistance,
                List<BlockPos> routeWires
        ) {
            this.activeVoltage = Math.max(this.activeVoltage, voltage);
            this.usedMicroAmps = saturatedAdd(this.usedMicroAmps, microAmps);
            this.inputPower = saturatedAdd(this.inputPower, inputAmount);
            this.outputPower = saturatedAdd(this.outputPower, outputAmount);
            this.lossPower = saturatedAdd(this.lossPower, lostAmount);
            this.destinationLoads.addTo(destination.asLong(), outputAmount);
            for (BlockPos routeWire : routeWires) {
                this.wireLoads.addTo(routeWire.asLong(), microAmps);
            }

            for (int index = 0; index < this.transfers.size(); index++) {
                TransferRecord transfer = this.transfers.get(index);
                if (transfer.source().equals(source)
                        && transfer.destination().equals(destination)
                        && transfer.voltage() == voltage
                        && transfer.wireDistance() == wireDistance) {
                    this.transfers.set(
                            index,
                            new TransferRecord(
                                    transfer.source(),
                                    transfer.destination(),
                                    voltage,
                                    saturatedAdd(transfer.inputPower(), inputAmount),
                                    saturatedAdd(transfer.outputPower(), outputAmount),
                                    saturatedAdd(transfer.lossPower(), lostAmount),
                                    wireDistance
                            )
                    );
                    return;
                }
            }

            this.transfers.add(
                    new TransferRecord(
                            source.immutable(),
                            destination.immutable(),
                            voltage,
                            inputAmount,
                            outputAmount,
                            lostAmount,
                            wireDistance
                    )
            );
        }
    }
}
