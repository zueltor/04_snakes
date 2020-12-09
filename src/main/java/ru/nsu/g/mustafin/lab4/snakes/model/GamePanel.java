package ru.nsu.g.mustafin.lab4.snakes.model;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import ru.nsu.g.mustafin.lab4.snakes.SnakesProto;
import ru.nsu.g.mustafin.lab4.snakes.SnakesProto.Direction;
import ru.nsu.g.mustafin.lab4.snakes.SnakesProto.NodeRole;
import ru.nsu.g.mustafin.lab4.snakes.SnakesProto.GameState.Snake.SnakeState;
import ru.nsu.g.mustafin.lab4.snakes.messages.MessageController;

public class GamePanel extends JPanel implements ActionListener {
    private static int UNITS_X;
    private static int UNITS_Y;
    private static int UNIT_SIZE = 25;
    private static float SNAKE_PART_TO_APPLE_CHANCE;
    private static int STATIC_APPLES_ON_FIELD;
    private static float APPLES_PER_PLAYER;
    private final MessageController controller;
    private static final int SCOREBOARD_WIDTH = 200;
    private DefaultTableModel tableModel;
    private boolean isMaster;
    private int playerId;
    private GameState gameState;
    private final Unit[][] field;
    private Timer timer;

    public GamePanel(MessageController controller, GameConfig config, String name, boolean isMaster) {
        UNITS_X = config.width;
        UNITS_Y = config.height;
        Snake.UNITS_X=UNITS_X;
        Snake.UNITS_Y=UNITS_Y;
        int SCREEN_HEIGHT = UNITS_Y * UNIT_SIZE;
        int SCREEN_WIDTH = UNITS_X * UNIT_SIZE;
        if (SCREEN_HEIGHT > 1000 || SCREEN_WIDTH > 1000) {
            UNIT_SIZE = Math.min(1000 / UNITS_Y, 1000 / UNITS_X);
        }
        SCREEN_HEIGHT = UNITS_Y * UNIT_SIZE;
        SCREEN_WIDTH = UNITS_X * UNIT_SIZE;
        int DELAY = config.state_delay_ms;
        this.timer = new Timer(DELAY, this);
        STATIC_APPLES_ON_FIELD = config.food_static;
        SNAKE_PART_TO_APPLE_CHANCE = config.dead_food_prob;
        APPLES_PER_PLAYER = config.food_per_player;
        this.field = new Unit[UNITS_X][UNITS_Y];
        this.gameState = new GameState(config);
        JPanel scoreboardPanel = new JPanel();
        scoreboardPanel.setLayout(new BorderLayout());
        JList<String> list = new JList<>();
        scoreboardPanel.add(new JScrollPane(list), BorderLayout.NORTH);
        JTable table = new JTable();
        scoreboardPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        this.tableModel = (DefaultTableModel) table.getModel();
        this.tableModel.addColumn("Name");
        this.tableModel.addColumn("Score");
        table.setFocusable(false);
        scoreboardPanel.setBackground(Color.black);
        var buttonsPanel = new JPanel(new GridLayout(1, 2));
        JButton restartButton = new JButton("Restart");
        buttonsPanel.add(restartButton);
        JButton watchButton = new JButton("Watch");
        buttonsPanel.add(watchButton);
        restartButton.addActionListener(l -> {
            if (this.isMaster) {
                int masterId = this.gameState.getMasterId();
                this.gameState.players.get(masterId).score = 0;
                for (var snake : this.gameState.snakes) {
                    if (snake.id == masterId) {
                        snake.state = SnakeState.ZOMBIE;
                    }
                }
                if(!this.spawnSnake(masterId)){
                    this.infoBox("No space for spawn","Error");
                }
            } else {
                var currentRole = this.gameState.players.get(this.playerId).role;
                var newRole = (currentRole == NodeRole.VIEWER) ? NodeRole.NORMAL : currentRole;
                controller.sendRoleChangeMsg(null, null, newRole);
            }
            this.requestFocus();
        });
        watchButton.addActionListener(l -> {
            if (!isMaster) {
                controller.sendRoleChangeMsg(null, null, NodeRole.VIEWER);
            }
            this.requestFocus();
        });
        scoreboardPanel.add(buttonsPanel, BorderLayout.SOUTH);
        this.setPreferredSize(new Dimension(SCREEN_WIDTH + SCOREBOARD_WIDTH, SCREEN_HEIGHT));
        this.setBackground(Color.black);
        this.isMaster = isMaster;
        this.setFocusable(true);

        this.controller = controller;
        this.setLayout(new BorderLayout(10, 10));
        this.addKeyListener(new SnakeKeyAdapter());
        scoreboardPanel.setPreferredSize(new Dimension(SCOREBOARD_WIDTH, 100));
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);

