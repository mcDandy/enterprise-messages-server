package cz.upce.fei.ems.backend.dto;

import cz.upce.fei.ems.backend.domain.UserKey;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Detail E2EE klíče uživatele")
public class UserKeyResponseDto {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "1")
    private Long userId;

    @Schema(example = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...")
    private String publicKey;

    public static UserKeyResponseDto fromEntity(UserKey userKey) {
        if (userKey == null) {
            return null;
        }
        return UserKeyResponseDto.builder()
                .id(userKey.getId())
                .userId(userKey.getUserId())
                .publicKey(userKey.getPublicKey())
                .build();
    }
}