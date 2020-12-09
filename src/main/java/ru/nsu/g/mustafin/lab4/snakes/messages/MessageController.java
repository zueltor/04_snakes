package ru.nsu.g.mustafin.lab4.snakes.messages;

import com.google.protobuf.InvalidProtocolBufferException;
import ru.nsu.g.mustafin.lab4.snakes.model.GamePanel;
import ru.nsu.g.mustafin.lab4.snakes.model.Snake;
import ru.nsu.g.mustafin.lab4.snakes.SnakesProto;
import ru.nsu.g.mustafin.lab4.snakes.gui.MenuFrame;
import ru.nsu.g.mustafin.lab4.snakes.model.Coordinates;
import ru.nsu.g.mustafin.lab4.snakes.model.GameConfig;
import ru.nsu.g.mustafin.lab4.snakes.model.GamePlayer;
import ru.nsu.g.mustafin.lab4.snakes.model.GameState;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class MessageController implements AutoCloseable {
    private final int port;
    private final InetAddress group;
    private final List<Thread> threads = new ArrayList<>();
    private final Thread announcementReceiver;
    private final Thread announcementSender;
    private final Map<Long, Message> sentMessages = new ConcurrentHashMap<>();
    private final Map<MessageID, Long> receivedMessages = new ConcurrentHashMap<>();
    private final ArrayList<Long> messagesToRemove = new ArrayList<>();
    private static final int MOVE_MESSAGES_DELAY = 400;
    private static int PING_DELAY_MS = 100;
    private static int NODE_TIMEOUT_MS = 800;
    private final MenuFrame menuFrame;
    private GamePanel gamePanel;
    private static final int BUFFER_SIZE = 2048;
    private static final int MESSAGE_EXPIRATION_TIME = 1000;
    private final MulticastSocket announcementSocket;
    private final MulticastSocket socket;
    private final ConcurrentHashMap<Player, Long> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Player> playersById = new ConcurrentHashMap<>();
    private long sequence_number = 1;
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private Player master;
    private int masterId;
    private int id = 0;
    private int currentStateOrder = 0;
    private long id_message_sequence_number;
    private SnakesProto.NodeRole myRole;
    private final ConcurrentHashMap<Game, Long> games;
    private GameState previousGameState;

    public MessageController(int port, String address, NetworkInterface netif, MenuFrame menuFrame) throws IOException {
        this.port = port;
        this.group = InetAddress.getByName(address);
        this.socket = new MulticastSocket();
        this.games = new ConcurrentHashMap<>();
        this.announcementReceiver = new Thread(new AnnouncementReceiver());
        this.announcementSender = new Thread(new AnnouncementSender());
        this.threads.add(new Thread(new Sender()));
        this.threads.add(new Thread(new Receiver()));
        this.threads.add(new Thread(new MessageUpdater()));
        this.threads.add(new Thread(new PingMessageSender()));
        this.threads.add(new Thread(new PlayersChecker()));
        this.announcementSocket = new MulticastSocket(port);
        var isa = new InetSocketAddress(this.group, port);
        this.announcementSocket.joinGroup(isa, netif);
        this.socket.setNetworkInterface(netif);
        this.menuFrame = menuFrame;
    }

    public void setGamePanel(GamePanel gamePanel) {
        this.gamePanel = gamePanel;
    }

    public void hostGame(int ping_delay_ms, int node_timeout_ms) {
        PING_DELAY_MS = ping_delay_ms;
        NODE_TIMEOUT_MS = node_timeout_ms;
        this.myRole = SnakesProto.NodeRole.MASTER;
        this.announcementSender.start();
        for (var thread : this.threads) {
            thread.start();
        }
    }

    public void findGames() {
        this.announcementReceiver.start();
    }

    public void joinGame(InetSocketAddress address, String name, int ping_delay_ms, int node_timeout_ms) {
        PING_DELAY_MS = ping_delay_ms;
        NODE_TIMEOUT_MS = node_timeout_ms;
        this.master = new Player(address);
        this.players.put(this.master, System.currentTimeMillis());
        var bytes = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(this.sequence_number).setJoin(SnakesProto.GameMessage.JoinMsg.newBuilder()
                        .setName(name)
                        .build())
                .build().toByteArray();
        this.id_message_sequence_number = this.sequence_number++;
        this.messageQueue.add(new Message(null, bytes));
        for (var thread : this.threads) {
            thread.start();
        }

    }

    public void sendSteerMessage(SnakesProto.Direction direction) {
        var bytes = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(this.sequence_number++)
                .setSenderId(this.id)
                .setSteer(SnakesProto.GameMessage.SteerMsg.newBuilder()
                        .setDirection(direction).build())
                .build().toByteArray();
        this.messageQueue.add(new Message(null, bytes));
    }

    public int getPort() {
        return this.socket.getPort();
    }

    public void sendRoleChangeMsg(Player player, SnakesProto.NodeRole receiverRole, SnakesProto.NodeRole senderRole) {
        int receiverId = (player == null) ? this.masterId : player.getId();
        receiverRole = (receiverRole == null) ? SnakesProto.NodeRole.MASTER : receiverRole;
        var bytes = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(this.sequence_number++)
                .setRoleChange(SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                        .setSenderRole(senderRole)
                        .setReceiverRole(receiverRole)
                        .build())
                .setSenderId(this.id)
                .setReceiverId(receiverId)
                .build().toByteArray();
        this.messageQueue.add(new Message(player, bytes));
    }

    public void sendGameState(GameState gameState) {
        SnakesProto.GameConfig conf = this.buildGameConfig(gameState);
        var gamePlayers = this.buildGamePlayers(gameState);
        var gameStateBuilder = SnakesProto.GameState.newBuilder();
        for (var apple : gameState.apples) {
            gameStateBuilder.addFoods(SnakesProto.GameState.Coord.newBuilder()
                    .setX(apple.x)
                    .setY(apple.y)
                    .build());
        }
        for (var snake : gameState.snakes) {
            var snakeBuilder = SnakesProto.GameState.Snake.newBuilder();
            for (var coord : snake.offsets) {
                snakeBuilder.addPoints(SnakesProto.GameState.Coord.newBuilder()
                        .setX(coord.x)
                        .setY(coord.y)
                        .build());
            }
            var builtSnake = snakeBuilder.setState(snake.state)
                    .setPlayerId(snake.id)
                    .setHeadDirection(snake.direction)
                    .build();
            gameStateBuilder.addSnakes(builtSnake);
        }

        var builtGameState = gameStateBuilder.setStateOrder(gameState.state_order)
                .setConfig(conf)
                .setPlayers(gamePlayers)
                .build();
        for (var player : gameState.players.values()) {

            if (this.id != player.id) {
                var bytes = SnakesProto.GameMessage.newBuilder().setState(SnakesProto.GameMessage.StateMsg.newBuilder()
                        .setState(builtGameState).build()).setMsgSeq(this.sequence_number++).build().toByteArray();
                this.messageQueue.add(new Message(this.playersById.get(player.id), bytes));
            }
        }
    }

    private SnakesProto.GameConfig buildGameConfig(GameState gameState) {
        return SnakesProto.GameConfig.newBuilder()
                .setWidth(gameState.config.width)
                .setHeight(gameState.config.height)
                .setFoodStatic(gameState.config.food_static)
                .setFoodPerPlayer(gameState.config.food_per_player)
                .setDeadFoodProb(gameState.config.dead_food_prob)
                .setPingDelayMs(gameState.config.ping_delay_ms)
                .setStateDelayMs(gameState.config.state_delay_ms)
                .setNodeTimeoutMs(gameState.config.node_timeout_ms).build();
    }

    private void findNewDeputy() {
        for (var p : MessageController.this.players.keySet()) {
            if (p.getRole() == SnakesProto.NodeRole.NORMAL) {
                p.setRole(SnakesProto.NodeRole.DEPUTY);
                MessageController.this.sendRoleChangeMsg(p, SnakesProto.NodeRole.DEPUTY, MessageController.this.myRole);
                MessageController.this.playersById.get(p.getId()).setRole(SnakesProto.NodeRole.DEPUTY);
                MessageController.this.gamePanel.changePlayerRole(p.getId(), SnakesProto.NodeRole.DEPUTY);
                break;
            }
        }
    }

    private SnakesProto.GamePlayers buildGamePlayers(GameState gameState) {
        var playersBuilder = SnakesProto.GamePlayers.newBuilder();
        for (var player : gameState.players.values()) {
            playersBuilder.addPlayers(SnakesProto.GamePlayer.newBuilder()
                    .setName(player.name)
                    .setId(player.id)
                    .setIpAddress(player.stringAddress)
                    .setPort(player.port)
                    .setRole(player.role)
                    .setScore(player.score)
                    .build());
        }
        return playersBuilder.build();
    }

    private void setNewMaster(GamePlayer player) {
        var masterSocketAddress = new InetSocketAddress(player.stringAddress, player.port);
        var newPlayer = new Player(masterSocketAddress);
        newPlayer.setRole(SnakesProto.NodeRole.MASTER);
        newPlayer.setId(player.id);
        MessageController.this.players.clear();
        MessageController.this.players.put(newPlayer, System.currentTimeMillis());
        MessageController.this.master = newPlayer;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public void close() {
        this.announcementSender.interrupt();
        this.announcementReceiver.interrupt();
        for (var thread : this.threads) {
            thread.interrupt();
        }
        this.socket.close();
        this.announcementSocket.close();
    }

    class PlayersChecker implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(PING_DELAY_MS);
                } catch (final InterruptedException e) {
                    return;
                }

                Player timedOutPlayer = null;
                for (var p : MessageController.this.players.entrySet()) {
                    if (System.currentTimeMillis() - p.getValue() > NODE_TIMEOUT_MS) {
                        timedOutPlayer = p.getKey();
                        break;
                    }
                }

                if (timedOutPlayer != null) {
                    MessageController.this.players.remove(timedOutPlayer);
                    int playerId = timedOutPlayer.getId();
                    MessageController.this.playersById.remove(playerId);
                    MessageController.this.gamePanel.removePlayer(playerId);

                    //b
                    if (MessageController.this.myRole == SnakesProto.NodeRole.MASTER && timedOutPlayer.getRole() == SnakesProto.NodeRole.DEPUTY) {
                        MessageController.this.players.remove(timedOutPlayer);
                        MessageController.this.findNewDeputy();
                        //c
                    } else if (MessageController.this.myRole == SnakesProto.NodeRole.DEPUTY && timedOutPlayer.getRole() == SnakesProto.NodeRole.MASTER) {
                        MessageController.this.myRole = SnakesProto.NodeRole.MASTER;
                        MessageController.this.players.clear();
                        MessageController.this.playersById.clear();
                        int deputyId = 0;
                        for (var player : MessageController.this.previousGameState.players.values()) {
                            if (MessageController.this.id == player.id) {
                                continue;
                            }
                            if (player.role == SnakesProto.NodeRole.NORMAL) {
                                deputyId = player.id;
                            }
                            var inetSocketAddress = new InetSocketAddress(player.stringAddress, player.port);
                            var newPlayer = new Player(inetSocketAddress);
                            newPlayer.setRole(player.role);
                            newPlayer.setId(player.id);
                            MessageController.this.players.put(newPlayer, System.currentTimeMillis());
                            MessageController.this.playersById.put(newPlayer.getId(), newPlayer);
                        }

                        if (deputyId != 0) {
                            for (var player : MessageController.this.players.keySet()) {
                                if (deputyId == player.getId()) {
                                    MessageController.this.sendRoleChangeMsg(MessageController.this.playersById.get(deputyId), SnakesProto.NodeRole.DEPUTY, MessageController.this.myRole);
                                    MessageController.this.playersById.get(deputyId).setRole(SnakesProto.NodeRole.DEPUTY);
                                    MessageController.this.gamePanel.changePlayerRole(deputyId, SnakesProto.NodeRole.DEPUTY);
                                } else {
                                    MessageController.this.sendRoleChangeMsg(player, player.getRole(), MessageController.this.myRole);
                                }
                            }
                        }

                        MessageController.this.announcementSender.start();
                        MessageController.this.gamePanel.becomeMaster(MessageController.this.id);

                        //a
                    } else if ((MessageController.this.myRole == SnakesProto.NodeRole.NORMAL || MessageController.this.myRole == SnakesProto.NodeRole.VIEWER) && timedOutPlayer.getRole() == SnakesProto.NodeRole.MASTER) {
                        boolean hasDeputy = false;
                        for (var player : MessageController.this.previousGameState.players.values()) {
                            if (MessageController.this.id == player.id) {
                                continue;
                            }
                            if (player.role == SnakesProto.NodeRole.DEPUTY) {
                                MessageController.this.setNewMaster(player);
                                hasDeputy = true;
                                break;
                            }
                        }
                        if (!hasDeputy) {
                            MessageController.this.gamePanel.infoBox("No deputy, closing game","Error");
                            MessageController.this.gamePanel.close();
                        }
                    }
                }
            }
        }
    }

    class MessageUpdater implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(MOVE_MESSAGES_DELAY);
                } catch (final InterruptedException e) {
                    return;
                }
                synchronized (MessageController.this.sentMessages) {
                    synchronized (MessageController.this.messagesToRemove) {
                        MessageController.this.sentMessages.keySet().removeIf(MessageController.this.messagesToRemove::contains);
                        MessageController.this.messagesToRemove.clear();
                    }

                    for (final var entry : MessageController.this.sentMessages.entrySet()) {
                        MessageController.this.messageQueue.add(entry.getValue());
                    }

                    MessageController.this.sentMessages.clear();
                }
                MessageController.this.receivedMessages.entrySet().removeIf(message -> System.currentTimeMillis() - message.getValue() > MESSAGE_EXPIRATION_TIME);
            }
        }
    }

    class Sender implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final Message message = MessageController.this.messageQueue.take();
                    InetSocketAddress socketAddress;
                    if (message.player == null) {
                        socketAddress = MessageController.this.master.getInetSocketAddress();
                    } else {
                        socketAddress = message.player.getInetSocketAddress();
                    }
                    final DatagramPacket packet = new DatagramPacket(message.bytes, message.bytes.length, socketAddress);
                    final var dummyPlayer = new Player(socketAddress);
                    if (!MessageController.this.players.containsKey(dummyPlayer)) {
                        continue;
                    }
                    MessageController.this.socket.send(packet);

                    final var protoMessage = SnakesProto.GameMessage.parseFrom(message.bytes);
                    if (!protoMessage.hasAck() && !protoMessage.hasAnnouncement()) {
                        final long seq = protoMessage.getMsgSeq();
                        synchronized (MessageController.this.sentMessages) {
                            MessageController.this.sentMessages.put(seq, message);
                        }
                    }
                } catch (final InterruptedException | IOException e) {
                    return;
                }
            }
        }
    }

    class Receiver implements Runnable {
        @Override
        public void run() {
            final byte[] buffer = new byte[BUFFER_SIZE];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    MessageController.this.socket.receive(packet);
                    /*int i = ThreadLocalRandom.current().nextInt(100);
                    if (i < 30) {
                        final var message = SnakesProto.GameMessage.parseFrom(Arrays.copyOf(packet.getData(), packet.getLength()));
                        long seq = message.getMsgSeq();
                        System.out.println("Lost " + seq);
                        continue;
                    } else {
                    }*/
                    this.handleMessage(packet);
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void handleMessage(final DatagramPacket packet) throws InvalidProtocolBufferException {
            final var message = SnakesProto.GameMessage.parseFrom(Arrays.copyOf(packet.getData(), packet.getLength()));
            final var inetSocketAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
            final var dummyPlayer = new Player(inetSocketAddress);
            if (MessageController.this.players.containsKey(dummyPlayer)) {
                MessageController.this.players.put(dummyPlayer, System.currentTimeMillis());
            }
            long messageSequenceNumber = message.getMsgSeq();
            if (!message.hasAck() && !message.hasJoin()) {
                final var ack = SnakesProto.GameMessage.newBuilder()
                        .setAck(SnakesProto.GameMessage.AckMsg.newBuilder()
                                .build())
                        .setMsgSeq(messageSequenceNumber)
                        .build().toByteArray();

                MessageController.this.messageQueue.add(new Message(dummyPlayer, ack));
                var messageId = new MessageID(messageSequenceNumber, dummyPlayer);
                final var isDuplicateMessage = MessageController.this.receivedMessages.containsKey(messageId);
                if (!isDuplicateMessage) {
                    MessageController.this.receivedMessages.put(new MessageID(messageSequenceNumber, dummyPlayer), System.currentTimeMillis());
                } else {
                    return;
                }
            }
            if (message.hasJoin()) {
                var joinMessage = message.getJoin();
                SnakesProto.NodeRole role;
                boolean hasDeputyNode = false;
                for (var player : MessageController.this.players.keySet()) {
                    if (player.getRole().equals(SnakesProto.NodeRole.DEPUTY)) {
                        hasDeputyNode = true;
                        break;
                    }
                }

                if (hasDeputyNode) {
                    role = SnakesProto.NodeRole.NORMAL;
                } else {
                    role = SnakesProto.NodeRole.DEPUTY;
                }
                dummyPlayer.setRole(role);

                int receiver_id = MessageController.this.gamePanel.addNewPlayer(joinMessage.getName(), packet.getAddress().getHostAddress(), packet.getPort(), role);
                if (receiver_id == -1) {
                    final var err = SnakesProto.GameMessage.newBuilder()
                            .setMsgSeq(MessageController.this.sequence_number++)
                            .setError(SnakesProto.GameMessage.ErrorMsg.newBuilder()
                                    .setErrorMessage("Could not spawn snake")
                                    .build())
                            .build().toByteArray();
                    MessageController.this.messageQueue.add(new Message(dummyPlayer, err));
                }
                int sender_id = MessageController.this.gamePanel.getPlayerId();
                dummyPlayer.setId(receiver_id);
                MessageController.this.playersById.put(receiver_id, dummyPlayer);
                MessageController.this.players.put(dummyPlayer, System.currentTimeMillis());
                final var ack = SnakesProto.GameMessage.newBuilder().setAck(SnakesProto.GameMessage.AckMsg.newBuilder()
                        .build())
                        .setReceiverId(receiver_id)
                        .setSenderId(sender_id)
                        .setMsgSeq(message.getMsgSeq())
                        .build().toByteArray();
                MessageController.this.messageQueue.add(new Message(dummyPlayer, ack));
            } else if (message.hasSteer()) {
                var steerMessage = message.getSteer();
                var direction = steerMessage.getDirection();
                MessageController.this.gamePanel.setSnakeDirection(message.getSenderId(), direction);

            } else if (message.hasAck()) {
                if (MessageController.this.id_message_sequence_number == messageSequenceNumber) {
                    MessageController.this.id = message.getReceiverId();
                    MessageController.this.masterId = message.getSenderId();
                    MessageController.this.master.setId(MessageController.this.masterId);
                    MessageController.this.master.setRole(SnakesProto.NodeRole.MASTER);
                    MessageController.this.gamePanel.setPlayerId(MessageController.this.id);
                }
                synchronized (MessageController.this.messagesToRemove) {
                    MessageController.this.messagesToRemove.add(messageSequenceNumber); //MessageUpdater will remove messages and clear this array
                }
            } else if (message.hasState()) {
                var stateMessage = message.getState().getState();
                var state_order = stateMessage.getStateOrder();
                if (state_order < MessageController.this.currentStateOrder) {
                    return;
                }
                MessageController.this.currentStateOrder = state_order;
                var configMessage = stateMessage.getConfig();
                GameConfig config = new GameConfig(configMessage.getWidth(),
                        configMessage.getHeight(),
                        configMessage.getFoodStatic(),
                        configMessage.getFoodPerPlayer(),
                        configMessage.getStateDelayMs(),
                        configMessage.getDeadFoodProb(),
                        configMessage.getPingDelayMs(),
                        configMessage.getNodeTimeoutMs());
                var playersMessage = stateMessage.getPlayers();
                var players = new ConcurrentHashMap<Integer, GamePlayer>();
                for (int i = 0; i < playersMessage.getPlayersCount(); i++) {
                    var player = playersMessage.getPlayers(i);
                    players.put(player.getId(), new GamePlayer(player.getId(),
                            player.getName(),
                            player.getScore(),
                            player.getIpAddress(),
                            player.getPort(),
                            player.getRole()));
                }
                var applesList = stateMessage.getFoodsList();
                var snakesList = stateMessage.getSnakesList();
                ArrayList<Snake> snakes = new ArrayList<>();
                ArrayList<Coordinates> apples = new ArrayList<>();
                for (var apple : applesList) {
                    apples.add(new Coordinates(apple.getX(), apple.getY()));
                }
                for (var snake : snakesList) {
                    var coordList = snake.getPointsList();
                    var coordinates = new ArrayList<Coordinates>();
                    for (SnakesProto.GameState.Coord coord : coordList) {
                        coordinates.add(new Coordinates(coord.getX(), coord.getY()));
                    }
                    var _snake = new Snake(snake.getPlayerId(), snake.getState(), snake.getHeadDirection(), coordinates);
                    snakes.add(_snake);
                }
                var gameState = new GameState(snakes, apples, players, config, state_order);
                MessageController.this.myRole = players.get(MessageController.this.id).role;
                MessageController.this.gamePanel.setGameState(gameState);
                MessageController.this.gamePanel.update();
                MessageController.this.previousGameState = gameState;
            } else if (message.hasRoleChange()) {
                int senderId = message.getSenderId();
                var senderRole = message.getRoleChange().getSenderRole();
                var receiverRole = message.getRoleChange().getReceiverRole();
                if (senderRole == SnakesProto.NodeRole.MASTER) {
                    MessageController.this.myRole = receiverRole;
                    if (senderId != MessageController.this.masterId) {
                        var player = MessageController.this.previousGameState.players.get(senderId);
                        MessageController.this.setNewMaster(player);
                    }
                } else if (receiverRole == SnakesProto.NodeRole.MASTER) {
                    if (MessageController.this.playersById.get(senderId).getRole() == SnakesProto.NodeRole.DEPUTY && senderRole != SnakesProto.NodeRole.DEPUTY) {
                        MessageController.this.findNewDeputy();
                    } else if (MessageController.this.playersById.get(senderId).getRole() == SnakesProto.NodeRole.VIEWER && senderRole == SnakesProto.NodeRole.NORMAL) {
                        boolean hasDeputy = false;
                        for (var p : MessageController.this.players.keySet()) {
                            if (p.getRole() == SnakesProto.NodeRole.DEPUTY) {
                                hasDeputy = true;
                                break;
                            }
                        }
                        if (!hasDeputy) {
                            senderRole = SnakesProto.NodeRole.DEPUTY;
                        }
                    }
                    MessageController.this.playersById.get(senderId).setRole(senderRole);
                    MessageController.this.gamePanel.changePlayerRole(senderId, senderRole);
                }

            } else if (message.hasError()) {
                String error = message.getError().getErrorMessage();
                MessageController.this.gamePanel.infoBox(error, "Error");
                MessageController.this.gamePanel.close();
            } else if (!message.hasPing()) {
                System.err.println("Unknown msg");
            }
        }
    }

    class AnnouncementSender implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    var gameState = MessageController.this.gamePanel.getGameState();
                    var gameConfig = MessageController.this.buildGameConfig(gameState);
                    var playersBuilder = SnakesProto.GamePlayers.newBuilder();
                    for (var player : gameState.players.values()) {
                        var playerBuilder = SnakesProto.GamePlayer.newBuilder()
                                .setName(player.name)
                                .setId(player.id)
                                .setIpAddress(player.stringAddress)
                                .setPort(player.port)
                                .setRole(player.role)
                                .setScore(player.score);
                        if (player.id == MessageController.this.id) {
                            playerBuilder.setPort(MessageController.this.socket.getLocalPort());
                        }
                        playersBuilder.addPlayers(playerBuilder.build());
                    }
                    var gamePlayers = playersBuilder.build();
                    var announcementMsg = SnakesProto.GameMessage.newBuilder()
                            .setMsgSeq(1)
                            .setAnnouncement(SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                                    .setConfig(gameConfig)
                                    .setPlayers(gamePlayers)
                                    .setCanJoin(true)
                                    .build())
                            .build();
                    byte[] buf = announcementMsg.toByteArray();
                    final DatagramPacket packet = new DatagramPacket(buf, buf.length, MessageController.this.group, MessageController.this.port);
                    MessageController.this.socket.send(packet);

                    Thread.sleep(500);
                } catch (final IOException | InterruptedException e) {
                    return;
                }
            }
        }
    }

    class PingMessageSender implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(PING_DELAY_MS);
                    if (MessageController.this.myRole != SnakesProto.NodeRole.MASTER) {
                        break;
                    }
                    for (final var player : MessageController.this.players.entrySet()) {
                        if (System.currentTimeMillis() - player.getValue() > PING_DELAY_MS) {
                            final var aliveMessage = SnakesProto.GameMessage.newBuilder()
                                    .setMsgSeq(MessageController.this.sequence_number++)
                                    .setPing(SnakesProto.GameMessage.PingMsg.newBuilder()
                                            .build())
                                    .build().toByteArray();
                            MessageController.this.messageQueue.add(new Message(player.getKey(), aliveMessage));
                        }

                    }
                } catch (final InterruptedException e) {
                    return;
                }
            }
        }
    }

    class AnnouncementReceiver implements Runnable {
        @Override
        public void run() {
            final byte[] buffer = new byte[BUFFER_SIZE];
            final int UPDATE_GAMES_DELAY = 300;
            long current_time = System.currentTimeMillis();
            long next_check_time = UPDATE_GAMES_DELAY + current_time;
            long receive_timeout;
            boolean gameAdded = false;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final DatagramPacket packet;
                    current_time = System.currentTimeMillis();
                    if (next_check_time - current_time <= 0 || gameAdded) {
                        gameAdded = false;
                        MessageController.this.games.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > UPDATE_GAMES_DELAY * 10);
                        MessageController.this.menuFrame.updateGamesList(new ArrayList<>(MessageController.this.games.keySet()));
                        current_time = System.currentTimeMillis();
                        next_check_time = UPDATE_GAMES_DELAY + current_time;
                    } else {
                        receive_timeout = next_check_time - current_time;
                        MessageController.this.announcementSocket.setSoTimeout((int) receive_timeout);
                        packet = new DatagramPacket(buffer, buffer.length);
                        MessageController.this.announcementSocket.receive(packet);

                        try {
                            final var message = SnakesProto.GameMessage.parseFrom(Arrays.copyOf(packet.getData(), packet.getLength()));
                            if (message.hasAnnouncement()) {
                                var announcementMessage = message.getAnnouncement();
                                boolean hasMaster = false;
                                Game game = new Game();

                                if (announcementMessage.hasConfig()) {
                                    var configMessage = announcementMessage.getConfig();
                                    game.config = new GameConfig(configMessage.getWidth(),
                                            configMessage.getHeight(),
                                            configMessage.getFoodStatic(),
                                            configMessage.getFoodPerPlayer(),
                                            configMessage.getStateDelayMs(),
                                            configMessage.getDeadFoodProb(),
                                            configMessage.getPingDelayMs(),
                                            configMessage.getNodeTimeoutMs());
                                }

                                if (announcementMessage.hasPlayers()) {
                                    var players = announcementMessage.getPlayers();
                                    for (int i = 0; i < players.getPlayersCount(); i++) {
                                        var player = players.getPlayers(i);
                                        if (player.getRole() == SnakesProto.NodeRole.MASTER) {
                                            hasMaster = true;
                                            break;
                                        }
                                    }
                                    if (!hasMaster) {
                                        continue; //no master, no game
                                    }
                                    game.inetSocketAddress = new InetSocketAddress(packet.getAddress().getHostAddress(), packet.getPort());
                                    if (!MessageController.this.games.containsKey(game)) {
                                        gameAdded = true;
                                    }
                                    MessageController.this.games.put(game, System.currentTimeMillis());
                                }
                            }
                        } catch (InvalidProtocolBufferException e) {
                            System.err.println("Invalid protocol");
                            return;
                        }
                    }
                } catch (final SocketTimeoutException ignored) {
                } catch (IOException e) {
                    return;
                }
            }
        }
    }
}