        this.add(scoreboardPanel, BorderLayout.EAST);
        if (isMaster) {
            this.playerId = 1;
            controller.setId(this.playerId);
            this.newApple();
            this.gameState.players.put(this.playerId, new GamePlayer(this.playerId, name, "", controller.getPort(), NodeRole.MASTER));
            this.spawnSnake(this.playerId);
            this.startMasterGame();
        }
    }

    public GameState getGameState() {
        return this.gameState;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public int getPlayerId() {
        return this.playerId;
    }

    private boolean spawnSnake(int id) {
        var coords = this.findNewPosition();
        if (coords.size() < 2) {
            return false;
        }

        Direction direction = this.getDirection(coords);
        this.gameState.snakes.add(new Snake(id, SnakeState.ALIVE, direction, coords));
        return true;
    }

    private Direction getDirection(ArrayList<Coordinates> coords) {
        var dx = coords.get(1).x;
        var dy = coords.get(1).y;
        Direction direction;
        if (dx < 0) {
            direction = Direction.RIGHT;
        } else if (dx > 0) {
            direction = Direction.LEFT;
        } else if (dy < 0) {
            direction = Direction.DOWN;
        } else {
            direction = Direction.UP;
        }
        return direction;
    }

    public void removePlayer(int id) {
        this.gameState.players.remove(id);
        for (var snake : this.gameState.snakes) {
            if (snake.id == id) {
                snake.state = SnakeState.ZOMBIE;
            }
        }
    }

    public void stop() {
        this.timer.stop();
    }

    public void changePlayerRole(int senderId, NodeRole role) {
        var player = this.gameState.players.get(senderId);
        var previousRole = player.role;
        player.role = role;
        if (role == NodeRole.VIEWER) {
            for (var snake : this.gameState.snakes) {
                if (snake.id == senderId) {
                    snake.state = SnakeState.ZOMBIE;
                }
            }
            return;
        }
        player.score = 0;
        if (previousRole == NodeRole.VIEWER) {
            if(!this.spawnSnake(senderId)){
                this.infoBox("No space for spawn","Error");
            }
        } else if (previousRole == role) {
            for (var snake : this.gameState.snakes) {
                if (snake.id == senderId) {
                    snake.state = SnakeState.ZOMBIE;
                }
            }
            if(!this.spawnSnake(senderId)){
                this.infoBox("No space for spawn","Error");
            }
        }

    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public void update() {
        this.repaint();
    }

    public void setSnakeDirection(int id, Direction direction) {
        var snakes = new ArrayList<Snake>();
        for (var s : this.gameState.snakes) {
            if (s.id == id) {
                snakes.add(s);
            }
        }
        for (var snake : snakes) {
            if (snake.state == SnakeState.ZOMBIE) {
                continue;
            }
            switch (direction) {
                case LEFT:
                    if (snake.oldDirection != Direction.RIGHT) {
                        snake.direction = Direction.LEFT;
                    }
                    break;
                case RIGHT:
                    if (snake.oldDirection != Direction.LEFT) {
                        snake.direction = Direction.RIGHT;
                    }
                    break;
                case UP:
                    if (snake.oldDirection != Direction.DOWN) {
                        snake.direction = Direction.UP;
                    }
                    break;
                case DOWN:
                    if (snake.oldDirection != Direction.UP) {
                        snake.direction = Direction.DOWN;
                    }
                    break;
            }
        }
    }

    private ArrayList<Coordinates> findNewPosition() {
        this.updateField();
        var snakeCoordinates = new ArrayList<Coordinates>();
        for (int x = 0; x < UNITS_X; x++) {
            for (int y = 0; y < UNITS_Y; y++) {
                boolean goodPosition = true;
                if (this.field[x][y].equals(Unit.empty)) {
                    outer:
                    for (int i = -2; i <= 2; i++) {
                        for (int j = -2; j <= 2; j++) {
                            int xx = Math.floorMod(x + i, UNITS_X);
                            int yy = Math.floorMod(y + j, UNITS_Y);
                            if (this.field[xx][yy] == Unit.snakePart) {
                                goodPosition = false;
                                break outer;
                            }
                        }
                    }
                } else {
                    continue;
                }
                if (goodPosition) {
                    var checkAppleCoords = new ArrayList<Coordinates>() {
                        {
                            this.add(new Coordinates(-1, 0));
                            this.add(new Coordinates(0, 1));
                            this.add(new Coordinates(1, 0));
                            this.add(new Coordinates(0, -1));
                        }
                    };
                    var possibleBodyCoords = new ArrayList<Coordinates>();
                    for (var coord : checkAppleCoords) {
                        int xx = Math.floorMod(x + coord.x, UNITS_X);
                        int yy = Math.floorMod(y + coord.y, UNITS_Y);
                        if (this.field[xx][yy] == Unit.empty) {
                            possibleBodyCoords.add(coord);
                        }
                    }
                    int size = possibleBodyCoords.size();
                    if (size > 0) {
                        int i = ThreadLocalRandom.current().nextInt(size);
                        snakeCoordinates.add(new Coordinates(x, y));
                        snakeCoordinates.add(possibleBodyCoords.get(i));
                        return snakeCoordinates;
                    }

                }
            }
        }
        return snakeCoordinates;
    }

    public int addNewPlayer(String name, String address, int port, NodeRole role) {
        int maxId = 0;
        for (var snake : this.gameState.snakes) {
            if (maxId < snake.id) {
                maxId = snake.id;
            }
        }
        for (var player : this.gameState.players.values()) {
            if (maxId < player.id) {
                maxId = player.id;
            }
        }
        maxId++;
        int id = maxId;
        var coords = this.findNewPosition();
        if (coords.size() < 2) {
            return -1;
        }
        var direction = this.getDirection(coords);
        this.gameState.players.put(id, new GamePlayer(id, name, address, port, role));
        this.gameState.snakes.add(new Snake(id, SnakeState.ALIVE, direction, coords));
        return id;
    }

    public void startMasterGame() {
        this.timer.start();
    }

    public void becomeMaster(int id) {
        this.isMaster = true;
        this.gameState.players.get(id).role = NodeRole.MASTER;
        this.timer.start();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        this.draw(g);
    }

    private void draw(Graphics g) {
        this.tableModel.setRowCount(0);
        ArrayList<Vector<String>> rows = new ArrayList<>();
        for (var snake : this.gameState.snakes) {
            if (snake.state != SnakeState.ZOMBIE) {
                Vector<String> row = new Vector<>();
                String name = this.gameState.players.get(snake.id).name;//snake.name;
                String score = Integer.toString(this.gameState.players.get(snake.id).score);//Integer.toString(snake.applesEaten);
                row.add(name);
                row.add(score);
                rows.add(row);
            }
        }
        rows.sort((a, b) -> Integer.parseInt(b.elementAt(1)) - Integer.parseInt(a.elementAt(1)));
        for (var row : rows) {
            this.tableModel.addRow(row);
        }
        g.setColor(Color.red);
        this.gameState.apples.forEach(apple -> g.fillOval(apple.x * UNIT_SIZE, apple.y * UNIT_SIZE, UNIT_SIZE, UNIT_SIZE));
        this.gameState.snakes.forEach(snake -> {
            Color headColor;
            Color bodyColor;
            for (int i = 0; i < snake.coordinates.size(); i++) {
                var coord = snake.coordinates.get(i);
                if (snake.id == this.playerId && snake.state != SnakeState.ZOMBIE) {
                    headColor = Color.green;
                    bodyColor = new Color(45, 180, 0);
                } else if (snake.state == SnakeState.ZOMBIE) {
                    headColor = new Color(180, 180, 180);
                    bodyColor = new Color(60, 60, 60);
                } else {
                    headColor = new Color(180, 45, 0);
                    bodyColor = new Color(90, 45, 0);
                }
                if (i == 0) {
                    g.setColor(headColor);
                    g.fillRect(coord.x * UNIT_SIZE, coord.y * UNIT_SIZE, UNIT_SIZE, UNIT_SIZE);
                    if (snake.state != SnakeState.ZOMBIE && this.gameState.players.containsKey(snake.id) && this.gameState.players.get(snake.id).role == NodeRole.MASTER) {
                        g.setColor(Color.white);
                        g.fillRect(coord.x * UNIT_SIZE + UNIT_SIZE / 4, coord.y * UNIT_SIZE + UNIT_SIZE / 4, UNIT_SIZE / 2, UNIT_SIZE / 2);
                    } else if (snake.state != SnakeState.ZOMBIE && this.gameState.players.containsKey(snake.id) && this.gameState.players.get(snake.id).role == NodeRole.DEPUTY) {
                        g.setColor(Color.blue);
                        g.fillRect(coord.x * UNIT_SIZE + UNIT_SIZE / 4, coord.y * UNIT_SIZE + UNIT_SIZE / 4, UNIT_SIZE / 2, UNIT_SIZE / 2);
                    }
                } else {
                    g.setColor(bodyColor);
                    g.fillRect(coord.x * UNIT_SIZE, coord.y * UNIT_SIZE, UNIT_SIZE, UNIT_SIZE);
                }
            }
        });
    }

    public void newApple() {
        while (this.gameState.apples.size() < STATIC_APPLES_ON_FIELD + this.gameState.players.size() * APPLES_PER_PLAYER) {
            AtomicInteger emptyUnitsCount = this.updateField();
            int randomUnit = ThreadLocalRandom.current().nextInt(emptyUnitsCount.get());
            int currentUnit = 0;
            for (int i = 0; i < UNITS_X; i++) {
                for (int j = 0; j < UNITS_Y; j++) {
                    if (this.field[i][j] == Unit.empty && currentUnit == randomUnit) {
                        this.gameState.apples.add(new Coordinates(i, j));
                    }
                    currentUnit++;
                }
            }
        }
    }

    private AtomicInteger updateField() {
        AtomicInteger emptyUnitsCount = new AtomicInteger(UNITS_X * UNITS_Y);
        for (int i = 0; i < UNITS_X; i++) {
            for (int j = 0; j < UNITS_Y; j++) {
                this.field[i][j] = Unit.empty;
            }
        }
        this.gameState.apples.forEach(apple -> {
            this.field[apple.x][apple.y] = Unit.apple;
            emptyUnitsCount.decrementAndGet();
        });
        this.gameState.snakes.forEach(snake -> {
            for (var coord : snake.coordinates) {
                this.field[coord.x][coord.y] = Unit.snakePart;
            }
            emptyUnitsCount.addAndGet(-snake.coordinates.size());
        });
        return emptyUnitsCount;
    }

    public void move() {
        this.gameState.snakes.forEach(snake -> {
            snake.tail = snake.coordinates.remove(snake.coordinates.size() - 1);
            var head = new Coordinates(snake.coordinates.get(0).x, snake.coordinates.get(0).y);

            switch (snake.direction) {
                case UP:
                    head.y = Math.floorMod(head.y - 1, UNITS_Y);
                    break;
                case DOWN:
                    head.y = Math.floorMod(head.y + 1, UNITS_Y);
                    break;
                case LEFT:
                    head.x = Math.floorMod(head.x - 1, UNITS_X);
                    break;
                case RIGHT:
                    head.x = Math.floorMod(head.x + 1, UNITS_X);
                    break;
            }
            snake.coordinates.add(0, head);
        });
    }

    private void checkApple() {
        this.gameState.snakes.forEach(snake -> {
            var head = snake.coordinates.get(0);
            boolean ateApple = this.gameState.apples.removeIf(apple ->
                    (head.x == apple.x) && (head.y == apple.y)
            );
            if (ateApple) {
                snake.expandOnNextState = true;
                if (snake.state != SnakeState.ZOMBIE && this.gameState.players.containsKey(snake.id)) {
                    this.gameState.players.get(snake.id).score++;
                }
                this.newApple();
            }
        });
    }

    private void checkCollisions() {
        this.gameState.snakes.forEach(snake1 -> {
            outer:
            for (Snake snake2 : this.gameState.snakes) {
                var head = snake1.coordinates.get(0);
                if (snake1 != snake2) {

                    for (var coord : snake2.coordinates) {
                        if ((head.x == coord.x) && (head.y == coord.y)) {
                            snake1.dead = true;
                            if (this.gameState.players.containsKey(snake2.id)) {
                                this.gameState.players.get(snake2.id).score++;
                            }
                            break outer;
                        }
                    }
                } else {
                    for (int i = 1; i < snake1.coordinates.size(); i++) {
                        var body = snake1.coordinates.get(i);
                        if ((head.x == body.x) && (head.y == body.y)) {
                            snake1.dead = true;
                            break outer;
                        }
                    }
                }
            }
        });

        this.gameState.snakes.forEach(snake -> {
            if (snake.dead) {
                for (var coord : snake.coordinates) {
                    final int number = ThreadLocalRandom.current().nextInt(100);
                    if (number < SNAKE_PART_TO_APPLE_CHANCE * 100) {
                        this.gameState.apples.add(coord);
                    }
                }
            }
        });
        this.gameState.snakes.removeIf(snake -> snake.dead);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (this.gameState.snakes.isEmpty()) {
            return;
        }
        for (var snake : this.gameState.snakes) {
            snake.oldDirection = snake.direction;
            if (snake.expandOnNextState) {
                snake.expandOnNextState = false;
                snake.coordinates.add(snake.tail);
            }
        }
        this.move();
        this.checkApple();
        this.checkCollisions();
        for (var snake : this.gameState.snakes) {
            snake.setSnakeOffsets();
        }
        this.controller.sendGameState(this.gameState.nextState());
        this.repaint();
    }

    public void infoBox(String infoMessage, String titleBar) {
        JOptionPane.showMessageDialog(null, infoMessage, titleBar, JOptionPane.INFORMATION_MESSAGE);
    }

    public void close() {
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }

    class SnakeKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (GamePanel.this.isMaster) {
                int masterId = GamePanel.this.gameState.getMasterId();
                var snakes = new ArrayList<Snake>();
                for (var snake : GamePanel.this.gameState.snakes) {
                    if (snake.id == masterId && snake.state != SnakeState.ZOMBIE) {
                        snakes.add(snake);
                    }
                }
                for (var snake : snakes) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_A:
                        case KeyEvent.VK_LEFT:
                            if (snake.oldDirection != SnakesProto.Direction.RIGHT) {
                                snake.direction = SnakesProto.Direction.LEFT;
                            }
                            break;
                        case KeyEvent.VK_D:
                        case KeyEvent.VK_RIGHT:
                            if (snake.oldDirection != SnakesProto.Direction.LEFT) {
                                snake.direction = SnakesProto.Direction.RIGHT;
                            }
                            break;
                        case KeyEvent.VK_W:
                        case KeyEvent.VK_UP:
                            if (snake.oldDirection != SnakesProto.Direction.DOWN) {
                                snake.direction = SnakesProto.Direction.UP;
                            }
                            break;
                        case KeyEvent.VK_S:
                        case KeyEvent.VK_DOWN:
                            if (snake.oldDirection != SnakesProto.Direction.UP) {
                                snake.direction = SnakesProto.Direction.DOWN;
                            }
                            break;
                    }
                }
            } else {
                Direction direction = null;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_A:
                    case KeyEvent.VK_LEFT:
                        direction = SnakesProto.Direction.LEFT;
                        break;
                    case KeyEvent.VK_D:
                    case KeyEvent.VK_RIGHT:
                        direction = SnakesProto.Direction.RIGHT;
                        break;
                    case KeyEvent.VK_W:
                    case KeyEvent.VK_UP:
                        direction = SnakesProto.Direction.UP;
                        break;
                    case KeyEvent.VK_S:
                    case KeyEvent.VK_DOWN:
                        direction = SnakesProto.Direction.DOWN;
                        break;
                }
                if (direction != null) {
                    GamePanel.this.controller.sendSteerMessage(direction);
                }
            }

        }
    }
}
