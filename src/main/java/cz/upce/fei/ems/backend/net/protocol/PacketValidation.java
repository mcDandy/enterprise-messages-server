package cz.upce.fei.ems.backend.net.protocol;

/** Centralizes protocol payload size limits. */
public final class PacketValidation {
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private PacketValidation() {}
}
