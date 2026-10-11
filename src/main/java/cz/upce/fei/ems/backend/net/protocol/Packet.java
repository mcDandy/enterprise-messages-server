package cz.upce.fei.ems.backend.net.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Packet {
    private byte type;
    private byte[] payload;
}