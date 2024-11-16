package org.homepoker.lib.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.homepoker.lib.exception.SystemException;
import org.jspecify.annotations.Nullable;

import java.text.SimpleDateFormat;

@SuppressWarnings("ALL")
public class JsonUtils {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd hh:mm"));
  }

  private JsonUtils() {
  }

  @Nullable
  public static String toJson(@Nullable Object o) {
    return toJson(o, objectMapper);
  }

  @Nullable
  public static String toJson(@Nullable Object o, ObjectMapper objectMapper) {
    try {
      return objectMapper.writeValueAsString(o);
    } catch (JsonProcessingException exception) {
      throw new SystemException("Error converting object to Json.", exception);
    }
  }

  @Nullable
  public static String toFormattedJson(@Nullable Object o) {
    return toFormattedJson(o, objectMapper);
  }

  @Nullable
  public static String toFormattedJson(@Nullable Object o, ObjectMapper objectMapper) {
    if (o == null) return null;
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
    } catch (JsonProcessingException exception) {
      throw new SystemException("Error converting object to Json.", exception);
    }
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
    try {
      return objectMapper.readValue(jsonString, valueType);
    } catch (JsonProcessingException exception) {
      throw new SystemException("Error converting JSON string [" + jsonString + "] to instance of ["
          + valueType.getCanonicalName() + "].", exception);
    }
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable String jsonString, TypeReference<T> valueTypeReference) {
    return jsonToObject(jsonString, valueTypeReference, objectMapper);
  }

  @Nullable
  public static <T> T jsonToObject(@Nullable String jsonString, TypeReference<T> valueTypeReference, ObjectMapper objectMapper) {
    if (jsonString == null) return null;
    try {
    return objectMapper.readValue(jsonString, valueTypeReference);
    } catch (JsonProcessingException exception) {
      throw new SystemException("Error converting JSON string [" + jsonString + "] to instance of ["
          + valueTypeReference + "].", exception);
    }
  }
}
