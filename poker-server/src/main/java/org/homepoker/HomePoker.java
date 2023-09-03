package org.homepoker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HomePoker {

  public static void main(String[] args) {
    SpringApplication.run(HomePoker.class, args);
  }

}
