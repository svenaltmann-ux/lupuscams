# Lupus Cams – Android-App für den gesicherten Kamerazugriff

Live-Ansicht für Lupus LE232, LE221, LE204 und LE202. Die App spricht die Kameras direkt per RTSP im Heimnetz an.
Von unterwegs kommst du über das WireGuard-VPN deiner Fritzbox ins Heimnetz. Die Kameras sind nie direkt im Internet erreichbar.

## Was die App kann

- Übersicht mit vier Kacheln (Substream), Tipp auf eine Kachel öffnet den Hauptstream im Vollbild
- Entsperren per Fingerabdruck, Gesicht oder Displaysperre bei jedem App-Start; beim Verlassen sperrt die App wieder und beendet alle Streams
- Zugangsdaten werden verschlüsselt über den Android Keystore gespeichert
- Keine Screenshots und keine Vorschau im App-Wechsler
- RTSP über TCP, das läuft durch den VPN-Tunnel am stabilsten

## 1. App bauen (Android Studio)

1. Android Studio installieren und den Ordner `LupusCams` öffnen (File > Open).
2. Warten, bis der Gradle-Sync fertig ist. Falls Studio ein Update für das Android-Gradle-Plugin oder Gradle vorschlägt, kann man es annehmen.
3. Handy per USB anschließen (Entwickleroptionen und USB-Debugging aktivieren) und auf "Run" klicken.

Hinweis: Das Projekt wurde ohne Build-Umgebung geschrieben und noch nicht kompiliert. Sollte der Build einen Fehler melden, schick mir die Meldung, dann korrigiere ich es.

## 2. WireGuard auf der Fritzbox 7490 (FRITZ!OS 7.62)

1. Die Fritzbox braucht von außen eine erreichbare Adresse: `Internet > MyFRITZ-Konto` einrichten (oder DynDNS). Ohne öffentliche IPv4 (DS-Lite/CGNAT) funktioniert das nur über IPv6.
2. `Internet > Freigaben > VPN (WireGuard)` öffnen, "WireGuard-Verbindung hinzufügen".
3. "Einzelnes Gerät mit dem Heimnetz verbinden" wählen und den Anweisungen folgen. Die Fritzbox zeigt am Ende einen QR-Code.
4. Auf dem Handy die App "WireGuard" (offiziell, kostenlos) installieren und den QR-Code scannen.
5. Test: Mobilfunk an, WLAN aus, VPN in der WireGuard-App aktivieren, dann `http://fritz.box` im Browser öffnen. Wenn die Fritzbox-Oberfläche kommt, steht der Tunnel.

## 3. Kameras vorbereiten

1. In der Fritzbox unter `Heimnetz > Netzwerk` jeder Kamera eine feste IP geben ("Diese Netzwerkgeräte immer die gleiche IPv4-Adresse zuweisen").
2. In den Kameraeinstellungen prüfen, dass RTSP bzw. ONVIF aktiv ist, und ein eigenes, starkes Passwort setzen (nicht das Werkspasswort).
3. Keine Portfreigaben für die Kameras in der Fritzbox einrichten.

## 4. Kameras in der App eintragen

Tipp auf eine Kachel, dann IP-Adresse, Benutzer und Passwort eintragen. Der RTSP-Pfad ist vorbelegt mit:

    /cam/realmonitor?channel=1&subtype=%d

(`%d` wird in der App zu 0 für den Hauptstream und 1 für den Substream.) Dieser Pfad ist für LE202, LE204 und LE221 üblich. Für die LE232 habe ich keine gesicherte Angabe gefunden. Wenn sie sich nicht verbindet, den Pfad zuerst im Heimnetz mit VLC testen (Medien > Netzwerkstream öffnen, `rtsp://benutzer:passwort@IP:554/...`) und dann in der App anpassen.

## Ideen für später

- Aufnahmen abrufen (je nach Kamera über SD-Karte oder Lupus-Recorder)
- Push-Benachrichtigung bei Bewegung
- Ton einschalten (aktuell stumm)
