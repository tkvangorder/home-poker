import {
  Component,
  AfterViewInit,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  Input,
  ViewChild,
  ElementRef,
  NgZone,
} from '@angular/core';
import Phaser from 'phaser';
import { PhaserBridgeService } from './phaser-bridge.service';
import { BootScene } from './scenes/boot.scene';
import { PokerTableScene } from './scenes/poker-table.scene';
import { TableState, PlayerState } from '../../game/game-state.service';

@Component({
  selector: 'app-phaser-table',
  standalone: true,
  template: '<div #gameContainer class="w-full h-full"></div>',
  styles: [':host { display: block; width: 100%; height: 100%; }'],
})
export class PhaserTableComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('gameContainer', { static: true }) gameContainer!: ElementRef<HTMLDivElement>;

  @Input({ required: true }) tableState!: TableState;
  @Input({ required: true }) players!: PlayerState[];
  @Input() currentUserId: string = '';

  private game: Phaser.Game | null = null;
  private bridge = new PhaserBridgeService();

  constructor(private ngZone: NgZone) {}

  ngAfterViewInit(): void {
    // Push initial state before Phaser starts
    this.bridge.updateTableState(this.tableState);
    this.bridge.updatePlayers(this.players);
    this.bridge.updateCurrentUserId(this.currentUserId);

    // Run Phaser outside Angular zone to avoid unnecessary change detection
    this.ngZone.runOutsideAngular(() => {
      const bridge = this.bridge;

      // Custom PokerTableScene that receives bridge via closure
      class WiredPokerTableScene extends PokerTableScene {
        override init(): void {
          super.init({ bridge });
        }
      }

      this.game = new Phaser.Game({
        type: Phaser.AUTO,
        parent: this.gameContainer.nativeElement,
        transparent: true,
        scale: {
          mode: Phaser.Scale.RESIZE,
          width: '100%',
          height: '100%',
        },
        scene: [BootScene, WiredPokerTableScene],
        audio: { noAudio: true },
        banner: false,
      });
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['tableState']) {
      this.bridge.updateTableState(this.tableState);
    }
    if (changes['players']) {
      this.bridge.updatePlayers(this.players);
    }
    if (changes['currentUserId']) {
      this.bridge.updateCurrentUserId(this.currentUserId);
    }
  }

  ngOnDestroy(): void {
    if (this.game) {
      this.game.destroy(true);
      this.game = null;
    }
    this.bridge.destroy();
  }
}
