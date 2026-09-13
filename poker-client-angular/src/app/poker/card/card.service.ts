import { Injectable, inject } from '@angular/core';
import { SvgLoaderService } from '../../lib/svg-loader.service';
import { Observable, map } from 'rxjs';
import { CardSuit, CardValue } from '../poker-models';

const VALUE_FILE_NAMES: Record<CardValue, string> = {
  [CardValue.TWO]: '2',
  [CardValue.THREE]: '3',
  [CardValue.FOUR]: '4',
  [CardValue.FIVE]: '5',
  [CardValue.SIX]: '6',
  [CardValue.SEVEN]: '7',
  [CardValue.EIGHT]: '8',
  [CardValue.NINE]: '9',
  [CardValue.TEN]: '10',
  [CardValue.JACK]: 'jack',
  [CardValue.QUEEN]: 'queen',
  [CardValue.KING]: 'king',
  [CardValue.ACE]: 'ace',
};

@Injectable({
  providedIn: 'root',
})
export class CardService {
  private svgLoader = inject(SvgLoaderService);

  getCardImage(
    cardValue: CardValue | 'hidden' | 'blank',
    cardSuit?: CardSuit
  ): Observable<SVGElement> {
    let card$: Observable<SVGElement>;
    if (cardValue === 'hidden') {
      card$ = this.svgLoader.loadSvg('/assets/cards/back_2.svg');
    } else if (cardValue === 'blank') {
      card$ = this.svgLoader.loadSvg('/assets/cards/blank_card.svg');
    } else if (!cardSuit) {
      throw new Error('Card suit must be provided');
    }
    const url = `/assets/cards/fronts/${cardSuit?.toLowerCase()}_${VALUE_FILE_NAMES[cardValue as CardValue]}.svg`;
    card$ = this.svgLoader.loadSvg(url);
    return card$.pipe(
      map((card) => {
        this.applyCardStyles(card);
        return card;
      })
    );
  }

  getTopCardImage(
    cardValue: CardValue | 'hidden' | 'blank',
    cardSuit?: CardSuit
  ): Observable<SVGElement> {
    return this.getCardImage(cardValue, cardSuit).pipe(
      map((card) => {
        card.setAttribute('viewBox', '-120 -168 240 100');
        card.setAttribute('viewPort', '-120 -168 240 100');
        card.setAttribute('preserveAspectRatio', 'xMidYMid meet');
        return card;
      })
    );
  }

  applyCardStyles(card: SVGElement) {
    card.style.width = '100%';
    card.style.height = '100%';
  }
}
