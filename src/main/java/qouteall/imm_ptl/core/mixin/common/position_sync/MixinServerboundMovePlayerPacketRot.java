package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;

@Mixin(ServerboundMovePlayerPacket.Rot.class)
public class MixinServerboundMovePlayerPacketRot {
    @Inject(method = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot;read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot;", at = @At("RETURN"), cancellable = true)
    private static void onRead(
        FriendlyByteBuf buf, CallbackInfoReturnable<ServerboundMovePlayerPacket.Rot> cir
    ) {
        ResourceKey<Level> playerDim = buf.readResourceKey(Registries.DIMENSION);
        ((IEPlayerMoveC2SPacket) cir.getReturnValue()).ip_setPlayerDimension(playerDim);
    }
}
