// Fabric 26.3 server port of the pinned upstream file; see PORTING.md.
package com.riichimahjong.cuterenderer;

import net.minecraft.network.FriendlyByteBuf;

public sealed interface InteractKey {
    byte wireTag();

    void encode(FriendlyByteBuf buf);

    record Named(String name) implements InteractKey {
        public static final byte TAG = 1;
        @Override public byte wireTag() { return TAG; }
        @Override public void encode(FriendlyByteBuf buf) { buf.writeUtf(name, 64); }
        public static Named decode(FriendlyByteBuf buf) { return new Named(buf.readUtf(64)); }
    }

    record Slot(int index) implements InteractKey {
        public static final byte TAG = 3;
        @Override public byte wireTag() { return TAG; }
        @Override public void encode(FriendlyByteBuf buf) { buf.writeVarInt(index); }
        public static Slot decode(FriendlyByteBuf buf) { return new Slot(buf.readVarInt()); }
    }

    record SeatSlot(byte seat, byte area, short index) implements InteractKey {
        public static final byte TAG = 2;
        public static final byte AREA_HAND = 0;
        public static final byte AREA_RIVER = 1;
        public static final byte AREA_MELD = 2;
        public static final byte AREA_WALL = 3;

        public static final byte AREA_BUTTON = 4;
        @Override public byte wireTag() { return TAG; }
        @Override public void encode(FriendlyByteBuf buf) {
            buf.writeByte(seat); buf.writeByte(area); buf.writeShort(index);
        }
        public static SeatSlot decode(FriendlyByteBuf buf) {
            return new SeatSlot(buf.readByte(), buf.readByte(), buf.readShort());
        }
    }

    static void write(FriendlyByteBuf buf, InteractKey key) {
        buf.writeByte(key.wireTag());
        key.encode(buf);
    }

    static InteractKey read(FriendlyByteBuf buf) {
        byte tag = buf.readByte();
        return switch (tag) {
            case Named.TAG -> Named.decode(buf);
            case SeatSlot.TAG -> SeatSlot.decode(buf);
            case Slot.TAG -> Slot.decode(buf);
            default -> throw new IllegalArgumentException("Unknown InteractKey tag: " + tag);
        };
    }
}
