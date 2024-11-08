package at.fhooe.tcp_chat;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

// import javafx.scene.image.Image;
// import javafx.scene.image.PixelReader;
// import javafx.scene.image.WritableImage;
// import javafx.scene.paint.Color;

public class Server {
    private static final int PORT = 4000;
    private static final int BROADCAST_PORT = 8501; // UDP-Port für Broadcasts
    private static final int BROADCAST_INTERVAL_MS = 5000; // Intervall von 5 Sekunden
    static final Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Server gestartet...");
        
        // Startet den Broadcast-Thread
        new Thread(Server::startBroadcast).start();

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

    // Methode für den regelmäßigen Broadcast
    private static void startBroadcast() {
        try (DatagramSocket broadcastSocket = new DatagramSocket()) {
            broadcastSocket.setBroadcast(true);
            String broadcastMessage = (PORT)+"";
            byte[] buffer = broadcastMessage.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("255.255.255.255"), BROADCAST_PORT);

            while (true) {
                broadcastSocket.send(packet);
                System.out.println("Broadcast gesendet: " + broadcastMessage);
                Thread.sleep(BROADCAST_INTERVAL_MS); // Warten Sie 5 Sekunden
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Fehler beim Senden des Broadcasts: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Nachricht an einen bestimmten Client senden
    static void sendMessageToClient(Message message, String recipientId) {
        ClientHandler recipient = clients.get(recipientId);
        if (recipient != null) {
            recipient.sendMessage(message);
        } else {
            System.out.println("Empfänger mit ID " + recipientId + " nicht gefunden.");
        }
    }

    // Nachricht an alle Clients senden, außer an den Sender
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
    protected byte[] clientImage;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Empfang der Anmeldenachricht
            int length = in.readInt();
            byte[] messageBytes = new byte[length];
            in.readFully(messageBytes);
            Message message = new Message(ByteBuffer.wrap(messageBytes));

            if (message.getType().equals("register")) {
                clientName = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                clientId = UUID.randomUUID().toString();

                // Speichert das Profilbild des Clients (falls vorhanden)
                if (message.getDataFields().size() > 1) {
                    byte[] originalImage = message.getDataFields().get(1);

                    try {
                        // Zuschneiden auf ein Rechteck von 40x40 Pixeln
                        clientImage = cropImageToRectangle(originalImage, 40, 40);
                    } catch (IOException e) {
                        System.err.println("Fehler beim Zuschneiden des Profilbildes: " + e.getMessage());
                        clientImage = originalImage; // Verwende das Originalbild, falls das Zuschneiden fehlschlägt
                    }
                }

                Server.addClient(clientId, this);
                System.out.println("Neuer Client verbunden: Name = " + clientName + ", ID = " + clientId);
                Message idAssignmentMessage = new Message("id_assignment");
                idAssignmentMessage.add(clientId);
                sendMessage(idAssignmentMessage);

                // Abrufen der Liste aller Clients und Senden an den neuen Client
                for (ClientHandler client : Server.clients.values()) {
                    if (client.clientId == clientId)
                        continue;
                    Message clientListMessage = new Message("id_announcement");
                    clientListMessage.add(client.clientName);
                    clientListMessage.add(client.clientId);
                    if (client.clientImage != null) {
                        clientListMessage.add(client.clientImage);
                    }
                    sendMessage(clientListMessage);
                }
                // Benachrichtigung aller anderen Clients über den neuen Client
                Message newClientAnnouncement = new Message("id_announcement");
                newClientAnnouncement.add(clientName);
                newClientAnnouncement.add(clientId);
                if (clientImage != null) {
                    newClientAnnouncement.add(clientImage);
                }
                Server.broadcastMessage(newClientAnnouncement, clientId);
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
            deregister();
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
                message.edit(0, clientId); // Stelle sicher dass Absender nicht manipuliert wurde

                // Extrahiere Empfänger-ID aus der Nachricht und sende nur an diesen Empfänger
                String recipientId = new String(message.getDataFields().get(1), StandardCharsets.UTF_8);
                Server.sendMessageToClient(message, recipientId); // Senden an den vorgesehenen Empfänger
                break;

            case "deregister":
                deregister();
                break;

            default:
                System.out.println("Unbekannter Nachrichtentyp von " + clientName + " (ID: " + clientId + "): "
                        + message.getType());
        }
    }

    private void deregister() {
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
            out.writeInt(messageBytes.length);
            out.write(messageBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Zuschneidemethode für das Profilbild
    private byte[] cropImageToRectangle(byte[] imageData, int width, int height) throws IOException {
        return imageData;
        // ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);
        // Image image = new Image(inputStream);

        // // Überprüfen, ob das Bild geladen wurde
        // if (image.isError()) {
        // throw new IOException("Fehler beim Laden des Bildes.");
        // }

        // // Berechne das Seitenverhältnis und setze den Zuschnittbereich
        // int cropWidth = (int) Math.min(image.getWidth(), width);
        // int cropHeight = (int) Math.min(image.getHeight(), height);

        // PixelReader pixelReader = image.getPixelReader();

        // // Erstellen eines Byte-Arrays für die RGBA-Daten des zugeschnittenen Bildes
        // ByteBuffer buffer = ByteBuffer.allocate(cropWidth * cropHeight * 4); // 4
        // Bytes pro Pixel für RGBA

        // for (int y = 0; y < cropHeight; y++) {
        // for (int x = 0; x < cropWidth; x++) {
        // Color color = pixelReader.getColor(x, y);
        // buffer.put((byte) (color.getRed() * 255)); // Rot
        // buffer.put((byte) (color.getGreen() * 255)); // Grün
        // buffer.put((byte) (color.getBlue() * 255)); // Blau
        // buffer.put((byte) (color.getOpacity() * 255)); // Alpha
        // }
        // }

        // return buffer.array();
    }

}
