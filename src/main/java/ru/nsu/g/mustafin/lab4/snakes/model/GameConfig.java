package ru.nsu.g.mustafin.lab4.snakes.model;

public class GameConfig {
    public int width = 25;
    public int height = 25;
    public int food_static = 1;
    public float food_per_player = 1;
    public int state_delay_ms = 250;
    public float dead_food_prob = 0.1f;
    public int ping_delay_ms = 100;
    public int node_timeout_ms = 800;

    public GameConfig() {
    }

    public GameConfig(int width, int height, int food_static, float food_per_player, int state_delay_ms, float dead_food_prob, int ping_delay_ms, int node_timeout_ms) {
        this.width = width;
        this.height = height;
        this.food_static = food_static;
        this.food_per_player = food_per_player;
        this.state_delay_ms = state_delay_ms;
        this.dead_food_prob = dead_food_prob;
        this.ping_delay_ms = ping_delay_ms;
        this.node_timeout_ms = node_timeout_ms;
    }
}
