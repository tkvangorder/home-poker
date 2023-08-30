package org.homepoker.game;

import java.time.LocalDate;

public record GameCriteria(String name, GameStatus status, LocalDate startDate, LocalDate endDate) {
}
