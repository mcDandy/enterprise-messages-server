package cz.upce.fei.ems.backend.net.protocol;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum PacketType {
    //todo tady se pak budem muset domluvit, jaký všechny message chcem, ale už teďka říkám, že jich bude hoodně

    // --- Systémové & Základní (0x0001 - 0x000F) ---
    HANDSHAKE((short) 0x0001),
    CHAT_MESSAGE((short) 0x0002),
    ROOM_JOIN((short) 0x0003),
    PING((short) 0x0004),
    PONG((short) 0x0005),

    // --- Správa Serveru (0x0100 - 0x01FF) ---
    SERVER_CREATE((short) 0x0100),
    SERVER_DELETE((short) 0x0101),
    SERVER_JOIN((short) 0x0102),
    SERVER_LEAVE((short) 0x0103),

    // Pozvánky
    SERVER_INVITE_SEND((short) 0x0110),
    SERVER_INVITE_ACCEPT((short) 0x0111),
    SERVER_INVITE_DECLINE((short) 0x0112),

    // Správa členů
    SERVER_MEMBER_KICK((short) 0x0120),
    SERVER_MEMBER_BAN((short) 0x0121),
    SERVER_MEMBER_ROLE_UPDATE((short) 0x0122),

    // --- Kanály & Místnosti (0x0200 - 0x02FF) ---
    CHANNEL_CREATE((short) 0x0200),
    CHANNEL_DELETE((short) 0x0201),
    CHANNEL_JOIN((short) 0x0202),

    // --- Kryptografie (0x0400 - 0x04FF) ---
    KEY_EXCHANGE_REQ((short) 0x0400),
    KEY_EXCHANGE_RESP((short) 0x0401),
    KEY_ROTATION((short) 0x0402),

    UNKNOWN((short) 0x0000);

    private final short code;

    private static final Map<Short, PacketType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(PacketType::getCode, Function.identity()));

    public static PacketType fromCode(short code) {
        return BY_CODE.getOrDefault(code, UNKNOWN);
    }
}