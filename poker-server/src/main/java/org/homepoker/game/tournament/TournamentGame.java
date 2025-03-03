package org.homepoker.game.tournament;

import lombok.*;
import lombok.experimental.Accessors;
import org.homepoker.model.game.*;
import org.homepoker.model.user.User;

import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;

@Builder
@Data
@Accessors(fluent = true)
public final class TournamentGame implements Game<TournamentGame> {
  private String id;
  private String name;
  private GameFormat format;
  private GameType type;
  private Instant startTime;
  private Instant endTime;
  private GameStatus status;
  private User owner;
  private Map<String, Player> players;
  private NavigableMap<String, Table> tables;
  private Integer buyInChips;
  private Integer buyInAmount;
  private int blindIntervalMinutes;
  private int estimatedTournamentLengthHours;
  private Integer cliffLevel;
  private Integer numberOfRebuys;
  private Integer rebuyChips;
  private Integer rebuyAmount;
  private boolean addOnAllowed;
  private Integer addOnChips;
  private Integer addOnAmount;
  private BlindSchedule blindSchedule;
  private Instant lastModified;

  @Override
  public GameFormat format() {
    return GameFormat.TOURNAMENT;
  }

}
