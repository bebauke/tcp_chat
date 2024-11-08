package at.fhooe.tcp_chat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Message {
    private String type;
    private final List<byte[]> dataFields = new ArrayList<>();

    // Konstruktor für eingehende Nachrichten
    public Message(ByteBuffer byteBuffer) {
        // Lesen des Nachrichtentyps aus dem ByteBuffer
        int typeLength = byteBuffer.getInt();
        byte[] typeBytes = new byte[typeLength];
        byteBuffer.get(typeBytes);
        this.type = new String(typeBytes, StandardCharsets.UTF_8);

        // Lesen der Felder
        while (byteBuffer.hasRemaining()) {
            int fieldLength = byteBuffer.getInt();
            byte[] fieldBytes = new byte[fieldLength];
            byteBuffer.get(fieldBytes);
            dataFields.add(fieldBytes);
        }
    }

    // Konstruktor für ausgehende Nachrichten
    public Message(String type) {
        this.type = type;
    }

    // Methode zum Hinzufügen eines Strings als Feld
    public void add(String value) {
        dataFields.add(value.getBytes(StandardCharsets.UTF_8));
    }

    // Methode zum Hinzufügen eines Bildes (hier als Byte-Array)
    public void add(byte[] imageData) {
        dataFields.add(imageData);
    }

    public void edit(int index, String value) {
        dataFields.set(index, value.getBytes(StandardCharsets.UTF_8));
    }

    public void edit(int index, byte[] imageData) {
        dataFields.set(index, imageData);
    }

    // Methode zum Konvertieren der Nachricht in ein Byte-Array für die Übertragung
    public byte[] toBytes() {
        int totalLength = 4 + type.length();  // 4 Bytes für die Länge des Typs + Typ selbst
        for (byte[] field : dataFields) {
            totalLength += 4 + field.length;  // 4 Bytes für die Länge jedes Feldes + Feld selbst
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);

        // Typ in Byte-Array schreiben
        buffer.putInt(type.length());
        buffer.put(type.getBytes(StandardCharsets.UTF_8));

        // Felder in Byte-Array schreiben
        for (byte[] field : dataFields) {
            buffer.putInt(field.length);
            buffer.put(field);
        }

        return buffer.array();
    }

    // Für die Ausgabe: Beispielhafte Methode zum Anzeigen der Nachrichtendaten
    public void printMessage() {
        System.out.println("Type: " + type);
        for (byte[] field : dataFields) {
            System.out.println("Field: " + new String(field, StandardCharsets.UTF_8));
        }
    }

    public String getType() {
        return type;
    }

    public List<byte[]> getDataFields() {
        return dataFields;
    }
}
