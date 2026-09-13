import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ClientGameMessage, UserMessageEvent } from '../../game/game-events';

type DisplayMessage = ClientGameMessage | UserMessageEvent;

@Component({
  selector: 'app-messages-panel',
  imports: [CommonModule],
  templateUrl: './messages-panel.component.html',
})
export class MessagesPanelComponent {
  @Input() messages: DisplayMessage[] = [];

  isShowdown(msg: DisplayMessage): boolean {
    return msg.eventType === 'game-message' && (msg as ClientGameMessage).kind === 'showdown';
  }

  isAction(msg: DisplayMessage): boolean {
    return msg.eventType === 'game-message' && (msg as ClientGameMessage).kind === 'action';
  }

  getMessageClass(msg: DisplayMessage): string {
    if (msg.eventType === 'user-message') {
      switch (msg.severity) {
        case 'ERROR': return 'text-error';
        case 'WARNING': return 'text-warning';
      }
    }
    return '';
  }
}
