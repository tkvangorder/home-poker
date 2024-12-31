package org.homepoker.model.game.cash;

import lombok.*;
import lombok.experimental.Accessors;
import org.homepoker.model.game.*;
import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;

@Builder
@Data
@Accessors(fluent = true)
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
  private final Map<String, Player> players;
  private final Map<String, Table> tables;

  @Override
  public GameFormat format() {
    return GameFormat.CASH;
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

  @SuppressWarnings({"FieldMayBeFinal", "MismatchedQueryAndUpdateOfCollection"})
  public static class CashGameBuilder {
    private GameStatus status = GameStatus.SCHEDULED;
    private Map<String, Player> players = new HashMap<>();
    private Map<String, Table> tables = new HashMap<>();

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

