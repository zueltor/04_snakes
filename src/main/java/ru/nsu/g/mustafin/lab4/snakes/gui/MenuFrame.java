package ru.nsu.g.mustafin.lab4.snakes.gui;

import ru.nsu.g.mustafin.lab4.snakes.messages.Game;
import ru.nsu.g.mustafin.lab4.snakes.messages.MessageController;
import ru.nsu.g.mustafin.lab4.snakes.model.GameConfig;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MenuFrame extends JFrame {
    private JPanel content;
    private GameConfig config = new GameConfig();
    private ArrayList<Game> games = new ArrayList<>();
    private MessageController controller;
    private JPanel mainMenuPanel;
    private JPanel findHostsPanel;
    private JPanel hostPanel;
    private JLabel warningLabel;
    private JComboBox<NetworkInterface> networks = new JComboBox<>();
    private JTextField hostNameField = new JTextField("Player");
    private JTextField nameField = new JTextField("Player");
    DefaultTableModel tableModel;
    private JTable table;
    private MenuConfig menuConfig = new MenuConfig();

    static class MenuConfig {
        ArrayList<Pair> components = new ArrayList<>();

        public MenuConfig() {
            JSpinner width = new JSpinner(new SpinnerNumberModel(25, 10, 100, 1));
            JLabel label = new JLabel("Width");
            this.components.add(new Pair(label, width));
            JSpinner height = new JSpinner(new SpinnerNumberModel(15, 10, 100, 1));
            label = new JLabel("Height");
            this.components.add(new Pair(label, height));
            JSpinner foodStatic = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
            label = new JLabel("Food static");
            this.components.add(new Pair(label, foodStatic));
            JSpinner foodPerPlayer = new JSpinner(new SpinnerNumberModel(1.0, 0, 100, 1));
            label = new JLabel("Food per player");
            this.components.add(new Pair(label, foodPerPlayer));
            JSpinner stateDelayMs = new JSpinner(new SpinnerNumberModel(250, 1, 10000, 25));
            label = new JLabel("State delay ms");
            this.components.add(new Pair(label, stateDelayMs));
            JSpinner deadFoodProb = new JSpinner(new SpinnerNumberModel(0.1, 0, 1, 0.05));
            label = new JLabel("Dead food Probability");
            this.components.add(new Pair(label, deadFoodProb));
            JSpinner pingDelayMs = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 25));
            label = new JLabel("State delay ms");
            this.components.add(new Pair(label, pingDelayMs));
            JSpinner nodeTimeoutMs = new JSpinner(new SpinnerNumberModel(800, 1, 10000, 25));
            label = new JLabel("Node timeout ms");
            this.components.add(new Pair(label, nodeTimeoutMs));
        }

        static class Pair {
            public JLabel label;
            public JSpinner component;

            public Pair(JLabel label, JSpinner component) {
                this.label = label;
                this.component = component;
            }
        }
    }

    public MenuFrame() {
        this.initMainMenuPanel();
        this.initHostPanel();
        this.initFindPanel();
        this.content = new JPanel();
        this.content.add(this.mainMenuPanel);
        this.setSize(500, 350);

        this.add(this.content);
        this.setTitle("Snake");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
        this.setLocationRelativeTo(null);
    }

    private static List<NetworkInterface> getAvailableInterfaces() throws SocketException {
        Stream<NetworkInterface> networkInterface = NetworkInterface.networkInterfaces().filter(it -> {
            try {
                return it.isUp()
                        && !it.isLoopback()
                        && it.supportsMulticast();
            } catch (SocketException e) {
                return false;
            }
        });

        return networkInterface.collect(Collectors.toList());
    }

    public void initMainMenuPanel() {
        this.mainMenuPanel = new JPanel();
        JButton hostButton = new JButton("New game");
        JButton findHosts = new JButton("Find existing games");

        this.mainMenuPanel.setLayout(new GridLayout(4, 1));
        this.mainMenuPanel.add(new JLabel("Choose network interface:"));
        this.mainMenuPanel.add(this.networks);
        this.mainMenuPanel.add(hostButton);
        this.mainMenuPanel.add(findHosts);

        try {
            var availableInterfaces = getAvailableInterfaces();
            for (var it : availableInterfaces) {
                this.networks.addItem(it);
            }
        } catch (SocketException ignored) {
        }

        hostButton.addActionListener(l -> {
            try {
                this.controller = new MessageController(9192, "239.192.0.4", (NetworkInterface) Objects.requireNonNull(this.networks.getSelectedItem()), this);
            } catch (IOException e) {
                System.err.println("Could not create message controller");
                System.exit(0);
            }
            this.changeContent(this.hostPanel);
        });

        findHosts.addActionListener(l -> {
            try {
                this.controller = new MessageController(9192, "239.192.0.4", (NetworkInterface) Objects.requireNonNull(this.networks.getSelectedItem()), this);
            } catch (IOException e) {
                System.err.println("Could not create message controller");
                System.exit(0);
            }
            this.controller.findGames();
            this.changeContent(this.findHostsPanel);
        });
    }

    public void initFindPanel() {
        JButton joinButton = new JButton("Join");
        this.findHostsPanel = new JPanel(new BorderLayout());

        this.table = new JTable();
        JScrollPane pane = new JScrollPane(this.table);
        JPanel panel = new JPanel(new GridLayout(2, 2));
        this.warningLabel = new JLabel();
        panel.add(this.warningLabel);
        panel.add(new JLabel());
        panel.add(new JLabel("Name"));
        panel.add(this.nameField);
        pane.setPreferredSize(new Dimension(200, 200));
        this.tableModel = (DefaultTableModel) this.table.getModel();
        this.tableModel.addColumn("Host Address");
        this.tableModel.addColumn("Port");
        this.findHostsPanel.add(joinButton, BorderLayout.SOUTH);
        this.findHostsPanel.add(pane, BorderLayout.CENTER);
        this.findHostsPanel.add(panel, BorderLayout.NORTH);


        joinButton.addActionListener(l -> {
            int index = this.table.getSelectedRow();
            if (index == -1) {
                this.warningLabel.setText("Please select game");
                return;
            }
            String name = this.nameField.getText();
            var game = this.games.get(this.table.getSelectedRow());
            var gameFrame = new GameFrame(this.controller, game.config, name, false);
            this.controller.setGamePanel(gameFrame.getGamePanel());
            this.controller.joinGame(game.inetSocketAddress, name, this.config.ping_delay_ms, this.config.node_timeout_ms);
            this.dispose();
        });
    }

    public void initHostPanel() {
        this.hostPanel = new JPanel(new GridLayout(12, 2));
        JButton startButton = new JButton("Start");
        this.hostPanel.add(new JLabel("Name"));
        this.hostPanel.add(this.hostNameField);
        for (var comp : this.menuConfig.components) {
            this.hostPanel.add(comp.label);
            this.hostPanel.add(comp.component);
        }
        this.hostPanel.add(startButton);
        startButton.addActionListener(l -> {
            this.dispose();
            var components = this.menuConfig.components;
            var name = this.hostNameField.getText();
            this.config.width = (Integer) components.get(0).component.getValue();
            this.config.height = (Integer) components.get(1).component.getValue();
            this.config.food_static = (Integer) components.get(2).component.getValue();
            double val = (Double) components.get(3).component.getValue();
            this.config.food_per_player = (float) val;
            this.config.state_delay_ms = (Integer) components.get(4).component.getValue();
            val = (Double) components.get(5).component.getValue();
            this.config.dead_food_prob = (float) val;
            this.config.ping_delay_ms = (Integer) components.get(6).component.getValue();
            this.config.node_timeout_ms = (Integer) components.get(7).component.getValue();
            var gameFrame = new GameFrame(this.controller, this.config, name, true);
            this.controller.setGamePanel(gameFrame.getGamePanel());
            this.controller.hostGame(this.config.ping_delay_ms, this.config.node_timeout_ms);
        });
    }


    public void changeContent(JPanel new_content) {
        this.content.removeAll();
        this.content.add(new_content);
        this.content.validate();
        this.content.repaint();
    }

    public void updateGamesList(ArrayList<Game> games) {
        this.games = games;
        int index = this.table.getSelectedRow();
        this.tableModel.setRowCount(0);
        for (var game : games) {
            Vector<String> row = new Vector<>();
            row.add(game.inetSocketAddress.getAddress().getHostAddress());
            row.add(Integer.toString(game.inetSocketAddress.getPort()));
            this.tableModel.addRow(row);
        }
        if (index != -1 && index < this.table.getRowCount()) {
            this.table.addRowSelectionInterval(index, index);
        }
    }

}