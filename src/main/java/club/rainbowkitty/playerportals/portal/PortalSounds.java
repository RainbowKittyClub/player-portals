package club.rainbowkitty.playerportals.portal;

import eu.pb4.polymer.core.api.other.PolymerSoundEvent;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import club.rainbowkitty.playerportals.PlayerPortals;

/**
 * This mod's sounds. Each is a Polymer sound event: a client with the resource pack hears the
 * sound the pack carries under this id, any other hears the vanilla sound named as its fallback.
 */
public final class PortalSounds {
    /** The warp a player hears on arriving; vanilla's portal travel without the pack. */
    public static final SoundEvent PORTAL_JUMP =
            register("portal_jump", SoundEvents.PORTAL_TRAVEL);

    private PortalSounds() {
    }

    /** Forces registration; the field above does the work. */
    public static void init() {
    }

    private static SoundEvent register(String path, SoundEvent fallback) {
        Identifier id = PlayerPortals.id(path);
        SoundEvent event = Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id));
        return PolymerSoundEvent.registerOverlay(event, fallback,
                PolymerResourcePackUtils.getMainUuid());
    }
}
