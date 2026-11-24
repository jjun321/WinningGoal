
package penaltyshootout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final int PORT = 5001;

    private static final Map<String, PrintWriter> clients =
            new ConcurrentHashMap<>();

    public static void main(String[] args) {

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {

            System.out.println("서버가 시작되었습니다.");
            System.out.println("포트 번호: " + PORT);

            while (true) {

                Socket socket = serverSocket.accept();

                System.out.println("새로운 클라이언트가 접속했습니다.");

                ClientHandler handler = new ClientHandler(socket);
                handler.start();
            }

        } catch (Exception e) {
            System.out.println("서버 오류: " + e.getMessage());
        }
    }

    // 접속자 목록 전송
    public static void broadcastUserList() {

        String userList = String.join(",", clients.keySet());

        for (PrintWriter writer : clients.values()) {
            writer.println(Protocol.USER_LIST + "|" + userList);
            writer.flush();
        }
    }

    // 전체 채팅 전송
    public static void broadcastChat(String nickname, String message) {

        String chatMessage =
                Protocol.CHAT + "|" + nickname + "|" + message;

        for (PrintWriter writer : clients.values()) {
            writer.println(chatMessage);
            writer.flush();
        }
    }

    // 클라이언트 통신 처리
    static class ClientHandler extends Thread {

        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String nickname;
        private boolean registered = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try {

                in = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                out = new PrintWriter(
                        socket.getOutputStream(),
                        true,
                        StandardCharsets.UTF_8
                );

                // 닉네임 입력 및 중복 확인
                while (true) {

                    String firstMessage = in.readLine();

                    if (firstMessage == null) {
                        return;
                    }

                    String[] parts = firstMessage.split("\\|", 2);

                    if (parts.length != 2 ||
                            !parts[0].equals(Protocol.NICK)) {

                        out.println(Protocol.ERROR + "|잘못된 요청입니다.");
                        continue;
                    }

                    String inputNickname = parts[1].trim();

                    if (inputNickname.isEmpty()) {
                        out.println(Protocol.ERROR + "|닉네임을 입력해주세요.");
                        continue;
                    }

                    if (inputNickname.contains("|") ||
                            inputNickname.contains(",")) {

                        out.println(
                                Protocol.ERROR + "|닉네임에 사용할 수 없는 문자가 있습니다."
                        );
                        continue;
                    }

                    PrintWriter existing =
                            clients.putIfAbsent(inputNickname, out);

                    if (existing != null) {
                        out.println(
                                Protocol.ERROR + "|이미 사용 중인 닉네임입니다."
                        );
                        continue;
                    }

                    nickname = inputNickname;
                    registered = true;

                    out.println(Protocol.OK + "|접속 성공!");

                    System.out.println(nickname + "님이 접속했습니다.");

                    break;
                }

                // 접속자 목록 갱신
                broadcastUserList();

                // 채팅 메시지 수신
                String message;

                while ((message = in.readLine()) != null) {

                    if (message.equals(Protocol.QUIT)) {
                        break;
                    }

                    if (message.startsWith(Protocol.CHAT + "|")) {

                        String[] chatParts = message.split("\\|", 2);

                        if (chatParts.length == 2) {

                            System.out.println(
                                    "[" + nickname + "] " + chatParts[1]
                            );

                            System.out.flush();

                            broadcastChat(nickname, chatParts[1]);
                        }
                    }
                }

            } catch (Exception e) {

                System.out.println(
                        "클라이언트 통신 오류: " + e.getMessage()
                );

            } finally {

                if (registered) {

                    clients.remove(nickname, out);

                    System.out.println(nickname + "님이 퇴장했습니다.");

                    broadcastUserList();
                }

                try {
                    socket.close();
                } catch (Exception e) {
                    System.out.println("소켓 종료 오류");
                }
            }
        }
    }
}



