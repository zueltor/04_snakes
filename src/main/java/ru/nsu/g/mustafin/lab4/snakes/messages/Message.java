package ru.nsu.g.mustafin.lab4.snakes.messages;

public class Message {
    public Player player;
    public byte[] bytes;

    public Message(Player player, byte[] bytes) {
        this.player = player;
        this.bytes = bytes;
    }
}
