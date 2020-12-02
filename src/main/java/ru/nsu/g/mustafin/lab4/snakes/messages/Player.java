package ru.nsu.g.mustafin.lab4.snakes.messages;

import ru.nsu.g.mustafin.lab4.snakes.SnakesProto;

import java.net.InetSocketAddress;

public class Player {
    private final InetSocketAddress address;
    private SnakesProto.NodeRole role;

    public SnakesProto.NodeRole getRole() {
        return this.role;
    }

    public void setRole(SnakesProto.NodeRole role) {
        this.role = role;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    private int id;

    public Player(final InetSocketAddress address) {
        this.address = address;
    }

    public InetSocketAddress getInetSocketAddress() {
        return this.address;
    }

    @Override
    public int hashCode() {
        return this.address.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (obj.getClass() != this.getClass()) {
            return false;
        }
        final var neighbour = (Player) obj;
        return this.address.equals(neighbour.address);
    }
}
