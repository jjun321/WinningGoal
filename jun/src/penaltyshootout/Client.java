
package penaltyshootout;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/*
 * WINNING GOAL 클라이언트
 * 닉네임 입력, 채팅 화면, 메시지 송수신을 담당한다.
 */
public class Client extends JFrame {

    private static final long serialVersionUID = 1L;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private JTextArea chatArea;
    private JTextField inputField;
    private DefaultListModel<String> userModel;

    private String nickname;

    // 프로그램 실행 시 닉네임 입력 화면을 생성한다.
    public Client() {
        createNicknameGUI();
        setTitle("WINNING GOAL");
        setSize(600, 360);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // 창 닫기 버튼을 누르면 연결 종료
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                closeChat();
            }
        });

        setVisible(true);
    }

    // 닉네임 입력 화면
    private void createNicknameGUI() {

        JPanel panel = new JPanel();
        panel.setBackground(new Color(24, 27, 34));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(35, 70, 40, 70));

        JLabel title = new JLabel("⚽ WINNING GOAL");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 28));
        title.setForeground(new Color(155, 190, 230));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel description = new JLabel("친구와 함께하는 온라인 승부차기");
        description.setForeground(Color.LIGHT_GRAY);
        description.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 닉네임 입력창
        JTextField nicknameField = new JTextField();
        nicknameField.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        nicknameField.setHorizontalAlignment(JTextField.CENTER);
        nicknameField.setMaximumSize(new Dimension(380, 45));
        nicknameField.setToolTipText("닉네임을 입력하세요");

        // 입장 버튼
        JButton enterButton = new JButton("입장하기");
        enterButton.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        enterButton.setBackground(new Color(79, 151, 222));
        enterButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        enterButton.setMaximumSize(new Dimension(225, 50));

        panel.add(title);
        panel.add(Box.createVerticalStrut(15));
        panel.add(description);
        panel.add(Box.createVerticalStrut(30));
        panel.add(nicknameField);
        panel.add(Box.createVerticalStrut(20));
        panel.add(enterButton);

        // 버튼 클릭 또는 Enter 키로 입장
        enterButton.addActionListener(e -> enterGame(nicknameField));
        nicknameField.addActionListener(e -> enterGame(nicknameField));

        setContentPane(panel);
    }

    // 닉네임 확인 후 서버에 접속한다.
    private void enterGame(JTextField field) {

        nickname = field.getText().trim();

        if (nickname.isEmpty()) {
            JOptionPane.showMessageDialog(this, "닉네임을 입력해주세요.");
            return;
        }

        try {
            // 서버 연결
            socket = new Socket("localhost", 5001);

            out = new PrintWriter(
                    socket.getOutputStream(), true, StandardCharsets.UTF_8);

            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // 서버에 닉네임 전송
            out.println(Protocol.NICK + "|" + nickname);

            String response = in.readLine();

            if (response == null) {
                throw new Exception("서버 응답이 없습니다.");
            }

            // 닉네임 중복 등 서버 오류 처리
            if (response.startsWith(Protocol.ERROR + "|")) {
                JOptionPane.showMessageDialog(this, response.split("\\|", 2)[1]);
                closeConnection();
                return;
            }

            // 닉네임 승인 후 채팅 화면으로 이동
            if (response.startsWith(Protocol.OK + "|")) {
                createGUI();
                receiveMessages();
            } else {
                throw new Exception("서버 응답 오류");
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this, "서버에 연결할 수 없습니다.\n" + e.getMessage());
            closeConnection();
        }
    }

    // 채팅 화면 생성
    private void createGUI() {

        setTitle("WINNING GOAL - " + nickname);
        setSize(750, 550);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JLabel title = new JLabel("⚽ WINNING GOAL", SwingConstants.CENTER);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 24));
        panel.add(title, BorderLayout.NORTH);

        // 채팅 메시지 표시 영역
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 15));
        chatArea.setLineWrap(true);

        panel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // 접속자 목록
        userModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userModel);

        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.add(new JLabel("접속자 목록", SwingConstants.CENTER), BorderLayout.NORTH);
        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        userPanel.setPreferredSize(new Dimension(140, 0));

        panel.add(userPanel, BorderLayout.EAST);

        // 메시지 입력 영역
        JPanel bottom = new JPanel(new BorderLayout(5, 5));

        inputField = new JTextField();
        JButton sendButton = new JButton("전송");

        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(sendButton, BorderLayout.EAST);

        panel.add(bottom, BorderLayout.SOUTH);

        // 전송 버튼과 Enter 키 이벤트
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        setContentPane(panel);
        revalidate();
        repaint();

        inputField.requestFocusInWindow();
    }

    // 입력한 메시지를 서버로 전송한다.
    private void sendMessage() {

        String message = inputField.getText().trim();

        if (message.isEmpty()) return;

        if (message.equals("/quit")) {
            closeChat();
            return;
        }

        out.println(Protocol.CHAT + "|" + message);
        inputField.setText("");
    }

    // 서버에서 메시지를 계속 수신하는 스레드
    private void receiveMessages() {

        Thread receiver = new Thread(() -> {
            try {
                String message;

                while ((message = in.readLine()) != null) {

                    String received = message;

                    // Swing 화면 변경은 이벤트 처리 스레드에서 실행
                    SwingUtilities.invokeLater(() -> {

                        // 접속자 목록 갱신
                        if (received.startsWith(Protocol.USER_LIST + "|")) {

                            userModel.clear();
                            String list = received.split("\\|", 2)[1];

                            if (!list.isEmpty()) {
                                for (String user : list.split(",")) {
                                    userModel.addElement(user);
                                }
                            }
                        }

                        // 채팅 메시지 출력
                        else if (received.startsWith(Protocol.CHAT + "|")) {

                            String[] parts = received.split("\\|", 3);

                            if (parts.length == 3) {
                                chatArea.append(
                                        "[" + parts[1] + "] " + parts[2] + "\n");

                                chatArea.setCaretPosition(
                                        chatArea.getDocument().getLength());
                            }
                        }
                    });
                }

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (chatArea != null && isDisplayable()) {
                        chatArea.append("\n서버 연결이 종료되었습니다.\n");
                    }
                });
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    // 서버 연결을 종료한다.
    private void closeConnection() {

        try {
            if (out != null) out.println(Protocol.QUIT);
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception e) {
            System.out.println("연결 종료 오류: " + e.getMessage());
        }
    }

    // 프로그램 종료
    private void closeChat() {
        closeConnection();
        dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}






