package org.homepoker.model.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.jsontype.NamedType;
import org.homepoker.lib.exception.SystemException;
import org.homepoker.lib.util.StringUtils;
import org.homepoker.model.user.User;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import tools.jackson.databind.module.SimpleModule;

import java.util.Set;

/**
 * A command consists of an ID, a game ID, the user issuing the command and any additional data that is specific to
 * that command. A command does not have any behavior and is used to serialize and deserialize commands to and from JSON.
 * <P>
 * As such, the command ID acts as a discriminator for the JSON serialization/deserialization process. The command ID is
 * used to determine the concrete type of the command when deserializing JSON. Any object mapper that is used must
 * have the GameCommandModule registered with it. The module can be instantiated by using the {@link GameCommand#gameCommandsModule()} method.
 * <P>
 * The user field is excluded from JSON serialization but can be populated during deserialization
 * (WRITE_ONLY access). On the server, the user is injected into the JSON tree from the authenticated
 * session before deserializing the command.
 *
 * @author tyler.vangorder
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "commandId")
public interface GameCommand {

  @JsonTypeId
  default String commandId() {
    return StringUtils.camelToKabobCase(this.getClass().getSimpleName());
  }

  String gameId();
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  User user();

  /**
   * This module scan for all game commands (annotated with GameCommandMarker) and dynamically register those types with
   * the module. This allows the object mapper perform polymorphic deserialization of a GameCommand into
   * the correct subtype. The module can be added to the JsonMapper using the builder.
   */
  static JacksonModule gameCommandsModule() {

    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter((GameCommandMarker.class)));

    Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents("org.homepoker.model.command");
		SimpleModule module = new SimpleModule("GameCommandsModule");

    for (BeanDefinition beanDefinition : beanDefinitions) {
      if (beanDefinition instanceof AnnotatedBeanDefinition && beanDefinition.getBeanClassName() != null) {
        int lastDot = beanDefinition.getBeanClassName().lastIndexOf('.');
        if (lastDot == -1) {
          continue;
        }
        String classSimpleName = StringUtils.camelToKabobCase(beanDefinition.getBeanClassName().substring(lastDot + 1));

        try {

          module.registerSubtypes(new NamedType(Class.forName(beanDefinition.getBeanClassName()), classSimpleName));
        } catch (ClassNotFoundException e) {
          throw new SystemException("Failed to register JSON subtype [" + beanDefinition.getBeanClassName()
              + "] under the name [" + classSimpleName + "]", e);
        }
      }
    }
		return module;
  }
}
