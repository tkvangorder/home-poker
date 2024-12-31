package org.homepoker.model.game.cash;

import lombok.*;
import org.homepoker.model.game.*;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;

@Builder
@Setter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class CashGame implements Game<CashGame> {
  private String id;
  private String name;
  private GameType type;
  private Instant startTime;
  private @Nullable Instant endTime;
  private GameStatus status;
  private User owner;
  private Integer smallBlind;
  private Integer bigBlind;
  private Integer maxBuyIn;
  private Instant lastModified;
  private Map<String, Player> players;
  private Map<String, Table> tables;

  @Override
  public GameFormat format() {
    return GameFormat.CASH;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GameType type() {
    return type;
  }

  public Instant startTime() {
    return startTime;
  }

  public @Nullable Instant endTime() {
    return endTime;
  }

  @Override
  public GameStatus status() {
    return status;
  }

  @Override
  public User owner() {
    return owner;
  }

  public Integer smallBlind() {
    return smallBlind;
  }

  public Integer bigBlind() {
    return bigBlind;
  }

  public Integer maxBuyIn() {
    return maxBuyIn;
  }

  @Override
  public Instant lastModified() {
    return lastModified;
  }

  @Override
  public Map<String, Player> players() {
    return players;
  }

  @Override
  public Map<String, Table> tables() {
    return tables;
  }

  public CashGame copy() {
    return CashGame.builder()
        .id(id)
        .name(name)
        .type(type)
        .startTime(startTime)
        .endTime(endTime)
        .status(status)
        .owner(owner)
        .smallBlind(smallBlind)
        .bigBlind(bigBlind)
        .maxBuyIn(maxBuyIn)
        .lastModified(lastModified)
        .players(new HashMap<>(players))
        .tables(new HashMap<>(tables))
        .build();
  }
  public static class CashGameBuilder {

    @SuppressWarnings("FieldMayBeFinal")
    private GameStatus status = GameStatus.SCHEDULED;
    private final Map<String, Player> players = new HashMap<>();
    private final Map<String, Table> tables = new HashMap<>();

    public CashGame build() {
      return new CashGame(id, name, type, startTime, endTime, status, owner, smallBlind, bigBlind,
          maxBuyIn, lastModified, players, tables);
    }

    public CashGameBuilder player(Player player) {
      if (player.user().id() == null) {
        throw new IllegalArgumentException("Player must have a user id");
      }
      players.put(player.user().id(), player);
      return this;
    }

    public CashGameBuilder table(Table table) {
      tables.put(table.id(), table);
      return this;
    }
  }
}

