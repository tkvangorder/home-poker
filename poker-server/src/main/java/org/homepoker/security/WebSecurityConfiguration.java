package org.homepoker.security;

import org.homepoker.user.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
      AuthenticationProvider authenticationProvider) {

    return http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            (requests) -> requests
                .requestMatchers("/auth/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/ws/**").permitAll()
                .anyRequest().authenticated() // Everything else will require authentication
        )
        .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authenticationProvider(authenticationProvider)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /**
   * This bean validates the JWT token in the Authorization header of the request and sets the user on the security
   * context if the token is valid.
   *
   * @param jwtTokenService The service used to validate the JWT token.
   * @param userDetailsService The service used to load user details from the database.
   * @return The filter that will validate the JWT token.
   */
  @Bean
  JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenService jwtTokenService, UserDetailsService userDetailsService) {
    return new JwtAuthenticationFilter(jwtTokenService, userDetailsService);
  }

  /**
   * This bean is used to authenticate a user based on the provided username and password. This provider is an
   * instance of Spring's DaoAuthenticationProvider which is configured to use the userDetailsService and passwordEncoder.
   *
   * @param userDetailsService The service used to load user details from the database.
   * @param securityUtilities The utilities used to encode and decode passwords.
   * @return The authentication provider that will authenticate users.
   */
  @Bean
  AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, SecurityUtilities securityUtilities) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(securityUtilities.getPasswordEncoder());
    return provider;
  }

  /**
   *
   * @param userRepository The repository used to load user details from the database.
   * @param securityUtilities The utilities used to encode and decode passwords.
   * @return The user details service is used by Spring Security to load user details during authentication.
   */
  @Bean
  UserDetailsService userDetailsService(UserRepository userRepository, SecurityUtilities securityUtilities) {
    return new PokerUserDetailsService(userRepository, securityUtilities);
  }

  /**
   * @param config The configuration used to create the authentication manager.
   * @return The authentication manager is used by Spring Security to authenticate users.
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
    return config.getAuthenticationManager();
  }
}
