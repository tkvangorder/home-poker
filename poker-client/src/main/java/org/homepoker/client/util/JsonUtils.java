package org.homepoker.client.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.text.SimpleDateFormat;

public class JsonUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd hh:mm"));
  }

  private JsonUtils() {
  }

  public static String toJson(Object o) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (JsonProcessingException exception) {
      return "Error : " + exception;
    }
  }

  @Nullable
  public static String toFormattedJson(@Nullable Object o) {
    if (o == null) return null;
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
    } catch (JsonProcessingException exception) {
      return "Error : " + exception;
    }
  }

  public static <T> T readValue(String jsonString, Class<T> valueType) throws IOException {
    return objectMapper.readValue(jsonString, valueType);
  }

  public static <T> T readValue(String jsonString, TypeReference<T> valueTypeReference)
      throws IOException {
    return objectMapper.readValue(jsonString, valueTypeReference);
  }
}
