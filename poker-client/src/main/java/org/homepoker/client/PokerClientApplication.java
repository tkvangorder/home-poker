package org.homepoker.client;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootApplication
@EnableWebSocket
public class PokerClientApplication {

  public static void main(String[] args) {
    SpringApplication.run(PokerClientApplication.class, args);
  }

  @Bean
  WebSocketStompClient stompClient(Jackson2ObjectMapperBuilder builder) {

    WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    stompClient.setMessageConverter(converter);
    return stompClient;

  }

//  @Bean
//  public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
//    ObjectMapper objectMapper = builder.build();
//    objectMapper.activateDefaultTyping(new LaissezFaireSubTypeValidator(), ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
//    return objectMapper;
//  }
}
