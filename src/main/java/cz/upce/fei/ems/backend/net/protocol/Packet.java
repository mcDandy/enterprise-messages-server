package cz.upce.fei.ems.backend.net.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a decoded protocol packet as a type byte and opaque payload.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Packet {
    private byte type;
    private byte[] payload;
}
