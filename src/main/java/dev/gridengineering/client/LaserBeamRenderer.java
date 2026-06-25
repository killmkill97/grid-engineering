package dev.gridengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gridengineering.block.entity.LaserTransformerBlockEntity;
import dev.gridengineering.config.LaserConfig;
import dev.gridengineering.laser.LaserRole;
import java.awt.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LaserBeamRenderer implements BlockEntityRenderer<LaserTransformerBlockEntity> {
    public LaserBeamRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            LaserTransformerBlockEntity transformer,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (!shouldOwnBeamRender(transformer)) {
            return;
        }

        BlockPos delta = transformer.linkedPos().subtract(transformer.getBlockPos());
        BeamBounds bounds = BeamBounds.between(delta, 0.10F);
        LaserTransformerBlockEntity sender = linkedSender(transformer);
        var beamTier = sender == null ? transformer.tier() : sender.tier();
        int color = beamTier.isRainbow()
                ? Color.HSBtoRGB(
                        ((transformer.getLevel().getGameTime() + partialTick) % 120.0F) / 120.0F,
                        1.0F,
                        1.0F
                ) & 0xFFFFFF
                : beamTier.color();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        renderBox(poseStack.last(), consumer, bounds, color, 64);
        renderBox(
                poseStack.last(),
                consumer,
                BeamBounds.between(delta, 0.035F),
                color,
                230
        );
    }

    @Override
    public boolean shouldRenderOffScreen(LaserTransformerBlockEntity transformer) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(LaserTransformerBlockEntity transformer) {
        if (transformer.linkedPos() == null) {
            return new AABB(transformer.getBlockPos());
        }
        return new AABB(transformer.getBlockPos())
                .minmax(new AABB(transformer.linkedPos()))
                .inflate(0.25D);
    }

    @Override
    public int getViewDistance() {
        return LaserConfig.renderDistance();
    }

    @Override
    public boolean shouldRender(LaserTransformerBlockEntity transformer, Vec3 cameraPos) {
        if (!shouldOwnBeamRender(transformer)) {
            return false;
        }
        Vec3 sender = Vec3.atCenterOf(transformer.getBlockPos());
        Vec3 receiver = Vec3.atCenterOf(transformer.linkedPos());
        double viewDistance = this.getViewDistance();
        return sender.closerThan(cameraPos, viewDistance)
                || receiver.closerThan(cameraPos, viewDistance)
                || distanceToSegmentSquared(cameraPos, sender, receiver)
                <= viewDistance * viewDistance;
    }

    private static boolean shouldOwnBeamRender(LaserTransformerBlockEntity transformer) {
        if (transformer.linkedPos() == null || transformer.getLevel() == null) {
            return false;
        }
        if (transformer.role() == LaserRole.SENDER) {
            return true;
        }

        LaserTransformerBlockEntity sender = linkedSender(transformer);
        return sender == null || sender.linkedPos() == null;
    }

    private static LaserTransformerBlockEntity linkedSender(
            LaserTransformerBlockEntity transformer
    ) {
        if (transformer.getLevel() == null || transformer.linkedPos() == null) {
            return null;
        }
        if (transformer.role() == LaserRole.SENDER) {
            return transformer;
        }
        return transformer.getLevel().getBlockEntity(transformer.linkedPos())
                instanceof LaserTransformerBlockEntity linked
                && linked.role() == LaserRole.SENDER
                ? linked
                : null;
    }

    private static void renderBox(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            BeamBounds bounds,
            int color,
            int alpha
    ) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        float x0 = bounds.minimumX();
        float y0 = bounds.minimumY();
        float z0 = bounds.minimumZ();
        float x1 = bounds.maximumX();
        float y1 = bounds.maximumY();
        float z1 = bounds.maximumZ();

        quad(pose, consumer, red, green, blue, alpha, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, consumer, red, green, blue, alpha, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(pose, consumer, red, green, blue, alpha, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, consumer, red, green, blue, alpha, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(pose, consumer, red, green, blue, alpha, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, consumer, red, green, blue, alpha, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
    }

    private static void quad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int red,
            int green,
            int blue,
            int alpha,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3
    ) {
        consumer.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x2, y2, z2).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x3, y3, z3).setColor(red, green, blue, alpha);
    }

    private static double distanceToSegmentSquared(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared == 0.0D) {
            return point.distanceToSqr(start);
        }
        double progress = Math.max(
                0.0D,
                Math.min(1.0D, point.subtract(start).dot(segment) / lengthSquared)
        );
        return point.distanceToSqr(start.add(segment.scale(progress)));
    }

    private record BeamBounds(
            float minimumX,
            float minimumY,
            float minimumZ,
            float maximumX,
            float maximumY,
            float maximumZ
    ) {
        private static BeamBounds between(BlockPos delta, float radius) {
            float minimumX = 0.5F - radius;
            float maximumX = 0.5F + radius;
            float minimumY = 0.5F - radius;
            float maximumY = 0.5F + radius;
            float minimumZ = 0.5F - radius;
            float maximumZ = 0.5F + radius;

            if (delta.getX() > 0) {
                minimumX = 1.0F;
                maximumX = delta.getX();
            } else if (delta.getX() < 0) {
                minimumX = delta.getX() + 1.0F;
                maximumX = 0.0F;
            } else if (delta.getY() > 0) {
                minimumY = 1.0F;
                maximumY = delta.getY();
            } else if (delta.getY() < 0) {
                minimumY = delta.getY() + 1.0F;
                maximumY = 0.0F;
            } else if (delta.getZ() > 0) {
                minimumZ = 1.0F;
                maximumZ = delta.getZ();
            } else if (delta.getZ() < 0) {
                minimumZ = delta.getZ() + 1.0F;
                maximumZ = 0.0F;
            }
            return new BeamBounds(
                    minimumX,
                    minimumY,
                    minimumZ,
                    maximumX,
                    maximumY,
                    maximumZ
            );
        }
    }
}
