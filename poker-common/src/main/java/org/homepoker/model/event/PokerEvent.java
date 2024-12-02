package org.homepoker.model.event;

import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.homepoker.lib.exception.SystemException;
import org.homepoker.lib.util.StringUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.time.Instant;
import java.util.Set;

/**
 * Base interface for all events emitted by the game server. This interface provides a mechanism for dynamically
 * registering all event subtypes with Jackson for polymorphic deserialization by calling the static method "registerEventsWithJackson".
 */
public interface PokerEvent {

  @JsonTypeId
  default String eventType() {
    return StringUtils.camelToKabobCase(this.getClass().getSimpleName());
  }

  Instant timestamp();

  @SuppressWarnings("ConstantValue")
  default boolean isValid() {
    return timestamp() != null;
  }

  /**
   * This method will scan for all game events (annotated with GameEventMarker) and dynamically register those types with
   * the given object mapper. This allows the object mapper perform polymorphic deserialization of a GameEvent into
   * the correct subtype.
   *
   * @param objectMapper The object mapper to register the subtypes with
   */
  static void registerEventsWithJackson(ObjectMapper objectMapper) {
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter((EventMarker.class)));

    Set<BeanDefinition> beanDefinitions = scanner.findCandidateComponents("org.homepoker.model.event");
    for (BeanDefinition beanDefinition : beanDefinitions) {
      if (beanDefinition instanceof AnnotatedBeanDefinition annotatedDefinition && beanDefinition.getBeanClassName() != null) {
        int lastDot = beanDefinition.getBeanClassName().lastIndexOf('.');
        if (lastDot == -1) {
          continue;
        }
        String classSimpleName = StringUtils.camelToKabobCase(beanDefinition.getBeanClassName().substring(lastDot + 1));

        try {
          objectMapper.registerSubtypes(new NamedType(Class.forName(beanDefinition.getBeanClassName()), classSimpleName));
        } catch (ClassNotFoundException e) {
          throw new SystemException("Failed to register JSON subtype [" + beanDefinition.getBeanClassName()
              + "] under the name [" + classSimpleName + "]", e);
        }
      }
    }
  }

}

