package ru.nsu.g.mustafin.lab4.snakes.model;

import ru.nsu.g.mustafin.lab4.snakes.SnakesProto;

public class GamePlayer {
    public int id;
    public String name;
    public int score;
    public String stringAddress;
    public int port;
    public SnakesProto.NodeRole role;

    public GamePlayer(int id, String name, String address, int port, SnakesProto.NodeRole role) {
        this.id = id;
        this.name = name;
        this.stringAddress = address;
        this.port = port;
        this.role = role;
        this.score = 0;
    }

    public GamePlayer(int id, String name, int score, String stringAddress, int port, SnakesProto.NodeRole role) {
        this.id = id;
        this.name = name;
        this.score = score;
        this.stringAddress = stringAddress;
        this.port = port;
        this.role = role;
    }
}
