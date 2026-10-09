package cz.upce.fei.ems.backend.domain;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ServerMemberId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long serverId;
    private Long userId;
}
