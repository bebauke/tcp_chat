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
