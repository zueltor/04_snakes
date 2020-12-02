package ru.nsu.g.mustafin.lab4.snakes;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import ru.nsu.g.mustafin.lab4.snakes.SnakesProto.Direction;
import ru.nsu.g.mustafin.lab4.snakes.SnakesProto.NodeRole;
import ru.nsu.g.mustafin.lab4.snakes.SnakesProto.GameState.Snake.SnakeState;
import ru.nsu.g.mustafin.lab4.snakes.messages.MessageController;
import ru.nsu.g.mustafin.lab4.snakes.model.Coordinates;
import ru.nsu.g.mustafin.lab4.snakes.model.GameConfig;
import ru.nsu.g.mustafin.lab4.snakes.model.GamePlayer;
import ru.nsu.g.mustafin.lab4.snakes.model.GameState;

public class GamePanel extends JPanel implements ActionListener {
    private static int UNITS_X;
    private static int UNITS_Y;
    private static int UNIT_SIZE = 25;
    private static int SCREEN_WIDTH;
    private static int SCREEN_HEIGHT;
    private static int GAME_UNITS;
    private static int DELAY;
    private static float SNAKE_PART_TO_APPLE_CHANCE;
    private static int STATIC_APPLES_ON_FIELD;
    private static float APPLES_PER_PLAYER;
    private final MessageController controller;
    private static final int SCOREBOARD_WIDTH = 200;
    private JPanel scoreboardPanel = new JPanel();
    private JList<String> list = new JList<>();
    private DefaultTableModel tableModel;
    private JButton watchButton = new JButton("Watch");
    private JButton restartButton = new JButton("Restart");
    private boolean isMaster;
    private String name;
    private int playerId;
    private final GameConfig config;
    public GameState getGameState() {
        return this.gameState;
    }
    private GameState gameState;
    private final Unit[][] field;
    private Timer timer;

