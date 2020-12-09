package ru.nsu.g.mustafin.lab4.snakes.model;

import ru.nsu.g.mustafin.lab4.snakes.SnakesProto;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class GameState {
    public ArrayList<Snake> snakes;
    public ArrayList<Coordinates> apples;
    public ConcurrentHashMap<Integer, GamePlayer> players;
    public GameConfig config;
    public int state_order;

    public GameState(GameConfig config) {
        this.snakes = new ArrayList<>();
        this.apples = new ArrayList<>();
        this.players = new ConcurrentHashMap<>();
        this.config = config;
        this.state_order = 1;
    }

    public GameState(ArrayList<Snake> snakes, ArrayList<Coordinates> apples, ConcurrentHashMap<Integer, GamePlayer> players, GameConfig config, int state_order) {
        this.snakes = snakes;
        this.apples = apples;
        this.players = players;
        this.config = config;
        this.state_order = state_order;
    }

    public int getMasterId(){
        for (var player: this.players.values()){
            if(player.role== SnakesProto.NodeRole.MASTER){
                return player.id;
            }
        }
        return 0;
    }

    public GameState nextState() {
        this.state_order++;
        return this;
    }
}
