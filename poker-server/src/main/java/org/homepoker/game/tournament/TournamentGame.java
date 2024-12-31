package org.homepoker.game.tournament;

import lombok.*;
import org.homepoker.model.game.*;
import org.homepoker.model.user.User;

import java.time.Instant;
import java.util.Map;

@Builder
@Setter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public final class TournamentGame implements Game<TournamentGame> {
  private String id;
  private String name;
  private GameFormat format;
  private GameType type;
  private Instant startTimestamp;
  private Instant endTimestamp;
  private GameStatus status;
  private User owner;
  private Map<String, Player> players;
  private Map<String, Table> tables;
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
  public String id() {
    return id;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GameFormat format() {
    return format;
  }

  @Override
  public GameType type() {
    return type;
  }

  public Instant startTimestamp() {
    return startTimestamp;
  }

  public Instant endTimestamp() {
    return endTimestamp;
  }

  @Override
  public GameStatus status() {
    return status;
  }

  @Override
  public User owner() {
    return owner;
  }

  @Override
  public Map<String, Player> players() {
    return players;
  }

  @Override
  public Map<String, Table> tables() {
    return tables;
  }

  public Integer buyInChips() {
    return buyInChips;
  }

  public Integer buyInAmount() {
    return buyInAmount;
  }

  public int blindIntervalMinutes() {
    return blindIntervalMinutes;
  }

  public int estimatedTournamentLengthHours() {
    return estimatedTournamentLengthHours;
  }

  public Integer cliffLevel() {
    return cliffLevel;
  }

  public Integer numberOfRebuys() {
    return numberOfRebuys;
  }

  public Integer rebuyChips() {
    return rebuyChips;
  }

  public Integer rebuyAmount() {
    return rebuyAmount;
  }

  public boolean addOnAllowed() {
    return addOnAllowed;
  }

  public Integer addOnChips() {
    return addOnChips;
  }

  public Integer addOnAmount() {
    return addOnAmount;
  }

  public BlindSchedule blindSchedule() {
    return blindSchedule;
  }

  @Override
  public Instant lastModified() {
    return lastModified;
  }
}
