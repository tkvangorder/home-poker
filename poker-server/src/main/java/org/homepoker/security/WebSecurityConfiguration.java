package org.homepoker.security;

import org.homepoker.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
    // set the name of the attribute the CsrfToken will be populated on
    requestHandler.setCsrfRequestAttributeName(null);

    http
        .csrf(c -> c
            .ignoringRequestMatchers("/user/register", "/connect")
            .csrfTokenRequestHandler(requestHandler)
        )
        .authorizeHttpRequests((requests) -> requests
            .requestMatchers("/user/*").permitAll()
            .anyRequest().authenticated()
        ).httpBasic(withDefaults());
    return http.build();
  }

  @Bean
  UserDetailsService userDetailsService(UserRepository userRepository, PokerSecurityProperties securitySettings) {
    return new MongoUserDetailsService(userRepository, securitySettings);
  }

}
