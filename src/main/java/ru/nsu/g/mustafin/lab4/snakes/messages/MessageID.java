package ru.nsu.g.mustafin.lab4.snakes.messages;

import java.util.Objects;

public class MessageID {
    public Long sequenceNumber;
    public Player player;

    public MessageID(Long sequenceNumber, Player player) {
        this.sequenceNumber = sequenceNumber;
        this.player = player;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        MessageID messageID = (MessageID) o;
        return this.sequenceNumber.equals(messageID.sequenceNumber) &&
                this.player.equals(messageID.player);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.sequenceNumber, this.player);
    }
}
