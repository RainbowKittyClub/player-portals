package club.rainbowkitty.playerportals.mixin;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.soundpatcher.impl.SoundRemapperImpl;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.sounds.SoundEvent;

/**
 * Keeps Polymer's sound patcher from renaming a sound for a client without the resource pack.
 * The patcher moves each converted vanilla sound event to its own namespace in the pack and
 * rewrites the id in every outgoing sound packet, pack or no pack; a client without the pack has
 * no such sound and plays nothing, so every wood step it hears another player take would go
 * silent. The pack is optional here, so the rename is kept to the clients that hold it.
 */
@Mixin(value = SoundRemapperImpl.class, remap = false)
public abstract class SoundRemapperMixin {
    @Inject(method = "remap", at = @At("HEAD"), cancellable = true)
    private static void playerportals$onlyWithPack(SoundEvent event,
            CallbackInfoReturnable<SoundEvent> cir) {
        if (!PolymerResourcePackUtils.hasMainPack(PacketContext.get())) {
            cir.setReturnValue(event);
        }
    }
}
