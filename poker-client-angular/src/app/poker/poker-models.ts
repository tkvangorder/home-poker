export enum CardValue {
  TWO = 'TWO',
  THREE = 'THREE',
  FOUR = 'FOUR',
  FIVE = 'FIVE',
  SIX = 'SIX',
  SEVEN = 'SEVEN',
  EIGHT = 'EIGHT',
  NINE = 'NINE',
  TEN = 'TEN',
  JACK = 'JACK',
  QUEEN = 'QUEEN',
  KING = 'KING',
  ACE = 'ACE',
}

export enum CardSuit {
  HEART = 'HEART',
  DIAMOND = 'DIAMOND',
  CLUB = 'CLUB',
  SPADE = 'SPADE',
}

export interface Card {
  value: CardValue;
  suit: CardSuit;
}
