import { Card, CardSuit, CardValue } from '../../../poker/poker-models';

const SUIT_NAMES: Record<CardSuit, string> = {
  [CardSuit.HEART]: 'hearts',
  [CardSuit.DIAMOND]: 'diamonds',
  [CardSuit.CLUB]: 'clubs',
  [CardSuit.SPADE]: 'spades',
};

const VALUE_NAMES: Record<CardValue, string> = {
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


export const CARD_BACK_KEY = 'card_back';

export function getCardTextureKey(card: Card): string {
  return `card_${SUIT_NAMES[card.suit]}_${VALUE_NAMES[card.value]}`;
}

export function getCardAssetPath(card: Card): string {
  return `assets/cards/fronts/${SUIT_NAMES[card.suit]}_${VALUE_NAMES[card.value]}.svg`;
}

export function getCardBackAssetPath(): string {
  return 'assets/cards/back_1.svg';
}

export interface CardAssetEntry {
  key: string;
  path: string;
}

export function getAllCardAssets(): CardAssetEntry[] {
  const entries: CardAssetEntry[] = [];
  for (const suit of Object.values(CardSuit)) {
    for (const value of Object.values(CardValue)) {
      const card: Card = { suit, value };
      entries.push({
        key: getCardTextureKey(card),
        path: getCardAssetPath(card),
      });
    }
  }
  entries.push({ key: CARD_BACK_KEY, path: getCardBackAssetPath() });
  return entries;
}
