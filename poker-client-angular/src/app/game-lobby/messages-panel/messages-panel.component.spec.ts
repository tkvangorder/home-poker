import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessagesPanelComponent } from './messages-panel.component';
import { ClientGameMessage, UserMessageEvent } from '../../game/game-events';

function showdownMsg(message: string): ClientGameMessage {
  return {
    eventType: 'game-message',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: 'g1',
    message,
    kind: 'showdown',
  };
}

function actionMsg(message: string): ClientGameMessage {
  return {
    eventType: 'game-message',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: 'g1',
    message,
    kind: 'action',
  };
}

function plainInfoMsg(message: string): ClientGameMessage {
  return {
    eventType: 'game-message',
    timestamp: '2026-05-09T00:00:01Z',
    gameId: 'g1',
    message,
  };
}

describe('MessagesPanelComponent', () => {
  let fixture: ComponentFixture<MessagesPanelComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MessagesPanelComponent] });
    fixture = TestBed.createComponent(MessagesPanelComponent);
  });

  function render(messages: (ClientGameMessage | UserMessageEvent)[]): HTMLElement {
    fixture.componentInstance.messages = messages;
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('renders showdown messages with the showdown-row class and a WINNER prefix', () => {
    const el = render([showdownMsg('Alice won $0.50 (Pair of Aces)')]);
    const row = el.querySelector('.showdown-row');
    expect(row).not.toBeNull();
    expect(row!.textContent).toContain('WINNER');
    expect(row!.textContent).toContain('Alice won $0.50 (Pair of Aces)');
  });

  it('renders action messages with the action-row class and no special prefix', () => {
    const el = render([actionMsg('Bob folds')]);
    const row = el.querySelector('.action-row');
    expect(row).not.toBeNull();
    expect(row!.textContent).not.toContain('WINNER');
  });

  it('renders plain info messages without showdown or action classes', () => {
    const el = render([plainInfoMsg('Hand #1 started')]);
    expect(el.querySelector('.showdown-row')).toBeNull();
    expect(el.querySelector('.action-row')).toBeNull();
  });
});
