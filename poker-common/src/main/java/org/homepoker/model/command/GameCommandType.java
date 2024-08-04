package org.homepoker.model.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotations used to dynamically map a game command subclass to the commandId when it is being serialized/deserialized
 * via Jackson. This annotation is used in combination with JsonUtils.registerSubtypes() to register all annotated game
 * commands with the ObjectMapper.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameCommandType {
  CommandId value();
}
