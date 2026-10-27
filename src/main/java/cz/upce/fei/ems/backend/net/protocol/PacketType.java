package cz.upce.fei.ems.backend.net.protocol;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PacketType {

    UNKNOWN((byte) 0x00),
    CHAT_MESSAGE_ENC((byte) 0x01),
    ROTATE_KEY((byte) 0x0a),
    UNICAST_KEY_ROTATION((byte) 0x0d);

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