package Client;

import javax.swing.*;
import java.awt.*;
import java.io.ObjectInputStream;

public class GameRoom extends JPanel{
    ObjectInputStream in = null;
    public GameRoom() {
        setLayout(new BorderLayout());
        setBackground(new Color(224, 245, 255));

        // 상단 타이틀
        JLabel title = new JLabel("", SwingConstants.LEFT);
        title.setFont(new Font("Dialog", Font.BOLD, 40));
        title.setForeground(new Color(80, 190, 255));
        title.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        add(title, BorderLayout.NORTH);
    }
}
