package cz.upce.fei.ems.backend.domain;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Composite database key identifying a server membership by server and user IDs.
 */

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MessageServerMemberId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long serverId;
    private Long userId;
}
