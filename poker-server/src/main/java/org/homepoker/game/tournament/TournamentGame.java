package org.homepoker.game.tournament;

import lombok.Builder;
import lombok.With;
import org.homepoker.game.*;
import org.homepoker.model.game.GameFormat;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@With
@Builder
public record TournamentGame(
    String id,
    String name,
    GameFormat format,
    GameType type,
    Instant startTimestamp,
    Instant endTimestamp,
    GameStatus status,
    User owner,
    Map<String, Player> players,
    List<Table> tables,
    Integer buyInChips,
    Integer buyInAmount,
    int blindIntervalMinutes,
    int estimatedTournamentLengthHours,
    Integer cliffLevel,
    Integer numberOfRebuys,
    Integer rebuyChips,
    Integer rebuyAmount,
    boolean addOnAllowed,
    Integer addOnChips,
    Integer addOnAmount,
    BlindSchedule blindSchedule,
    Instant lastModified
) implements Game<TournamentGame> {

}
