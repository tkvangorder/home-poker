package org.homepoker.client.converters;

import org.homepoker.lib.util.JsonUtils;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;

import java.io.IOException;
import java.util.Set;

/**
 * The Spring Shell uses Spring's ConversionService to marshal parameters to/from Strings.
 * The out-of-the-box converters handle common primitive datatypes. This converter can be
 * used to register classes that can be converted to/from JSON strings.
 *
 * @author tyler.vangorder
 */
public class ConditionalJsonConverter implements ConditionalGenericConverter {

  private final Set<Class<?>> classes;

  /**
   * The set of classes that will be included in the JSON converter.
   *
   * @param classes the classes to include in the converter.
   */
  public ConditionalJsonConverter(Class<?>... classes) {
    this.classes = Set.of(classes);
  }

  @Override
  public Set<ConvertiblePair> getConvertibleTypes() {
    //Per the documentation on ConditionalGenericConverter, this method can return null.
    return null;
  }

  @Override
  public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
    try {
      if (sourceType.getType() == String.class) {
        //String -> Object, using the target's type.
        return JsonUtils.readValue((String) source, targetType.getType());
      } else {
        //Object to JSON
        return JsonUtils.toJson(source);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to convert JSON type. " + e);
    }
  }

  @Override
  public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
    //This converter will signal which conversions it can handle
    // String --> (one of the registered classes)
    // (one of the registered classes) --> String
    return
        (classes.contains(sourceType.getType()) && targetType.getType() == String.class) ||
            (sourceType.getType() == String.class && classes.contains(targetType.getType()));
  }
}
