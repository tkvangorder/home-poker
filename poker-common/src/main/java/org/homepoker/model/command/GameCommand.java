package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.homepoker.lib.exception.SystemException;
import org.homepoker.model.user.User;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Map;
import java.util.Set;

/**
 * A command consists of an ID, a game ID, the user issuing the command and any additional data that is specific to
 * that command. A command does not have any behavior and is used to serialize and deserialize commands to and from JSON.
 * <P>
 * As such, the command ID acts as a discriminator for the JSON serialization/deserialization process. The command ID is
 * used to determine the concrete type of the command when deserializing JSON. Any object mapper that is used must
 * have the subtypes registered with it via the {@link GameCommand#registerCommandsWithJackson(ObjectMapper)} method.
 * <P>
 * The user field is NOT included in the JSON serialization/deserialization and is instead injected via Spring
 * Security when the command is submitted to the game manager.
 *
 * @author tyler.vangorder
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "commandId")
public interface GameCommand {

  @JsonTypeId
  CommandId commandId();
  String gameId();
  @JsonIgnore
  User user();

  /**
   * This method will scan for all game commands (annotated with GameCommandType) and dynamically register those types with
   * the given object mapper. This allows the object mapper to polymorphically deserialize a GameCommand
   * into the correct subtype.
   *
   * @param objectMapper The object mapper to register the subtypes with
   */
  static void registerCommandsWithJackson(ObjectMapper objectMapper) {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter((GameCommandType.class)));

    Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents("org.homepoker.model.command");
    for (BeanDefinition beanDefinition : beanDefinitions) {
      if (beanDefinition instanceof AnnotatedBeanDefinition annotatedDefinition) {
        Map<String, Object> annotationAttributes = annotatedDefinition.getMetadata()
            .getAnnotationAttributes(GameCommandType.class.getCanonicalName());

        String subTypeName = annotationAttributes == null ? null : annotationAttributes.get("value").toString();
        if (subTypeName == null) {
          continue;
        }
        try {
          objectMapper.registerSubtypes(new NamedType(Class.forName(beanDefinition.getBeanClassName()), subTypeName));
        } catch (ClassNotFoundException e) {
          throw new SystemException("Failed to register JSON subtype [" + beanDefinition.getBeanClassName()
              + "] under the name [" + subTypeName + "]", e);
        }
      }
    }
  }
}