    public GamePanel(MessageController controller, GameConfig config,String name,boolean isMaster) {
        this.name=name;
        this.config = config;
        UNITS_X= this.config.width;
        UNITS_Y= this.config.height;
        SCREEN_HEIGHT = UNITS_Y * UNIT_SIZE;
        SCREEN_WIDTH = UNITS_X * UNIT_SIZE;
        System.out.println("Before "+SCREEN_HEIGHT+" "+SCREEN_WIDTH);
        if(SCREEN_HEIGHT>1000||SCREEN_WIDTH>1000){
            UNIT_SIZE=Math.min(1000/UNITS_Y,1000/UNITS_X);
            System.out.println("U size "+UNIT_SIZE);
        }
        SCREEN_HEIGHT = UNITS_Y * UNIT_SIZE;
        SCREEN_WIDTH = UNITS_X * UNIT_SIZE;
        System.out.println("After "+SCREEN_HEIGHT+" "+SCREEN_WIDTH);
        GAME_UNITS = UNITS_X * UNITS_Y;
        DELAY= this.config.state_delay_ms;
        this.timer =new Timer(DELAY, this);
        STATIC_APPLES_ON_FIELD = this.config.food_static;
        SNAKE_PART_TO_APPLE_CHANCE= this.config.dead_food_prob;
        APPLES_PER_PLAYER= this.config.food_per_player;

        this.field = new Unit[UNITS_X][UNITS_Y];

        this.gameState = new GameState(this.config);
        this.scoreboardPanel.setLayout(new BorderLayout());
        this.scoreboardPanel.add(new JScrollPane(this.list), BorderLayout.NORTH);
        JTable table = new JTable();
        this.scoreboardPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        this.tableModel = (DefaultTableModel) table.getModel();
        this.tableModel.addColumn("Name");
        this.tableModel.addColumn("Score");
        table.setFocusable(false);
        this.scoreboardPanel.setBackground(Color.black);
        var buttonsPanel = new JPanel(new GridLayout(1, 2));
        buttonsPanel.add(this.restartButton);
        buttonsPanel.add(this.watchButton);
        this.restartButton.addActionListener(l -> {
            System.out.println("IS MASTER "+this.isMaster);
            if(this.isMaster){
                System.out.println("Master respawn");
                int masterId= this.gameState.getMasterId();
                this.gameState.players.get(masterId).score=0;
                for(var snake: this.gameState.snakes){
                    if(snake.id==masterId){
                        snake.state = SnakeState.ZOMBIE;
                    }
                }

                this.spawnSnake(masterId);

                //this.gameState.snakes.add(new Snake(masterId,name));
            }else {
                var currentRole= this.gameState.players.get(this.playerId).role;
                var newRole=(currentRole==NodeRole.VIEWER)?NodeRole.NORMAL:currentRole;
                System.out.println("Chel respawn "+ this.gameState.players.get(this.playerId).role);
                controller.sendRoleChangeMsg(null,null,newRole);
            }
            this.requestFocus();
        });
        this.watchButton.addActionListener(l -> {
            if(isMaster){

            }else {
                controller.sendRoleChangeMsg(null,null,NodeRole.VIEWER);
            }
            this.requestFocus();
        });
        this.scoreboardPanel.add(buttonsPanel, BorderLayout.SOUTH);
        this.setPreferredSize(new Dimension(SCREEN_WIDTH + SCOREBOARD_WIDTH, SCREEN_HEIGHT));
        this.setBackground(Color.black);
        this.isMaster = isMaster;
        this.setFocusable(true);

        this.controller = controller;
        this.setLayout(new BorderLayout(10, 10));
        this.addKeyListener(new SnakeKeyAdapter());
        this.scoreboardPanel.setPreferredSize(new Dimension(SCOREBOARD_WIDTH, 100));
        this.list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        //sidePanel.add(scoreboardPanel);
        //add(sidePanel,BorderLayout.EAST);

        this.add(this.scoreboardPanel, BorderLayout.EAST);
        if (isMaster) {
            this.playerId = 1;
            controller.setId(this.playerId);
            this.newApple();
            this.gameState.players.put(this.playerId, new GamePlayer(this.playerId, this.name, "", controller.getPort(), NodeRole.MASTER));
            this.spawnSnake(this.playerId);
            //this.gameState.snakes.add(new Snake(this.playerId, ""));
            //snakes.get(0).alive = true;
           // idCounter++;
            //snakes.add(new Snake(idCounter++,10, 10));


            this.startMasterGame();
        } else {
            // startNormalGame();
        }
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public int getPlayerId() {
        return this.playerId;
    }

    private void spawnSnake(int masterId) {
        var coords = this.findNewPosition();
        if (coords.size() < 2) {
            System.out.println("Ne poshlo");
        }
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
        this.gameState.snakes.add(new Snake(masterId, SnakeState.ALIVE, direction, coords));
    }

    public void removePlayer(int id){
        this.gameState.players.remove(id);
        for (var snake: this.gameState.snakes){
            if(snake.id==id){
                snake.state=SnakeState.ZOMBIE;
            }
        }
    }

    public void stop() {
        this.timer.stop();
    }

    public void changePlayerRole(int senderId, NodeRole role) {
        var player= this.gameState.players.get(senderId);
        var previousRole=player.role;
        player.role=role;
        System.out.println(previousRole+" -> "+role+" for id = "+senderId);
        if(role==NodeRole.VIEWER){
            for(var snake: this.gameState.snakes){
                if(snake.id==senderId){
                        snake.state = SnakeState.ZOMBIE;
                }
            }
            return;
        }
        player.score=0;
        if(previousRole==NodeRole.VIEWER){
            this.spawnSnake(senderId);
            //this.gameState.snakes.add(new Snake(senderId,player.name));
            System.out.println("Size "+ this.gameState.snakes.size());
        } else if(previousRole==role){ //todo check
            for(var snake: this.gameState.snakes){
                if(snake.id==senderId){
                    snake.state = SnakeState.ZOMBIE;
                }
            }
            this.spawnSnake(senderId);
        }

    }

    enum Unit {
        empty, snakePart, apple
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public void update() {
        this.repaint();
    }

    public void setSnakeDirection(int id, Direction direction) {
        var snakes=new ArrayList<Snake>();

        for (var s : this.gameState.snakes) {
            if (s.id == id) {
                snakes.add(s);
            }
        }

        for(var snake:snakes){
            if(snake.state==SnakeState.ZOMBIE){
                continue;
            }
            System.out.println(snakes.size()+" "+ this.gameState.snakes.size()+" Setting direction " + id+" "+snake.id + " to " + direction);
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

    public static class Snake {
        public int id;
        public final int[] x = new int[GAME_UNITS];
        public final int[] y = new int[GAME_UNITS];
        public int bodyParts = 2;
        public SnakeState state = SnakeState.ALIVE;
        public Direction direction = Direction.RIGHT;
        public Direction oldDirection;
        public boolean dead = false;
        public boolean expandOnNextState = false;
        public ArrayList<Coordinates> snakeOffsets = new ArrayList<>();

        public Snake(int id) {
            this.id = id;
        }

        public Snake(int id, SnakeState state, Direction direction, ArrayList<Coordinates> snakeOffsets) {
            this.id = id;
            this.state = state;
            this.direction = direction;
            this.snakeOffsets = snakeOffsets;
            this.setSnakeUnits();
        }

        public void setSnakeUnits() {
            this.x[0] = this.snakeOffsets.get(0).x;
            this.y[0] = this.snakeOffsets.get(0).y;
            int count = 1;
            int prev_x = this.x[0];
            int prev_y = this.y[0];
            for (int i = 1; i < this.snakeOffsets.size(); i++) {
                int deltax = this.snakeOffsets.get(i).x;
                int deltay = this.snakeOffsets.get(i).y;
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
                    int new_x = Math.floorMod(prev_x + incrementx, UNITS_X);
                    deltax -= incrementx;
                    int new_y = Math.floorMod(prev_y + incrementy, UNITS_Y);
                    deltay -= incrementy;
                    this.x[count] = new_x;
                    this.y[count] = new_y;
                    prev_x = new_x;
                    prev_y = new_y;
                    count++;
                }
            }

            this.bodyParts = count;
        }

        public void setSnakeCoordinates() {
            this.snakeOffsets.clear();
            if (this.dead) {
                return;
            }
            this.snakeOffsets.add(new Coordinates(this.x[0], this.y[0]));
            int i = 1;
            int currentX = this.x[0];
            int currentY = this.y[0];
            int delta;
            int mult;
            while (i < this.bodyParts) {
                if (currentX == this.x[i]) {
                    delta = 0;
                    mult = 1;
                    if ((Math.abs(currentY - this.y[i]) == 1 && currentY > this.y[i]) ||
                            (Math.abs(currentY - this.y[i]) > 1 && currentY < this.y[i])) {
                        mult = -1;
                    }
                    var coord = new Coordinates(0, 0);
                    while (i < this.bodyParts && currentX == this.x[i]) {
                        delta++;
                        i++;
                    }
                    coord.y = mult * delta;
                    this.snakeOffsets.add(coord);
                    currentX = this.x[i - 1];
                    currentY = this.y[i - 1];
                } else {
                    delta = 0;
                    mult = 1;
                    if ((Math.abs(currentX - this.x[i]) == 1 && currentX > this.x[i]) ||
                            (Math.abs(currentX - this.x[i]) > 1 && currentX < this.x[i])) {
                        mult = -1;
                    }
                    //todo
                    var coord = new Coordinates(0, 0);
                    while (i < this.bodyParts && currentY == this.y[i]) {
                        delta++;
                        i++;
                    }
                    coord.x = mult * delta;
                    this.snakeOffsets.add(coord);
                    currentX = this.x[i - 1];
                    currentY = this.y[i - 1];
                }
            }
        }

        public Snake(int id, int x, int y) {
            this.id = id;
            Arrays.fill(this.x, x);
            Arrays.fill(this.y, y);
            this.bodyParts = 5;
        }
    }

    ArrayList<Coordinates> findNewPosition(){
        this.updateField();
        var snakeCoordinates=new ArrayList<Coordinates>();
        for(int x=0;x<UNITS_X;x++){
            for (int y=0;y<UNITS_Y;y++){
                boolean goodPosition=true;
                if(this.field[x][y].equals(Unit.empty)){
                    outer:
                    for(int i=-2;i<=2;i++) {
                        for (int j = -2; j <= 2; j++) {
                            int xx=Math.floorMod(x+i,UNITS_X);
                            int yy=Math.floorMod(y+j,UNITS_Y);
                            System.out.print(this.field[x][y].ordinal());
                            if(this.field[xx][yy]==Unit.snakePart){
                                goodPosition=false;
                                System.out.println("F");
                                break outer;
                            }
                        }
                        System.out.println();
                    }
                    System.out.println();
                } else{
                    continue;
                }
                if(goodPosition){
                    var checkAppleCoords=new ArrayList<Coordinates>(){
                        {
                            this.add(new Coordinates(-1,0));
                            this.add(new Coordinates(0,1));
                            this.add(new Coordinates(1,0));
                            this.add(new Coordinates(0,-1));}
                    };
                    var possibleBodyCoords=new ArrayList<Coordinates>();
                    for(var coord:checkAppleCoords){
                        int xx=Math.floorMod(x+coord.x,UNITS_X);
                        int yy=Math.floorMod(y+coord.y,UNITS_Y);
                        if(this.field[xx][yy]==Unit.empty){
                            possibleBodyCoords.add(coord);
                        }
                    }
                    int size=possibleBodyCoords.size();
                    if(size>0) {
                        int i = ThreadLocalRandom.current().nextInt(size);
                        snakeCoordinates.add(new Coordinates(x,y));
                        System.out.println("Head "+x+" "+y);
                        snakeCoordinates.add(possibleBodyCoords.get(i));
                        System.out.println("Delta "+possibleBodyCoords.get(i).x+" "+possibleBodyCoords.get(i).y);
                        return snakeCoordinates;
                    }

                }
            }
        }
        return snakeCoordinates;
    }

    public int addNewPlayer(String name, String address, int port, NodeRole role) {
        System.out.println("add new player");
        int maxId=0;
        for(var snake: this.gameState.snakes){
            if(maxId<snake.id){
                maxId=snake.id;
            }
        }
        for(var player: this.gameState.players.values()){
            if (maxId<player.id){
                maxId=player.id;
            }
        }
        maxId++;
        int id = maxId;
        var coords= this.findNewPosition();
        if(coords.size()<2){
            System.out.println("Ne poshlo");
            return -1;
        }
        var dx=coords.get(1).x;
        var dy=coords.get(1).y;
        Direction direction;
        if(dx<0){
            direction=Direction.RIGHT;
        } else if (dx>0){
            direction=Direction.LEFT;
        } else if(dy<0){
            direction=Direction.DOWN;
        } else{
            direction=Direction.UP;
        }


        this.gameState.players.put(id, new GamePlayer(id, name, address, port, role));
        this.gameState.snakes.add(new Snake(id, SnakeState.ALIVE,direction,coords));
       // idCounter++;
        return id;
    }

    public void startMasterGame() {
        this.timer.start();
    }

    public void becomeMaster(int id){
        this.isMaster =true;
        System.out.println("is master = "+ this.isMaster);
        this.gameState.players.get(id).role=NodeRole.MASTER;
        this.timer.start();
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        this.draw(g);
    }

    public void draw(Graphics g) {
        this.tableModel.setRowCount(0);
        ArrayList<Vector<String>> rows = new ArrayList<>();
        for (var snake : this.gameState.snakes) {
            if(snake.state!=SnakeState.ZOMBIE) {
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
        this.gameState.apples.forEach(apple -> {
            g.fillOval(apple.x * UNIT_SIZE, apple.y * UNIT_SIZE, UNIT_SIZE, UNIT_SIZE);
        });
        this.gameState.snakes.forEach(snake -> {
            Color headColor;
            Color bodyColor;
            for (int i = 0; i < snake.bodyParts; i++) {
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
                    g.fillRect(snake.x[i] * UNIT_SIZE, snake.y[i] * UNIT_SIZE, UNIT_SIZE, UNIT_SIZE);
                    if (snake.state!=SnakeState.ZOMBIE&& this.gameState.players.containsKey(snake.id) && this.gameState.players.get(snake.id).role == NodeRole.MASTER) {
                        g.setColor(Color.white);
                        g.fillRect(snake.x[i] * UNIT_SIZE + UNIT_SIZE / 4, snake.y[i] * UNIT_SIZE + UNIT_SIZE / 4, UNIT_SIZE / 2, UNIT_SIZE / 2);
                    } else if (snake.state!=SnakeState.ZOMBIE&& this.gameState.players.containsKey(snake.id) && this.gameState.players.get(snake.id).role == NodeRole.DEPUTY) {
                        g.setColor(Color.blue);
                        g.fillRect(snake.x[i] * UNIT_SIZE + UNIT_SIZE / 4, snake.y[i] * UNIT_SIZE + UNIT_SIZE / 4, UNIT_SIZE / 2, UNIT_SIZE / 2);
                    }
                } else {
                    g.setColor(bodyColor);
                    g.fillRect(snake.x[i] * UNIT_SIZE, snake.y[i] * UNIT_SIZE, UNIT_SIZE, UNIT_SIZE);
                }
            }
        });
    }

    public void newApple() {
        while (this.gameState.apples.size() < STATIC_APPLES_ON_FIELD+ this.gameState.players.size()*APPLES_PER_PLAYER) {
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
            for (int i = snake.bodyParts - 1; i >= 0; i--) {
                this.field[snake.x[i]][snake.y[i]] = Unit.snakePart;
            }
            emptyUnitsCount.addAndGet(-snake.bodyParts);
        });
        return emptyUnitsCount;
    }

    public void move() {
        this.gameState.snakes.forEach(snake -> {
            for (int i = snake.bodyParts - 1; i > 0; i--) {
                snake.x[i] = snake.x[i - 1];
                snake.y[i] = snake.y[i - 1];
            }

            switch (snake.direction) {
                case UP:
                    snake.y[0] = Math.floorMod(snake.y[0] - 1, UNITS_Y);
                    break;
                case DOWN:
                    snake.y[0] = Math.floorMod(snake.y[0] + 1, UNITS_Y);
                    break;
                case LEFT:
                    snake.x[0] = Math.floorMod(snake.x[0] - 1, UNITS_X);
                    break;
                case RIGHT:
                    snake.x[0] = Math.floorMod(snake.x[0] + 1, UNITS_X);
                    break;
            }
        });
    }

    public void checkApple() {
        this.gameState.snakes.forEach(snake -> {
            boolean ateApple = this.gameState.apples.removeIf(apple ->
                    (snake.x[0] == apple.x) && (snake.y[0] == apple.y)
            );
            if (ateApple) {
                snake.expandOnNextState = true;
                if (snake.state != SnakeState.ZOMBIE && this.gameState.players.containsKey(snake.id)) {
                    this.gameState.players.get(snake.id).score++; //= snake.applesEaten;
                }
                this.newApple();
            }
        });
    }

    public void checkCollisions() {
        //checks if head collides with body
        this.gameState.snakes.forEach(snake -> {
            outer:
            for (Snake snake2 : this.gameState.snakes) {
                if (snake != snake2) {
                    for (int i = 0; i < snake2.bodyParts; i++) {
                        if ((snake.x[0] == snake2.x[i]) && (snake.y[0] == snake2.y[i])) {
                            //snake.alive = false;
                            snake.dead = true;
                            //snake2.applesEaten++;
                            if (this.gameState.players.containsKey(snake2.id)) {
                                this.gameState.players.get(snake2.id).score++;// = snake2.applesEaten;
                            }
                            break outer;
                        }
                    }
                } else {
                    for (int i = snake.bodyParts - 1; i > 0; i--) {
                        if ((snake.x[0] == snake.x[i]) && (snake.y[0] == snake.y[i])) {
                            //snake.alive = false;
                            snake.dead = true;
                            break outer;
                        }
                    }
                }
            }
        });

        this.gameState.snakes.forEach(snake -> {
            if (snake.dead) {
                for (int i = snake.bodyParts - 1; i > 0; i--) {
                    final int number = ThreadLocalRandom.current().nextInt(100);
                    if (number < SNAKE_PART_TO_APPLE_CHANCE*100) {
                        this.gameState.apples.add(new Coordinates(snake.x[i], snake.y[i]));
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
                snake.bodyParts++;
            }
        }
        this.move();
        this.checkApple();
        this.checkCollisions();
        for (var snake : this.gameState.snakes) {
            snake.setSnakeCoordinates();
        }
        this.controller.sendGameState(this.gameState.nextState());//(new GameState(gameState.snakes, apples, players, config));
        this.repaint();

    }

    class SnakeKeyAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (GamePanel.this.isMaster) {
                int masterId= GamePanel.this.gameState.getMasterId(); //todo properly
                var snakes=new ArrayList<Snake>();
                for(var snake: GamePanel.this.gameState.snakes){
                    if(snake.id==masterId&& snake.state!=SnakeState.ZOMBIE){
                        snakes.add(snake);
                    }
                }
                for(var snake:snakes){
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT:
                            if (snake.oldDirection != SnakesProto.Direction.RIGHT) {
                                snake.direction = SnakesProto.Direction.LEFT;
                            }
                            break;
                        case KeyEvent.VK_RIGHT:
                            if (snake.oldDirection != SnakesProto.Direction.LEFT) {
                                snake.direction = SnakesProto.Direction.RIGHT;
                            }
                            break;
                        case KeyEvent.VK_UP:
                            if (snake.oldDirection != SnakesProto.Direction.DOWN) {
                                snake.direction = SnakesProto.Direction.UP;
                            }
                            break;
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
                    case KeyEvent.VK_LEFT:
                        direction = SnakesProto.Direction.LEFT;
                        break;
                    case KeyEvent.VK_RIGHT:
                        direction = SnakesProto.Direction.RIGHT;
                        break;
                    case KeyEvent.VK_UP:
                        direction = SnakesProto.Direction.UP;
                        break;
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

    public void infoBox(String infoMessage, String titleBar)
    {
        JOptionPane.showMessageDialog(null, infoMessage, "InfoBox: " + titleBar, JOptionPane.INFORMATION_MESSAGE);
    }

    public void close(){
        JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
    }
}
