package at.fhooe.tcp_chat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Client extends Application {

    private TextField sendHost;
    private Spinner<Integer> sendPort;
    private TextField sendName;
    private TextField sendMessage;
    private VBox receiveMessages;
    private ListView<String> clientListView;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private boolean isConnected = false;
    private String clientId;

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

        TextField serverIPField = new TextField("127.0.0.1");
        Spinner<Integer> serverPortSpinner = new Spinner<>(0, 65535, 4000);
        TextField nameField = new TextField("Anonymous");

        Button connectButton = new Button("Verbinden");
        connectButton.setOnAction(event -> {
            try {
                socket = new Socket(serverIPField.getText(), serverPortSpinner.getValue());
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());

                sendHost = new TextField(serverIPField.getText());
                sendPort = serverPortSpinner;
                sendName = new TextField(nameField.getText());
                clientId = UUID.randomUUID().toString();
                isConnected = true;

                // Anmeldung am Server
                sendRegisterMessage(nameField.getText());

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
        setupLayout.add(connectButton, 1, 3);

        Scene setupScene = new Scene(setupLayout, 300, 200);
        setupStage.setScene(setupScene);
        setupStage.show();
    }

    private void showMainGUI(Stage primaryStage) {
        if (!isConnected) return;

        sendMessage = new TextField();
        sendMessage.setPromptText("Nachricht...");
        sendMessage.setOnAction(event -> sendTextMessage());

        receiveMessages = new VBox();
        receiveMessages.setSpacing(10);
        receiveMessages.setPadding(new Insets(10));

        clientListView = new ListView<>();
        clientListView.setPlaceholder(new Label("Keine Clients verbunden"));
        clientListView.setPrefWidth(150);

        Button sendButton = new Button("Senden");
        sendButton.setOnAction(event -> sendTextMessage());

        ScrollPane receivePane = new ScrollPane(receiveMessages);
        receivePane.setFitToWidth(true);

        HBox sendPane = createSendPane(sendButton);
        BorderPane chatLayout = new BorderPane();
        chatLayout.setTop(createSettingsBar());
        chatLayout.setCenter(receivePane);
        chatLayout.setBottom(sendPane);

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(clientListView, chatLayout);
        splitPane.setDividerPositions(0.25);  // Setzt die Spaltenbreite auf 25% und 75%

        Scene scene = new Scene(splitPane, 800, 500);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Chat GUI");
        primaryStage.setOnCloseRequest(event -> {
            sendDeregisterMessage();
            closeSocket();
        });
        primaryStage.show();
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

    private void sendRegisterMessage(String name) {
        Message registerMessage = new Message("register");
        registerMessage.add(name);
        sendToServer(registerMessage);
    }

    private void sendTextMessage() {
        String selectedClient = clientListView.getSelectionModel().getSelectedItem();
        if (selectedClient != null) {
            Message textMessage = new Message("message");
            textMessage.add(clientId);
            textMessage.add(sendMessage.getText());
            sendToServer(textMessage);
            displayMessage("Ich", sendMessage.getText(), Pos.CENTER_RIGHT);
            sendMessage.clear();
        } else {
            showAlert("Kein Chatpartner ausgewählt","Fehlende Info", "Bitte wähle einen Chatpartner aus der Liste.");
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
            out.writeInt(messageBytes.length);  // Länge der Nachricht
            out.write(messageBytes);
        } catch (IOException e) {
            showAlert("Sendeproblem", "Nachricht konnte nicht gesendet werden.", e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            while (true) {
                int length = in.readInt();
                byte[] messageBytes = new byte[length];
                in.readFully(messageBytes);

                Message receivedMessage = new Message(ByteBuffer.wrap(messageBytes));
                Platform.runLater(() -> handleMessageFromServer(receivedMessage));
            }
        } catch (IOException e) {
            showAlert("Empfangsproblem", "Es ist ein Problem beim Empfangen aufgetreten.", e.getMessage());
        }
    }

    private void handleMessageFromServer(Message message) {
        switch (message.getType()) {
            case "id_announcement":
                String newClientName = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                Platform.runLater(() -> clientListView.getItems().add(newClientName));
                displayMessage("Server", "Neuer Client verbunden: " + newClientName, Pos.CENTER_LEFT);
                break;
            case "message":
                displayMessage("Anderer", new String(message.getDataFields().get(1), StandardCharsets.UTF_8), Pos.CENTER_LEFT);
                break;
            case "deregister":
                String disconnectedClientName = new String(message.getDataFields().get(0), StandardCharsets.UTF_8);
                Platform.runLater(() -> clientListView.getItems().remove(disconnectedClientName));
                displayMessage("Server", "Teilnehmer abgemeldet: " + disconnectedClientName, Pos.CENTER_LEFT);
                break;
            default:
                System.out.println("Unbekannter Nachrichtentyp: " + message.getType());
        }
    }

    private void displayMessage(String sender, String message, Pos position) {
        Label label = new Label(sender + ": " + message);
        label.setPadding(new Insets(10));
        label.setWrapText(true);
        HBox box = new HBox(label);
        box.setAlignment(position);
        receiveMessages.getChildren().add(box);
    }

    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
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
}
