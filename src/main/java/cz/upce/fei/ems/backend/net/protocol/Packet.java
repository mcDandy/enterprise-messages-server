package cz.upce.fei.ems.backend.net.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Packet {
    public static final byte FLAG_NONE = 0x00;
    public static final byte FLAG_ENCRYPTED = 0x01;

    private short type;
    private byte flags;
    private long timestamp;
    private byte[] payload;

    public Packet(short type, byte[] payload) {
        this(type, FLAG_NONE, System.currentTimeMillis(), payload);
    }
}