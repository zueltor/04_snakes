package ru.nsu.g.mustafin.lab4.snakes.messages;

import ru.nsu.g.mustafin.lab4.snakes.model.GameConfig;

import java.net.InetSocketAddress;
import java.util.Objects;

public class Game {
    public InetSocketAddress inetSocketAddress;
    public GameConfig config;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        Game game = (Game) o;
        return Objects.equals(this.inetSocketAddress, game.inetSocketAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.inetSocketAddress);
    }
}
