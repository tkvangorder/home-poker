package org.homepoker;

import org.homepoker.model.user.User;
import org.homepoker.recording.RecordedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
@EnableMongoAuditing
public class MongoConfiguration {

  private final MongoTemplate mongoTemplate;

  public MongoConfiguration(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @EventListener(ContextRefreshedEvent.class)
  public void setupIndexes() {
    mongoTemplate.indexOps(User.class)
        .createIndex(new Index().on("email", Sort.Direction.ASC).unique());

    // Primary index for hand-scoped replay queries.
    mongoTemplate.indexOps(RecordedEvent.class)
        .createIndex(new Index()
            .on("gameId", Sort.Direction.ASC)
            .on("tableId", Sort.Direction.ASC)
            .on("handNumber", Sort.Direction.ASC)
            .on("sequenceNumber", Sort.Direction.ASC)
            .named("recordedEvents_replay_idx"));

    // Secondary index for whole-game queries (no v1 endpoint, but trivial to add later).
    mongoTemplate.indexOps(RecordedEvent.class)
        .createIndex(new Index()
            .on("gameId", Sort.Direction.ASC)
            .on("recordedAt", Sort.Direction.ASC)
            .named("recordedEvents_game_time_idx"));
  }
}
