package org.homepoker.model.game;

import java.time.Instant;
import java.time.LocalDate;

public record GameCriteria(String name, GameStatus status, Instant startDate, Instant endDate) {
}
