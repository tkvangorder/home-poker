package org.homepoker.recording;

import org.homepoker.model.user.User;
import org.homepoker.model.user.UserRole;

import java.util.Set;

/**
 * System users — synthetic identities used by server-internal listeners and commands that
 * need a non-null {@code userId()} but are not real registered users.
 *
 * <p>These constants are <strong>not persisted</strong> to the users collection and are
 * never returned by {@code UserManager}. The reserved ids cannot collide with real user ids
 * because real ids are validated as email-format on registration.
 */
public final class SystemUsers {

  /** Reserved id for the {@link EventRecorder}'s synthetic listener identity. */
  public static final String EVENT_RECORDER_ID = "__system_recorder__";

  /**
   * The synthetic user the {@link EventRecorder} runs as. Has no password, no real contact
   * info, and the {@link UserRole#ADMIN} role only as a structural placeholder — this user
   * cannot log in.
   */
  public static final User EVENT_RECORDER = User.builder()
      .id(EVENT_RECORDER_ID)
      .email("<internal>")
      .name("Event Recorder")
      .phone("")
      .roles(Set.of(UserRole.ADMIN))
      .build();

  private SystemUsers() {
    // Utility class.
  }
}
