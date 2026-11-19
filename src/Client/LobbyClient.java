package Client;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class LobbyClient extends JFrame {
    private RoomListPanel roomListPanel;
    private Socket socket = null;
    private DataOutputStream out = null;
    private DataInputStream in = null;
    private String userName, ipAddress;
    private int portNumber;
    private Thread receiveThread = null;

    // 단독 실행 테스트용 main 메서드
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LobbyClient("TestUser", "127.0.0.1", 3000));
    }

    public LobbyClient(String userName, String ipAddress, int portNumber) {
        this.userName = userName;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;

        setTitle("로비 클라이언트 - " + userName);
        setSize(1000, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 1. UI 먼저 생성
        buildLobbyUI();

        // 2. 서버 연결 및 수신 스레드 시작
        connectToServer();
        sendUserID();

        receiveThread = new ReceiveThread();
        receiveThread.start();

        // 3. 자동 새로고침 시작
        roomListPanel.startAutoRefresh();
    }

    private void connectToServer() {
        try {
            socket = new Socket(ipAddress, portNumber);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패: " + e.getMessage(), "연결 오류", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }
    }

    private class ReceiveThread extends Thread {
        @Override
        public void run() {
            while (receiveThread == Thread.currentThread()) {
                try {
                    String msg = in.readUTF(); // 문자열 수신
                    handleServerData(msg);
                } catch (IOException e) {
                    System.err.println("서버 수신 종료> " + e.getMessage());
                    break;
                }
            }
        }
    }

    private void handleServerData(String msg) {
        if (msg.startsWith("ROOM_LIST|")) {
            List<RoomListPanel.RoomInfo> parsedList = parseRoomListString(msg.substring("ROOM_LIST|".length()));
            SwingUtilities.invokeLater(() -> roomListPanel.updateRoomList(parsedList));
        } else if (msg.startsWith("CREATE_SUCCESS|")) {
            String roomId = msg.substring("CREATE_SUCCESS|".length());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "방이 생성되었습니다! (ID: " + roomId + ")");
                // 대기방 화면으로 전환 코드 연결
            });
        } else if (msg.startsWith("JOIN_SUCCESS|")) {
            String roomId = msg.substring("JOIN_SUCCESS|".length());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "방에 입장했습니다! (ID: " + roomId + ")");
                // 대기방 화면으로 전환 코드 연결
            });
        } else if (msg.startsWith("JOIN_FAIL|")) {
            String reason = msg.substring("JOIN_FAIL|".length());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, reason, "입장 실패", JOptionPane.WARNING_MESSAGE));
        }
    }

    private List<RoomListPanel.RoomInfo> parseRoomListString(String payload) {
        List<RoomListPanel.RoomInfo> list = new ArrayList<>();
        if (payload == null || payload.trim().isEmpty()) {
            return list;
        }
        String[] roomTokens = payload.split(";");
        for (String token : roomTokens) {
            String[] fields = token.split(",");
            if (fields.length >= 6) {
                list.add(new RoomListPanel.RoomInfo(
                        fields[0],
                        fields[1],
                        Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]),
                        Boolean.parseBoolean(fields[4]),
                        Boolean.parseBoolean(fields[5])
                ));
            }
        }
        return list;
    }

    private void sendUserID() {
        send("LOGIN|" + userName);
    }

    private synchronized void send(String msg) {
        try {
            if (out != null) {
                out.writeUTF(msg);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("클라이언트 전송 오류> " + e.getMessage());
        }
    }

    private void buildLobbyUI() {
        roomListPanel = new RoomListPanel();

        roomListPanel.setNetworkSender(this::send);

        roomListPanel.setOnRoomActionListener(new RoomListPanel.OnRoomActionListener() {
            @Override
            public void onCreateRoom(String roomName, String password) {
                String pw = (password == null) ? "" : password;
                send("CREATE_ROOM|" + roomName + "|" + pw + "|" + userName);
            }

            @Override
            public void onJoinRoom(String roomId, String password) {
                String pw = (password == null) ? "" : password;
                send("JOIN_ROOM|" + roomId + "|" + pw + "|" + userName);
            }
        });

        add(roomListPanel);
        setVisible(true);
    }

    // RoomListPanel
    public static class RoomListPanel extends JPanel {
        public interface NetworkSender {
            void send(String msg);
        }

        public interface OnRoomActionListener {
            void onCreateRoom(String roomName, String password);
            void onJoinRoom(String roomId, String password);
        }

        private DefaultTableModel tableModel;
        private JTable roomTable;
        private JButton createRoomButton = new JButton("방 만들기");
        private JButton joinRoomButton = new JButton("선택한 방 참가");
        private JButton refreshButton = new JButton("새로고침");

        private List<RoomInfo> rooms = new ArrayList<>();
        private Timer refreshTimer;
        private NetworkSender networkSender;
        private OnRoomActionListener roomActionListener;

        public RoomListPanel() {
            setLayout(new BorderLayout());
            setBackground(new Color(224, 245, 255));

            JLabel title = new JLabel("방 목록", SwingConstants.CENTER);
            title.setFont(new Font("Dialog", Font.BOLD, 40));
            title.setForeground(new Color(80, 190, 255));
            title.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
            add(title, BorderLayout.NORTH);

            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.setOpaque(false);
            centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

            String[] columnNames = {"방 이름", "인원", "상태"};
            tableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            roomTable = new JTable(tableModel);
            roomTable.setFont(new Font("Dialog", Font.PLAIN, 16));
            roomTable.setRowHeight(40);
            roomTable.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 18));
            roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            JScrollPane scrollPane = new JScrollPane(roomTable);
            scrollPane.setBackground(Color.WHITE);
            centerPanel.add(scrollPane, BorderLayout.CENTER);

            add(centerPanel, BorderLayout.CENTER);

            JPanel bottomPanel = new JPanel();
            bottomPanel.setOpaque(false);
            bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));

            createRoomButton.setPreferredSize(new Dimension(150, 50));
            createRoomButton.setBackground(new Color(49, 119, 196));
            createRoomButton.setForeground(Color.WHITE);
            createRoomButton.setFont(new Font("Dialog", Font.BOLD, 16));

            joinRoomButton.setPreferredSize(new Dimension(150, 50));
            joinRoomButton.setBackground(new Color(255, 255, 255));
            joinRoomButton.setForeground(Color.WHITE);
            joinRoomButton.setFont(new Font("Dialog", Font.BOLD, 16));

            refreshButton.setPreferredSize(new Dimension(120, 50));
            refreshButton.setBackground(new Color(0, 0, 0));
            refreshButton.setForeground(Color.WHITE);
            refreshButton.setFont(new Font("Dialog", Font.BOLD, 16));

            bottomPanel.add(createRoomButton);
            bottomPanel.add(joinRoomButton);
            bottomPanel.add(refreshButton);

            add(bottomPanel, BorderLayout.SOUTH);

            createRoomButton.addActionListener(e -> showCreateRoomDialog());
            joinRoomButton.addActionListener(e -> joinSelectedRoom());
            refreshButton.addActionListener(e -> requestRoomList());

            roomTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent evt) {
                    if (evt.getClickCount() == 2 && !rooms.isEmpty()) {
                        joinSelectedRoom();
                    }
                }
            });

            updateRoomList(new ArrayList<>());
            refreshTimer = new Timer(2000, e -> requestRoomList());
        }

        public void startAutoRefresh() {
            if (refreshTimer != null && !refreshTimer.isRunning()) {
                refreshTimer.start();
                requestRoomList();
            }
        }

        public void stopAutoRefresh() {
            if (refreshTimer != null && refreshTimer.isRunning()) {
                refreshTimer.stop();
            }
        }

        public void setNetworkSender(NetworkSender sender) {
            this.networkSender = sender;
        }

        public void setOnRoomActionListener(OnRoomActionListener listener) {
            this.roomActionListener = listener;
        }

        private void showCreateRoomDialog() {
            JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
            JTextField roomNameField = new JTextField(15);
            JCheckBox hasPasswordCheck = new JCheckBox("비밀번호 설정");
            JPasswordField passwordField = new JPasswordField(15);
            passwordField.setEnabled(false);

            hasPasswordCheck.addActionListener(e -> passwordField.setEnabled(hasPasswordCheck.isSelected()));

            panel.add(new JLabel("방 이름:"));
            panel.add(roomNameField);
            panel.add(hasPasswordCheck);
            panel.add(new JLabel());
            panel.add(new JLabel("비밀번호:"));
            panel.add(passwordField);

            int result = JOptionPane.showConfirmDialog(
                    this,
                    panel,
                    "방 만들기",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (result == JOptionPane.OK_OPTION) {
                String roomName = roomNameField.getText().trim();
                if (!roomName.isEmpty()) {
                    String password = null;
                    if (hasPasswordCheck.isSelected()) {
                        password = new String(passwordField.getPassword()).trim();
                        if (password.isEmpty()) password = null;
                    }
                    if (roomActionListener != null) {
                        roomActionListener.onCreateRoom(roomName, password);
                    }
                }
            }
        }

        private void joinSelectedRoom() {
            if (rooms.isEmpty()) return;

            int selectedRow = roomTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this,
                        "방을 선택해주세요!",
                        "알림",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            if (selectedRow < rooms.size()) {
                RoomInfo room = rooms.get(selectedRow);
                String password = null;

                if (room.hasPassword) {
                    JPasswordField passwordField = new JPasswordField(15);
                    int result = JOptionPane.showConfirmDialog(
                            this,
                            passwordField,
                            "🔒 비밀번호를 입력하세요",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.PLAIN_MESSAGE
                    );

                    if (result != JOptionPane.OK_OPTION) {
                        return;
                    }
                    password = new String(passwordField.getPassword()).trim();
                }

                if (roomActionListener != null) {
                    roomActionListener.onJoinRoom(room.roomId, password);
                }
            }
        }

        public void requestRoomList() {
            if (networkSender != null) {
                networkSender.send("REQUEST_ROOM_LIST");
            }
        }

        public void updateRoomList(List<RoomInfo> newRooms) {
            String selectedRoomId = null;
            int prevRow = roomTable.getSelectedRow();
            if (prevRow >= 0 && prevRow < this.rooms.size()) {
                selectedRoomId = this.rooms.get(prevRow).roomId;
            }

            this.rooms = new ArrayList<>(newRooms);
            tableModel.setRowCount(0);

            int rowToReselect = -1;
            for (int i = 0; i < rooms.size(); i++) {
                RoomInfo room = rooms.get(i);
                if (room.roomId != null && room.roomId.equals(selectedRoomId)) {
                    rowToReselect = i;
                }
                String status = room.inGame ? "게임 중" : "대기 중";
                String roomNameWithLock = room.hasPassword ? "🔒 " + room.roomName : room.roomName;
                Object[] row = {
                        roomNameWithLock,
                        room.currentPlayers + "/" + room.maxPlayers,
                        status
                };
                tableModel.addRow(row);
            }

            if (rooms.isEmpty()) {
                tableModel.addRow(new Object[]{"방이 없습니다", "-", "-"});
                joinRoomButton.setEnabled(false);
            } else {
                joinRoomButton.setEnabled(true);
                if (rowToReselect != -1) {
                    roomTable.setRowSelectionInterval(rowToReselect, rowToReselect);
                }
            }
        }

        public static class RoomInfo {
            public String roomId;
            public String roomName;
            public int currentPlayers;
            public int maxPlayers;
            public boolean inGame;
            public boolean hasPassword;

            public RoomInfo(String roomId, String roomName, int currentPlayers, int maxPlayers, boolean inGame, boolean hasPassword) {
                this.roomId = roomId;
                this.roomName = roomName;
                this.currentPlayers = currentPlayers;
                this.maxPlayers = maxPlayers;
                this.inGame = inGame;
                this.hasPassword = hasPassword;
            }
        }
    }
}