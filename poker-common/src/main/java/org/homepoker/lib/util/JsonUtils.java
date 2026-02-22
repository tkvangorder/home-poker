package org.homepoker.lib.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("ALL")
public class JsonUtils {

  private static final ObjectMapper objectMapper = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build();

  private JsonUtils() {
  }

  @Nullable
  public static String toJson(@Nullable Object o) {
    return toJson(o, objectMapper);
  }

  @Nullable
  public static String toJson(@Nullable Object o, ObjectMapper objectMapper) {
    return objectMapper.writeValueAsString(o);
  }

  @Nullable
  public static String toFormattedJson(@Nullable Object o) {
    return toFormattedJson(o, objectMapper);
  }

  @Nullable
  public static String toFormattedJson(@Nullable Object o, ObjectMapper objectMapper) {
    if (o == null) return null;
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable JsonNode node, Class<T> type) {
    return jsonToObject(node, type, objectMapper);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable JsonNode node, Class<T> type, ObjectMapper objectMapper) {
    if (node == null) return null;
    return objectMapper.convertValue(node, type);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable JsonNode node, TypeReference<T> type) {
    return jsonToObject(node, type, objectMapper);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable JsonNode node, TypeReference<T> type, ObjectMapper objectMapper) {
    if (node == null) return null;
    return objectMapper.convertValue(node, type);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable String jsonString, Class<T> valueType) {
    return jsonToObject(jsonString, valueType, objectMapper);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable String jsonString, Class<T> valueType, ObjectMapper objectMapper) {
    if (jsonString == null) return null;
    return objectMapper.readValue(jsonString, valueType);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable String jsonString, TypeReference<T> valueTypeReference) {
    return jsonToObject(jsonString, valueTypeReference, objectMapper);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable String jsonString, TypeReference<T> valueTypeReference, ObjectMapper objectMapper) {
    if (jsonString == null) return null;
    return objectMapper.readValue(jsonString, valueTypeReference);
  }
}
