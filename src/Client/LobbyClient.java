package Client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;

public class LobbyClient {
    private JButton btn_createRoom, btn_joinRoom;
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;
    private Socket socket = null;
    private ObjectOutputStream out = null;
    private String userName, ipAddress;
    private int portNumber;
    private Thread receiveThread = null;
    ObjectInputStream in = null;
    
	public LobbyClient(String userName, String ipAddress, int portNumber) {
		
	}
}
