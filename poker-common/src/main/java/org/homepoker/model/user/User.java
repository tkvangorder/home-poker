package org.homepoker.model.user;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal=true, level=AccessLevel.PRIVATE)
@EqualsAndHashCode
@AllArgsConstructor
@Builder
@With
public class User {

  /**
   * Internally assigned unique key used for persistence.
   */
  String id;

  /**
   * The user ID chosen by the user. (Immutable, the only way to change this is to delete the user and then recreate)
   */
  String loginId;

  /**
   * User's password, always encrypted.
   */
  String password;

  /**
   * User's email, also used as the login ID.
   */
  String email;

  /**
   * User's preferred alias when in a game or at a table.
   */
  String alias;

  /**
   * User's "real" name.
   */
  String name;

  /**
   * Phone number can be useful when organizing a remote game.
   */
  String phone;
}
