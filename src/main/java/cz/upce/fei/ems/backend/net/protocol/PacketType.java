package cz.upce.fei.ems.backend.net.protocol;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PacketType {

    UNKNOWN((byte) 0x00),
    CHAT_MESSAGE_ENC((byte) 0x02),
    MESSAGE_RECEIPT((byte) 0x03),
    CHANNEL_JOIN((byte) 0x04),
    ROTATE_KEY((byte) 0x0a),
    MULTICAST_ROTATED_KEY((byte) 0x0b);

    private final byte code;

    public static PacketType fromCode(byte code) {
        for (PacketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}