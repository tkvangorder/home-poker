package org.homepoker;

import org.homepoker.model.user.User;
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
    mongoTemplate.indexOps(User.class).ensureIndex(new Index().on("loginId", Sort.Direction.ASC).unique());
    mongoTemplate.indexOps("user").ensureIndex(new Index().on("email", Sort.Direction.ASC).unique());
  }
}
