package club.rainbowkitty.playerportals.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import club.rainbowkitty.kalic0re.common.item.PolymerModelItem;
import club.rainbowkitty.playerportals.PlayerPortals;
import club.rainbowkitty.playerportals.frame.FrameFinder;
import club.rainbowkitty.playerportals.frame.PortalFrame;

/**
 * An item that works in two clicks on frame blocks: the first attunes it to that frame, the
 * second acts on the pair. A vanilla client sees a heart of the sea under this item's name, with
 * the attuned frame as a lore line; a client with the resource pack sees the item's own art.
 * Sneak-use on nothing clears the attunement.
 */
public abstract class CatalystItem extends PolymerModelItem {
    private static final Style HINT = Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false);
    private static final Style ATTUNED =
            Style.EMPTY.withColor(ChatFormatting.AQUA).withItalic(false);

    private final String path;

    protected CatalystItem(ResourceKey<Item> key, String path, Properties properties) {
        super(Items.HEART_OF_THE_SEA, PlayerPortals.id(path), properties.setId(key).stacksTo(1)
                .component(DataComponents.LORE, new ItemLore(List.of(hint(path)))));
        this.path = path;
    }

    /** {@code frame} for a player: size, orientation, origin and dimension. */
    public static MutableComponent describe(PortalFrame frame) {
        Identifier dimension = frame.dimension().identifier();
        String where = "minecraft".equals(dimension.getNamespace())
                ? dimension.getPath() : dimension.toString();
        return PlayerPortals.text().of(PlayerPortals.MOD_ID + ".frame", frame.width(),
                frame.height(), PlayerPortals.text().of(PlayerPortals.MOD_ID + ".plane."
                        + frame.plane().getSerializedName()),
                frame.origin().getX(), frame.origin().getY(), frame.origin().getZ(), where);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!permitted(player)) {
            say(player, deniedKey());
            return InteractionResult.CONSUME;
        }
        Optional<PortalFrame> frame =
                FrameFinder.findAround(level, context.getClickedPos(), context.getClickedFace());
        if (frame.isEmpty()) {
            say(player, "catalyst.no_frame");
            return InteractionResult.CONSUME;
        }
        return onFrame(level, player, context.getItemInHand(), frame.get());
    }

    // CONSUME, not SUCCESS: Polymer turns a success into a server-sent swing, which a vanilla
    // client echoes back as a left-click.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(CatalystComponents.ATTUNED)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            attune(stack, null);
            say(serverPlayer, "catalyst.cleared");
        }
        return InteractionResult.CONSUME;
    }

    /** Whether {@code player} may use this item at all. */
    protected abstract boolean permitted(ServerPlayer player);

    /** The message key, under {@code playerportals.}, for a player who may not. */
    protected abstract String deniedKey();

    /** This item's answer to a click on a frame block beside {@code frame}, a complete frame. */
    protected abstract InteractionResult onFrame(ServerLevel level, ServerPlayer player,
            ItemStack stack, PortalFrame frame);

    /** Sets, or with null clears, the frame {@code stack} is attuned to, and its lore line. */
    protected void attune(ItemStack stack, @Nullable PortalFrame frame) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(hint(path));
        if (frame == null) {
            stack.remove(CatalystComponents.ATTUNED);
        } else {
            stack.set(CatalystComponents.ATTUNED, frame);
            lines.add(PlayerPortals.text().of("item." + PlayerPortals.MOD_ID + ".catalyst.attuned",
                    describe(frame)).withStyle(ATTUNED));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    /** Tells {@code player} the line under {@code playerportals.}{@code key}, in chat. */
    protected static void say(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(
                PlayerPortals.text().of(PlayerPortals.MOD_ID + "." + key, args));
    }

    private static Component hint(String path) {
        return PlayerPortals.text().of("item." + PlayerPortals.MOD_ID + "." + path + ".tooltip")
                .withStyle(HINT);
    }
}
