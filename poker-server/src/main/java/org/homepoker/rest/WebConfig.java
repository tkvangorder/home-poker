package org.homepoker.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedMethods(CorsConfiguration.ALL)
        .allowedHeaders(CorsConfiguration.ALL)
        .allowedOriginPatterns(CorsConfiguration.ALL);
  }

}
