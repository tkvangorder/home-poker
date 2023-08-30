package org.homepoker.client.converters;

import org.homepoker.model.user.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConverterConfiguration {

  @Bean
  ConditionalJsonConverter converters() {
    return new ConditionalJsonConverter(User.class);
  }
}
