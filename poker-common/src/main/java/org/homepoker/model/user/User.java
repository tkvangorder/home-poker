package org.homepoker.model.user;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * A user registered with the system.
 */
@Builder
@With
public record User(String id, String loginId, String password, String email, String alias, String name, String phone) {
}
