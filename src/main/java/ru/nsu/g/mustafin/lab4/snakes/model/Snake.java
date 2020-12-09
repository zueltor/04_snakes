package ru.nsu.g.mustafin.lab4.snakes.model;

import ru.nsu.g.mustafin.lab4.snakes.SnakesProto;
import ru.nsu.g.mustafin.lab4.snakes.model.Coordinates;

import java.util.ArrayList;

public class Snake {
    public static int UNITS_X;
    public static int UNITS_Y;
    public int id;
    public ArrayList<Coordinates> coordinates;
    public SnakesProto.GameState.Snake.SnakeState state = SnakesProto.GameState.Snake.SnakeState.ALIVE;
    public SnakesProto.Direction direction = SnakesProto.Direction.RIGHT;
    public SnakesProto.Direction oldDirection;
    public boolean dead = false;
    public boolean expandOnNextState = false;
    public Coordinates tail;
    public ArrayList<Coordinates> offsets = new ArrayList<>();

    public Snake(int id) {
        this.id = id;
    }

    public Snake(int id, SnakesProto.GameState.Snake.SnakeState state, SnakesProto.Direction direction, ArrayList<Coordinates> snakeOffsets) {
        this.id = id;
        this.state = state;
        this.direction = direction;
        this.offsets = snakeOffsets;
        this.coordinates = new ArrayList<>();
        this.setSnakeCoordinates();
    }

    public void setSnakeCoordinates() {
        this.coordinates.add(this.offsets.get(0));
        var previousCoordinate = this.coordinates.get(0);
        for (int i = 1; i < this.offsets.size(); i++) {
            int deltax = this.offsets.get(i).x;
            int deltay = this.offsets.get(i).y;
            int incrementx = 0;
            int incrementy = 0;
            if (deltax < 0) {
                incrementx = -1;
            } else if (deltax > 0) {
                incrementx = 1;
            } else if (deltay < 0) {
                incrementy = -1;
            } else if (deltay > 0) {
                incrementy = 1;
            }
            while (deltax != 0 || deltay != 0) {
                var bodyCoordinate = new Coordinates(Math.floorMod(previousCoordinate.x + incrementx, UNITS_X), Math.floorMod(previousCoordinate.y + incrementy, UNITS_Y));
                deltax -= incrementx;
                deltay -= incrementy;
                this.coordinates.add(bodyCoordinate);
                previousCoordinate = bodyCoordinate;
            }
        }
    }

    public void setSnakeOffsets() {
        this.offsets.clear();
        if (this.dead) {
            return;
        }

        int i = 1;
        var currentCoord = this.coordinates.get(0);
        this.offsets.add(currentCoord);
        int delta;
        int mult;

        while (i < this.coordinates.size()) {
            var nextCoord = this.coordinates.get(i);
            if (currentCoord.x == this.coordinates.get(i).x) {
                delta = 0;
                mult = 1;
                if ((Math.abs(currentCoord.y - nextCoord.y) == 1 && currentCoord.y > nextCoord.y) ||
                        (Math.abs(currentCoord.y - nextCoord.y) > 1 && currentCoord.y < nextCoord.y)) {
                    mult = -1;
                }
                var coord = new Coordinates(0, 0);
                while (i < this.coordinates.size() && currentCoord.x == this.coordinates.get(i).x) {
                    nextCoord = this.coordinates.get(i);
                    delta++;
                    i++;
                }
                coord.y = mult * delta;
                this.offsets.add(coord);
                currentCoord = nextCoord;
            } else {
                delta = 0;
                mult = 1;
                if ((Math.abs(currentCoord.x - nextCoord.x) == 1 && currentCoord.x > nextCoord.x) ||
                        (Math.abs(currentCoord.x - nextCoord.x) > 1 && currentCoord.x < nextCoord.x)) {
                    mult = -1;
                }
                var coord = new Coordinates(0, 0);
                while (i < this.coordinates.size() && currentCoord.y == this.coordinates.get(i).y) {
                    nextCoord = this.coordinates.get(i);
                    delta++;
                    i++;
                }
                coord.x = mult * delta;
                this.offsets.add(coord);
                currentCoord = nextCoord;
            }
        }
    }
}
