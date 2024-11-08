package at.fhooe.tcp_chat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class Client extends Application {

    private volatile boolean cts = true;
    private TextField sendHost;
    private Spinner<Integer> sendPort;
    private TextField sendName;
    private TextField sendMessage;
    private VBox activeChatView;
    private ListView<HBox> clientListView;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private boolean isConnected = false;
    private String clientId;
    private File imageFile;
    private String activeChatId = null; // ID des aktuellen Chat-Partners
    private Map<String, VBox> chatViews = new HashMap<>(); // Speichert die Chat-Ansichten für jeden Teilnehmer
    private Map<String, String> chatNames = new HashMap<>();
    private Label statusLabel;

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        showSetupWindow(primaryStage);
    }

    private void showSetupWindow(Stage primaryStage) {
        Stage setupStage = new Stage();
        setupStage.setTitle("Setup - Verbindungseinstellungen");

        TextField serverIPField = new TextField(); // Erst leer, wird durch UDP Broadcast gesetzt
        Spinner<Integer> serverPortSpinner = new Spinner<>(0, 65535, 4000);
        TextField nameField = new TextField("Anonymous");

        Button chooseImageButton = new Button("Profilbild auswählen");
        Label imagePathLabel = new Label("Kein Bild ausgewählt");

        statusLabel = new Label("Warte auf Server-Einstellungen"); // Statusleiste anzeigen

        chooseImageButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Profilbild auswählen");
            imageFile = fileChooser.showOpenDialog(setupStage);
            if (imageFile != null) {
                imagePathLabel.setText(imageFile.getName());
            }
        });

        Button connectButton = new Button("Verbinden");
        connectButton.setOnAction(event -> {
            try {
                socket = new Socket(serverIPField.getText(), serverPortSpinner.getValue());
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());

                sendHost = new TextField(serverIPField.getText());
                sendPort = serverPortSpinner;
                sendName = new TextField(nameField.getText());
                isConnected = true;

                sendRegisterMessage(nameField.getText(), imageFile);

                setupStage.close();
                showMainGUI(primaryStage);

                new Thread(this::listenForMessages).start();
            } catch (IOException e) {
                showAlert("Verbindungsfehler", "Es konnte keine Verbindung hergestellt werden.", e.getMessage());
            }
        });

        GridPane setupLayout = new GridPane();
        setupLayout.setPadding(new Insets(10));
        setupLayout.setHgap(10);
        setupLayout.setVgap(10);
        setupLayout.add(new Label("Server IP:"), 0, 0);
        setupLayout.add(serverIPField, 1, 0);
        setupLayout.add(new Label("Server Port:"), 0, 1);
        setupLayout.add(serverPortSpinner, 1, 1);
        setupLayout.add(new Label("Name:"), 0, 2);
        setupLayout.add(nameField, 1, 2);
        setupLayout.add(chooseImageButton, 0, 3);
        setupLayout.add(imagePathLabel, 1, 3);
        setupLayout.add(connectButton, 1, 4);
        setupLayout.add(statusLabel, 1, 5); // Statuslabel hinzufügen

        Scene setupScene = new Scene(setupLayout, 400, 250);
        setupStage.setScene(setupScene);
        setupStage.show();

        // Starte den UDP Broadcast Listener in einem neuen Thread
        new Thread(() -> listenForServerBroadcast(serverIPField, serverPortSpinner)).start();
    }

    private void listenForServerBroadcast(TextField serverIPField, Spinner<Integer> serverPortSpinner) {
        try (DatagramSocket udpSocket = new DatagramSocket(8501)) {
            udpSocket.setBroadcast(true);
            byte[] buffer = new byte[256];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                String serverIP = packet.getAddress().getHostAddress();
                String _serverPort = new String(packet.getData(), 0, packet.getLength());
                int serverPort = Integer.parseInt(_serverPort);

                // Setze die empfangene IP und aktualisiere den Status
                Platform.runLater(() -> {
                    serverIPField.setText(serverIP);
                    serverPortSpinner.getValueFactory().setValue(serverPort);
                    statusLabel.setText("Server gefunden: " + serverIP);
                });
            }
        } catch (SocketException e) {
            Platform.runLater(() -> statusLabel.setText("Fehler beim Erstellen des UDP-Sockets."));
        } catch (IOException e) {
            Platform.runLater(() -> statusLabel.setText("Fehler beim Empfangen der Server-Einstellungen."));
        }
    }

    private void showMainGUI(Stage primaryStage) {
        if (!isConnected)
            return;

        primaryStage.setOnCloseRequest(event -> {
            cts = false;
            // Schicke Deregistrierungsnachricht an den Server
            sendDeregisterMessage();
            // Idle 500ms
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Schließe die Socket-Verbindung
            closeSocket();
        });

        sendMessage = new TextField();
        sendMessage.setPromptText("Nachricht...");
        sendMessage.setOnAction(event -> sendTextMessage());

        activeChatView = new VBox();
        activeChatView.setSpacing(10);
        activeChatView.setPadding(new Insets(10));

        clientListView = new ListView<>();
        clientListView.setPlaceholder(new Label("Keine Clients verbunden"));
        clientListView.setPrefWidth(150);

        clientListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                String selectedClientId = newSelection.getId();
                selectChat(selectedClientId);
            }
        });

        Button sendButton = new Button("Senden");
        sendButton.setOnAction(event -> sendTextMessage());

        ScrollPane receivePane = new ScrollPane(activeChatView);
        receivePane.setFitToWidth(true);

        HBox sendPane = createSendPane(sendButton);
        BorderPane chatLayout = new BorderPane();
        chatLayout.setTop(createSettingsBar());
        chatLayout.setCenter(receivePane);
        chatLayout.setBottom(sendPane);

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(clientListView, chatLayout);
        splitPane.setDividerPositions(0.25);

        Scene scene = new Scene(splitPane, 800, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Chat GUI");
        primaryStage.setOnCloseRequest(event -> {
            sendDeregisterMessage();
            closeSocket();
        });
        primaryStage.show();
    }

    private void selectChat(String clientId) {
        activeChatId = clientId;
        activeChatView.getChildren().clear();

        // Zeige alle Nachrichten der entsprechenden Chat-Ansicht in activeChatView an
        if (chatViews.containsKey(clientId)) {
            activeChatView.getChildren().addAll(chatViews.get(clientId).getChildren());
        }

        // Setze Schriftstärke des ausgewählten Teilnehmers auf normal
        clientListView.getItems().forEach(item -> {
            if (item.getId().equals(clientId)) {
                ((Label) item.getChildren().get(1)).setStyle("-fx-font-weight: normal");
            }
        });
    }

    private HBox createSendPane(Button sendButton) {
        HBox sendPane = new HBox();
        sendPane.getChildren().addAll(sendMessage, sendButton);
        sendPane.setAlignment(Pos.CENTER);
        sendPane.setPadding(new Insets(10));
        sendPane.setSpacing(10);
        return sendPane;
    }

    private GridPane createSettingsBar() {
        GridPane settingsBar = new GridPane();
        settingsBar.setPadding(new Insets(10));
        settingsBar.setHgap(10);
        settingsBar.setVgap(10);
        settingsBar.add(new Label("IP:"), 0, 0);
        settingsBar.add(sendHost, 1, 0);
        settingsBar.add(new Label("Port:"), 2, 0);
        settingsBar.add(sendPort, 3, 0);
        settingsBar.add(new Label("Name:"), 4, 0);
        settingsBar.add(sendName, 5, 0);
        return settingsBar;
    }

    private void sendRegisterMessage(String name, File imageFile) {
        Message registerMessage = new Message("register");
        registerMessage.add(name);

        if (imageFile != null) {
            try {
                byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
                registerMessage.add(imageBytes);
            } catch (IOException e) {
                showAlert("Bildproblem", "Fehler beim Lesen der Bilddatei.", e.getMessage());
            }
        }
        sendToServer(registerMessage);
    }

    private void sendTextMessage() {
        String messageText = sendMessage.getText();

        if (activeChatId == null) {
            showAlert("Nachrichtenfehler", "Kein Chat ausgewählt",
                    "Bitte wähle einen Chat aus, bevor du eine Nachricht sendest.");
            return;
        }
        if (messageText == null) {
            showAlert("Nachrichtenfehler", "Nachricht leer", "Bitte gib eine Nachricht ein, bevor du sie sendest.");
            return;
        }

        if (clientId == null) {
            showAlert("Nachricht konnte nicht gesendet werden", "Client-ID nicht verfügbar",
                    "Bitte warte, bis die Client-ID zugewiesen wurde.");
            return;
        }
        // Prüfen, ob das Textfeld leer oder null ist
        if (activeChatId != null && messageText != null && !messageText.trim().isEmpty()) {
            Message textMessage = new Message("message");
            textMessage.add(clientId); // Client-ID des Senders
            textMessage.add(activeChatId); // ID des Empfängers
            textMessage.add(messageText); // Nachrichtentext

            sendToServer(textMessage);
            displayMessage("Ich", messageText, Pos.CENTER_RIGHT, activeChatId, Color.LIGHTBLUE);
            sendMessage.clear();
        } else {
            showAlert("Nachrichtenfehler", "Leere Nachricht", "Bitte gib eine Nachricht ein, bevor du sie sendest.");
        }
    }

    private void listenForMessages() {
        try {
            while (cts) {
                int length = in.readInt();
                byte[] messageBytes = new byte[length];
                in.readFully(messageBytes);

                Message receivedMessage = new Message(ByteBuffer.wrap(messageBytes));
                Platform.runLater(() -> handleMessageFromServer(receivedMessage));
            }
        } catch (IOException e) {
            if (cts == true)
                showAlert("Empfangsproblem", "Es ist ein Problem beim Empfangen aufgetreten.", e.getMessage());
        }
    }

    private void handleMessageFromServer(Message message) {
        switch (message.getType()) {
            case "id_assignment":
                clientId = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                System.out.println("Server hat ID zugewiesen: " + clientId);
                break;
            case "id_announcement":
                String newClientName = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                String newClientId = new String(message.getDataFields().get(1), StandardCharsets.UTF_8);
                chatNames.put(newClientId, newClientName);
                byte[] imageBytes = message.getDataFields().size() > 2 ? message.getDataFields().get(2) : null;
                Image newClientImage = imageBytes != null
                        ? new Image(new ByteArrayInputStream(imageBytes), 30, 30, true, true)
                        : null;

                addClientToList(newClientName, newClientId, newClientImage);
                break;
            case "message":
                String senderId = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                String name = chatNames.get(senderId);
                String text = new String(message.getDataFields().get(2), StandardCharsets.UTF_8);
                displayMessage(name, text, Pos.CENTER_LEFT, senderId, Color.LIGHTGREEN);

                if (!senderId.equals(activeChatId)) {
                    markClientAsUnread(senderId);
                }
                break;
            case "deregister":
                String disconnectedClientId = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                Platform.runLater(
                        () -> clientListView.getItems().removeIf(hbox -> hbox.getId().equals(disconnectedClientId)));
                break;
            default:
                System.out.println("Unbekannter Nachrichtentyp: " + message.getType());
        }
    }

    private void addClientToList(String name, String id, Image profileImage) {
        Platform.runLater(() -> {
            HBox clientBox = new HBox(5);
            ImageView imageView = new ImageView(profileImage);
            imageView.setFitHeight(30);
            imageView.setPreserveRatio(true);
            Label nameLabel = new Label(name);
            clientBox.getChildren().addAll(imageView, nameLabel);
            clientBox.setId(id);
            clientListView.getItems().add(clientBox);

            chatViews.put(id, new VBox());
        });
    }

    private String toRgbString(Color color) {
        int red = (int) (color.getRed() * 255);
        int green = (int) (color.getGreen() * 255);
        int blue = (int) (color.getBlue() * 255);
        return "rgb(" + red + "," + green + "," + blue + ")";
    }

    private void displayMessage(String sender, String message, Pos position, String chatId, Color bgColor) {
        // Erstelle das Label mit Nachrichtentext und Padding
        Label label = new Label(sender + ": " + message);
        label.setPadding(new Insets(10));
        label.setWrapText(true);
        label.setStyle("-fx-background-color: " + toRgbString(bgColor) + "; -fx-background-radius: 10;");

        // Erstelle die Box für die Nachricht und füge das Label hinzu
        HBox box = new HBox(label);
        box.setAlignment(position);
        box.setPadding(new Insets(5)); // Optional: zusätzlichen Abstand um die Box
        box.setSpacing(10); // Optional: Abstand zwischen Boxen

        // Füge die Nachricht der entsprechenden Chat-Ansicht in chatViews hinzu
        if (!chatViews.containsKey(chatId)) {
            chatViews.put(chatId, new VBox());
        }
        chatViews.get(chatId).getChildren().add(box);

        // Zeige die Nachricht nur an, wenn der Chat aktuell aktiv ist
        if (chatId.equals(activeChatId)) {
            activeChatView.getChildren().add(box);
        }
    }

    private void markClientAsUnread(String clientId) {
        clientListView.getItems().forEach(item -> {
            if (item.getId().equals(clientId)) {
                ((Label) item.getChildren().get(1)).setStyle("-fx-font-weight: bold");
            }
        });
    }

    private void showAlert(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            showAlert("Schließungsproblem", "Problem beim Schließen des Sockets.", e.getMessage());
        }
    }

    private void sendDeregisterMessage() {
        Message deregisterMessage = new Message("deregister");
        deregisterMessage.add(clientId);
        sendToServer(deregisterMessage);
    }

    private void sendToServer(Message message) {
        try {
            byte[] messageBytes = message.toBytes();
            out.writeInt(messageBytes.length);
            out.write(messageBytes);
        } catch (IOException e) {
            showAlert("Sendeproblem", "Nachricht konnte nicht gesendet werden.", e.getMessage());
        }
    }
}
