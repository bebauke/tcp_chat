package at.fhooe.tcp_chat;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Server {
    private static final int PORT = 4000;
    private static final Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Server gestartet...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void broadcastMessage(Message message, String senderId) {
        for (ClientHandler client : clients.values()) {
            if (!client.getClientId().equals(senderId)) {
                client.sendMessage(message);
            }
        }
    }

    static void addClient(String clientId, ClientHandler clientHandler) {
        clients.put(clientId, clientHandler);
    }

    static void removeClient(String clientId) {
        clients.remove(clientId);
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String clientId;
    private String clientName;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Anmeldenachricht empfangen
            int length = in.readInt();  // Lesen der Länge des Nachrichten-Byte-Arrays
            byte[] messageBytes = new byte[length];
            in.readFully(messageBytes);
            Message message = new Message(ByteBuffer.wrap(messageBytes));

            if (message.getType().equals("register")) {
                clientName = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                clientId = UUID.randomUUID().toString(); // Generiere eine eindeutige ID

                Server.addClient(clientId, this);
                System.out.println(clientName + " (" + clientId + ") hat sich verbunden.");

                // ID-Bekanntmachung an alle Clients
                Message idAnnouncement = new Message("id_announcement");
                idAnnouncement.add(clientName);
                idAnnouncement.add(clientId);
                Server.broadcastMessage(idAnnouncement, clientId);
            }

            // Nachrichtenverarbeitung in einer Schleife
            while ((length = in.readInt()) != -1) {
                messageBytes = new byte[length];
                in.readFully(messageBytes);
                message = new Message(ByteBuffer.wrap(messageBytes));
                handleClientMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Server.removeClient(clientId);
            System.out.println(clientName + " (" + clientId + ") hat die Verbindung getrennt.");
            sendDeregisterMessage();
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleClientMessage(Message message) {
        switch (message.getType()) {
            case "message":
                // Nachricht an andere Clients senden
                Message messageToSend = new Message("message");
                messageToSend.add(clientId);
                messageToSend.add(new String(message.getDataFields().get(1), StandardCharsets.UTF_8));
                Server.broadcastMessage(messageToSend, clientId);
                break;
            case "deregister":
                // Client abmelden und Nachricht an andere Clients senden
                sendDeregisterMessage();
                break;
            default:
                System.out.println("Unbekannter Nachrichtentyp: " + message.getType());
        }
    }

    private void sendDeregisterMessage() {
        Message deregisterMessage = new Message("deregister");
        deregisterMessage.add(clientId);
        Server.broadcastMessage(deregisterMessage, clientId);
    }

    public String getClientId() {
        return clientId;
    }

    public void sendMessage(Message message) {
        try {
            byte[] messageBytes = message.toBytes();
            out.writeInt(messageBytes.length);  // Länge der Nachricht
            out.write(messageBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
