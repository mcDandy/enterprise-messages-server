package cz.upce.fei.ems.backend.net.protocol;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines packet type codes understood by the server protocol.
 */

@Getter
@RequiredArgsConstructor
public enum PacketType {

    UNKNOWN((byte) 0x00),
    CHAT_MESSAGE_ENC((byte) 0x01),
    ACKNOWLEDGE((byte) 0x02),
    READ((byte) 0x03),
    ROTATE_KEY((byte) 0x0a),
    UNICAST_KEY_ROTATION((byte) 0x0d),
    CHANNEL_MEMBER_INFO((byte) 0x0e);

    private final byte code;

    /**
     * Resolves a wire code to a known packet type.
     *
     * @param code type byte from the frame header
     * @return the matching type, or {@link #UNKNOWN} for unrecognized codes
     */
    public static PacketType fromCode(byte code) {
        for (PacketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
