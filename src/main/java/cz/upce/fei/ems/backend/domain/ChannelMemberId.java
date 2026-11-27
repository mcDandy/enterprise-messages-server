package cz.upce.fei.ems.backend.domain;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Composite database key identifying a channel membership by channel and user IDs.
 */

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChannelMemberId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long channelId;
    private Long userId;
}
