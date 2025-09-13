
package onitama;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Player {
    private String name;
    private Color pieceColor;
    private Card[] cards;





    public Player(String name, Color pieceColor, Card[] cards) {
        this.name = name;
        this.pieceColor = pieceColor;
        this.cards = cards;
    }

    public Player(String name, Color pieceColor, Card card1, Card card2) {
        this.name = name;
        this.pieceColor = pieceColor;
        this.cards = new Card[]{card1, card2};
    }

    public String getName() {
        return name;
    }

    public Color getPieceColor() {
        return pieceColor;
    }

    public Card[] getCards() {
        return cards;
    }

    protected void swapCard(Card oldCard, Card newCard) throws InvalidCardException{
        List<Card> Hand = new ArrayList<>(Arrays.asList(cards));
        if(!Hand.contains(oldCard)) throw new InvalidCardException("OldCard não está na mão do jogador");
        if (Hand.contains(newCard)) throw new InvalidCardException("NewCard já está na mão do jogador");

        int index = Hand.indexOf(oldCard);
        Hand.set(index, newCard);
        Card temp = newCard;
        newCard = oldCard;
        oldCard = temp;
        for (int i = 0; i < Hand.size(); i++) {
            Card card = Hand.get(i);
            if(card.equals(oldCard)){
                card = newCard;
                break;
            }
        }
        cards = Hand.toArray(new Card[Hand.size()]);
        //lembrar de salvar a tablecard antes de usar esse metodo
        //lembrar de mudar a tableCard dps de usar esse metodo
    }
}