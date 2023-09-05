package org.homepoker.event;


import org.homepoker.model.Message;

public record MessageReceived(Message message) implements Event {
}
