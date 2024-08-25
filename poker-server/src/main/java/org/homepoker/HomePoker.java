package org.homepoker;

import org.homepoker.threading.VirtualThreadManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMethodSecurity
public class HomePoker {

  public static void main(String[] args) {
    SpringApplication.run(HomePoker.class, args);
  }

  @Bean
  public VirtualThreadManager virtualThreadManager() {
    return new VirtualThreadManager();
  }

}
