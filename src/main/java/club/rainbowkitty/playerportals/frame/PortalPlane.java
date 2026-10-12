package club.rainbowkitty.playerportals.frame;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/**
 * The orientation of a portal's plane, named as {@code nether_portal[axis]} names it: {@code X}
 * and {@code Z} stand upright and run along that horizontal axis; {@code Y} lies flat.
 */
public enum PortalPlane implements StringRepresentable {
    X(Direction.Axis.X, Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z),
    Z(Direction.Axis.Z, Direction.Axis.Z, Direction.Axis.Y, Direction.Axis.X),
    Y(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y);

    public static final Codec<PortalPlane> CODEC =
            StringRepresentable.fromEnum(PortalPlane::values);

    private final Direction.Axis axis;
    private final Direction.Axis along;
    private final Direction.Axis up;
    private final Direction.Axis normal;

    PortalPlane(Direction.Axis axis, Direction.Axis along, Direction.Axis up,
            Direction.Axis normal) {
        this.axis = axis;
        this.along = along;
        this.up = up;
        this.normal = normal;
    }

    /** The plane the block-state axis {@code axis} names. */
    public static PortalPlane of(Direction.Axis axis) {
        return switch (axis) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }

    /** The planes whose span contains {@code axis}, upright ones first. */
    public static List<PortalPlane> spanning(Direction.Axis axis) {
        return switch (axis) {
            case X -> List.of(X, Y);
            case Z -> List.of(Z, Y);
            case Y -> List.of(X, Z);
        };
    }

    /** The block-state axis value this plane is stored as. */
    public Direction.Axis axis() {
        return axis;
    }

    /** The in-plane axis a frame's width runs along. */
    public Direction.Axis along() {
        return along;
    }

    /** The in-plane axis a frame's height runs along: up when upright, south when flat. */
    public Direction.Axis up() {
        return up;
    }

    /** The axis across the plane, which an entity crosses when it goes through. */
    public Direction.Axis normal() {
        return normal;
    }

    /** Whether {@code axis} lies in this plane. */
    public boolean spans(Direction.Axis candidate) {
        return candidate == along || candidate == up;
    }

    /** Whether this plane lies flat, as a hole in a floor or ceiling. */
    public boolean isFlat() {
        return this == Y;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
