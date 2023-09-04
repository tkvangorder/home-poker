package org.homepoker.event;

public record ApplicationError(String message, String details) implements Event {
}
