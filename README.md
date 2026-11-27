# EthriaAddons

EthriaAddons bündelt mehrere konfigurierbare Erweiterungen für Paper-Server.

## Funktionsumfang

- **DragonEgg:** Spezialwerkzeuge mit konfigurierbarem Namen, Lore und Custom-Model-Data. Optional lässt sich der Abbau in einer festgelegten PlotSquared-Welt auf eigene Plots beschränken.
- **LocateBiome:** Unterstützt Spielende beim Auffinden eines ausgewählten Bioms. Kosten, Abklingzeit und Berechtigung sind konfigurierbar.
- **HoloPanel:** Verwaltet Panels mit Überschrift, mehrzeiliger Beschreibung und optionalem Item. Zu jedem Panel können mehrere Positionen gespeichert werden. Eine GUI unterstützt Bearbeitung und Platzierung; Hologramme werden wahlweise über CMI, DecentHolograms oder Paper-Textanzeigen dargestellt.
- **AutoMessage:** Unterstützt sowohl serverbezogene als auch serverübergreifende (Cross-Server) Nachrichten. Bei Verwendung einer SQLite-Datenbank sind ausschließlich serverbezogene Nachrichten möglich, während eine gemeinsame MySQL-Datenbank sowohl serverbezogene als auch serverübergreifende Nachrichten ermöglicht. Der Editor bietet eine Übersicht, Vorschau, Statusumschaltung sowie die Möglichkeit, Texte und Items zu bearbeiten oder Nachrichten zu löschen. Ein Scheduler sendet aktive Nachrichten im konfigurierten Intervall entsprechend ihrer Serverzuordnung.
- **ViewNBT:** Zeigt die serialisierten Daten des Items in der Haupthand in einem Paper-Dialog an. Der Inhalt kann in die Zwischenablage kopiert werden. Mit `/viewnbt compare` öffnet sich eine GUI, in die zwei Items zum Vergleich gelegt werden; der Dialog zeigt anschließend nur die Unterschiede und bietet ebenfalls eine Kopierfunktion. Alternativ vergleicht `/viewnbt compare <Inventar-Slot>` das Item in der Haupthand direkt mit einem Inventar-Slot.
- **Konfiguration und Sprache:** Addons lassen sich einzeln aktivieren. Nachrichten sind auf Deutsch und Englisch verfügbar; Einstellungen und Sprachdateien werden beim Neuladen ergänzt.

## Konfiguration

Die Hauptkonfiguration liegt in `config.yml`. Erweiterungsspezifische Einstellungen befinden sich unter `Addons/`, Sprachdateien unter `language/`. AutoMessage und HoloPanel unterstützen SQLite und MySQL.
