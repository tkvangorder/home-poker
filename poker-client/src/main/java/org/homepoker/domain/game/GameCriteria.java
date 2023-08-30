package org.homepoker.domain.game;

import java.time.LocalDate;

import lombok.Value;

@Value
public class GameCriteria {

	String name;
	GameStatus status;
	LocalDate startDate;
	LocalDate endDate;
}
