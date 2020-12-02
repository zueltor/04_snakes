package ru.nsu.g.mustafin.lab4.snakes.gui;

import ru.nsu.g.mustafin.lab4.snakes.GamePanel;
import ru.nsu.g.mustafin.lab4.snakes.messages.MessageController;
import ru.nsu.g.mustafin.lab4.snakes.model.GameConfig;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class GameFrame extends JFrame {
    private final GamePanel gamePanel;

    public GameFrame(MessageController controller, GameConfig config, String name, boolean isMaster) {
        this.gamePanel = new GamePanel(controller, config, name, isMaster);
        this.add(this.gamePanel);
        this.setTitle("Snake");
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                try {
                    System.out.println("Closing");
                    controller.close();
                    GameFrame.this.gamePanel.stop();
                } catch (Exception ignored) {
                }
                new MenuFrame();
            }
        });

        this.setResizable(false);
        this.pack();
        this.setVisible(true);
        this.setLocationRelativeTo(null);
    }

    public GamePanel getGamePanel() {
        return this.gamePanel;
    }
}
