package org.homepoker.user;

import org.homepoker.model.user.User;

import java.util.Set;

/**
 * System users — synthetic identities used by server-internal listeners and commands that
 * need a non-null {@code userId()} but are not real registered users.
 *
 * <p>These constants are <strong>not persisted</strong> to the users collection and are
 * never returned by {@code UserManager}. The reserved ids use an underscore-bracketed
 * sentinel pattern that is not produced by registration; collision with a real user id is
 * deliberately implausible. As a defensive follow-up, callers that register users may want
 * to reject ids matching the reserved pattern explicitly.
 */
public final class SystemUsers {

  /** Reserved id for the EventRecorder's synthetic listener identity. */
  public static final String EVENT_RECORDER_ID = "__system_recorder__";

  /**
   * The synthetic user the EventRecorder runs as. No password, no real contact info, and
   * <strong>no roles</strong> — the recorder is a passive listener and never needs to be
   * treated as privileged. Granting a role here would be a footgun: any future code path
   * that branches on {@code user.roles().contains(...)} could silently include the recorder.
   */
  public static final User EVENT_RECORDER = User.builder()
      .id(EVENT_RECORDER_ID)
      .email("<internal>")
      .name("Event Recorder")
      .phone("")
      .roles(Set.of())
      .build();

  private SystemUsers() {
    // Utility class.
  }
}
