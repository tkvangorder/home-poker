package org.homepoker.websocket;

import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.security.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

  private final CashGameService cashGameService;
  private final JwtTokenService jwtTokenService;
  private final UserDetailsService userDetailsService;

  public WebSocketConfiguration(CashGameService cashGameService, JwtTokenService jwtTokenService,
      UserDetailsService userDetailsService) {
    this.cashGameService = cashGameService;
    this.jwtTokenService = jwtTokenService;
    this.userDetailsService = userDetailsService;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(pokerWebSocketHandler(), "/ws/games/{gameId}")
        .addInterceptors(jwtHandshakeInterceptor())
        .setAllowedOriginPatterns("*");
  }

  @Bean
  JwtHandshakeInterceptor jwtHandshakeInterceptor() {
    return new JwtHandshakeInterceptor(jwtTokenService, userDetailsService);
  }

  @Bean
  PokerWebSocketHandler pokerWebSocketHandler() {
    return new PokerWebSocketHandler(cashGameService, webSocketObjectMapper());
  }

  /**
   * A dedicated ObjectMapper for WebSocket message serialization/deserialization.
   * Registers the polymorphic type modules for both commands and events.
   */
  @Bean
  ObjectMapper webSocketObjectMapper() {
    return JsonMapper.builder()
        .addModule(GameCommand.gameCommandsModule())
        .addModule(PokerEvent.pokerEventModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }
}
