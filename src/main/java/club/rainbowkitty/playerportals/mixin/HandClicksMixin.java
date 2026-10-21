package club.rainbowkitty.playerportals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.kalic0re.common.click.HandClicks;

/**
 * Routes each hand's part of a right-click through kalic0re's {@code common/click}, so a click
 * the server takes with one hand is not answered again with the other. A vanilla client sees a
 * catalyst as a nether star, which does nothing to a block, so it offers the click to both
 * hands; a catalyst in each would otherwise both act on one frame.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HandClicksMixin {
    private static final String USE_ITEM_ON =
            "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn("
            + "Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";

    private static final String USE_ITEM =
            "Lnet/minecraft/server/level/ServerPlayerGameMode;useItem("
            + "Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)"
            + "Lnet/minecraft/world/InteractionResult;";

    private static final String PACKET_HAND =
            "Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;hand()"
            + "Lnet/minecraft/world/InteractionHand;";

    private static final String INTERACT_ON =
            "Lnet/minecraft/server/level/ServerPlayer;interactOn("
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;";

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target = USE_ITEM_ON))
    private InteractionResult playerportals$atBlock(ServerPlayerGameMode gameMode,
            ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
            BlockHitResult hit, Operation<InteractionResult> original) {
        return HandClicks.atBlock(playerportals$self(), hand,
                () -> original.call(gameMode, player, level, stack, hand, hit));
    }

    @WrapOperation(method = "handleUseItem", at = @At(value = "INVOKE", target = USE_ITEM))
    private InteractionResult playerportals$withItem(ServerPlayerGameMode gameMode,
            ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
            Operation<InteractionResult> original) {
        return HandClicks.withItem(playerportals$self(), hand,
                () -> original.call(gameMode, player, level, stack, hand));
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = PACKET_HAND),
            cancellable = true)
    private void playerportals$offerAtEntity(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (!HandClicks.offerAtEntity(playerportals$self(), packet.hand())) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE", target = INTERACT_ON))
    private InteractionResult playerportals$atEntity(ServerPlayer player, Entity target,
            InteractionHand hand, Vec3 location, Operation<InteractionResult> original) {
        return HandClicks.atEntity(playerportals$self(),
                () -> original.call(player, target, hand, location));
    }

    @Unique
    private ServerGamePacketListenerImpl playerportals$self() {
        return (ServerGamePacketListenerImpl) (Object) this;
    }
}
