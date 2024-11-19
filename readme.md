### TCP Chat Server und Client (Java)

#### Aufgabenstellung
Implementieren Sie einen Chat-Server und einen Chat-Client, die über TCP/IP miteinander kommunizieren. Der Server soll mehrere Clients gleichzeitig bedienen können. Der Client soll eine einfache Benutzeroberfläche haben, über die der Benutzer Nachrichten an den Server senden kann. Der Server soll die Nachrichten an alle verbundenen Clients.

#### Anforderungen
- Der Server soll mehrere Clients gleichzeitig bedienen können.
- Der Server soll die Nachrichten an den vorgesehenen verbundenen Client weiterleiten.
- Der Client soll eine Benutzeroberfläche haben, über die der Benutzer Nachrichten an den Server senden kann, mit einem Target-Client. 
- Der Client soll die Nachrichten von anderen Clients empfangen und anzeigen können.
#### Optional
- Gruppen von Clients erstellen und Nachrichten an Gruppen senden.
- Foto von Client an Server senden, dann verteilen.


### Ausführung
#### Screenshots
![grafik](https://github.com/user-attachments/assets/669ba0f8-5fda-4c7f-9aff-0240756f6d32)
![grafik](https://github.com/user-attachments/assets/fa2212d1-1da4-4516-8d38-f7e3b5d0fa56)

Der Client empfängt auf seinem Standart-UDP-Port Server-IP und TCP-Port und übernimmt diese. Nach der optionalen Auswahl eines Profilbildes wird die Verbindung initiiert.

![grafik](https://github.com/user-attachments/assets/4a6d5663-7816-4d42-9a53-98187882a6f3)



### Anwendung der Software

#### Server

Der Server wird mit dem Befehl  
``` bash
java -jar .\server-1.0.jar
```
gestartet.

#### Client

Der Client wird mit dem Befehl
``` bash
java --module-path "C:\Users\<Userdir>\.m2\repository\org\openjfx\javafx-controls\21.0.5\javafx-controls-21.0.5-win.jar;C:\Users\<Userdir>\.m2\repository\org\openjfx\javafx-graphics\21.0.5\javafx-graphics-21.0.5-win.jar;C:\Users\<Userdir>\.m2\repository\org\openjfx\javafx-base\21.0.5\javafx-base-21.0.5-win.jar" --add-modules javafx.controls,javafx.graphics,javafx.base -jar .\client-1.0.jar
```
gestartet.

#### TODO: 
- Der Client vergisst einmal angezeigte Nachrichten. Nach einem Wechsel sollen sie erhallten bleiben.
- Wird der Name oder die Server-IP oder der Server-Port aktualisiert muss sich der Client mit dem neuen Namen/Server neu verbinden (Button: "↺")
