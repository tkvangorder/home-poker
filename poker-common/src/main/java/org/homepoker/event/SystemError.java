package org.homepoker.event;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Builder
public record SystemError(
    Instant timestamp,
    @Nullable
    String gameId,
    @Nullable
    String userId,
    @Nullable
    String tableId,

    RuntimeException exception
) implements PokerEvent {
}
