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
            System.err.println("Fehler beim Starten des Servers: " + e.getMessage());
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
        System.out.println("Client hinzugefügt: ID = " + clientId);
    }

    static void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println("Client entfernt: ID = " + clientId);
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
                System.out.println("Neuer Client verbunden: Name = " + clientName + ", ID = " + clientId);

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
            System.err.println("Verbindung zu " + clientName + " (ID: " + clientId + ") verloren.");
            e.printStackTrace();
        } finally {
            Server.removeClient(clientId);
            System.out.println(clientName + " (ID: " + clientId + ") hat die Verbindung getrennt.");
            sendDeregisterMessage();
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Fehler beim Schließen der Verbindung mit " + clientName + " (ID: " + clientId + "): " + e.getMessage());
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
                System.out.println("Nachricht von " + clientName + " (ID: " + clientId + "): " + new String(message.getDataFields().get(1), StandardCharsets.UTF_8));
                break;
            case "deregister":
                // Client abmelden und Nachricht an andere Clients senden
                sendDeregisterMessage();
                break;
            default:
                System.out.println("Unbekannter Nachrichtentyp von " + clientName + " (ID: " + clientId + "): " + message.getType());
        }
    }

    private void sendDeregisterMessage() {
        Message deregisterMessage = new Message("deregister");
        deregisterMessage.add(clientId);
        Server.broadcastMessage(deregisterMessage, clientId);
        System.out.println("Abmeldung von " + clientName + " (ID: " + clientId + ") an alle gesendet.");
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
            System.err.println("Fehler beim Senden der Nachricht an " + clientName + " (ID: " + clientId + "): " + e.getMessage());
            e.printStackTrace();
        }
    }
}
