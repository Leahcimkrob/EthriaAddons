# EthriaAddons

EthriaAddons bündelt mehrere konfigurierbare Erweiterungen für Paper-Server.

## Funktionsumfang

- **DragonEgg:** Spezialwerkzeuge mit konfigurierbarem Namen, Lore und Custom-Model-Data. Optional lässt sich der Abbau in einer festgelegten PlotSquared-Welt auf eigene Plots beschränken.
- **LocateBiome:** Unterstützt Spielende beim Auffinden eines ausgewählten Bioms. Kosten, Abklingzeit und Berechtigung sind konfigurierbar.
- **HoloPanel:** Verwaltet Panels mit Überschrift, mehrzeiliger Beschreibung und optionalem Item. Zu jedem Panel können mehrere Positionen gespeichert werden. Eine GUI unterstützt Bearbeitung und Platzierung; Hologramme werden wahlweise über CMI, DecentHolograms oder Paper-Textanzeigen dargestellt.
- **AutoMessage:** Speichert Nachrichten serverbezogen in einer eigenen SQLite- oder MySQL-Datenbank. Der Editor bietet Übersicht, Vorschau, Statusumschaltung, Text- und Itembearbeitung sowie Löschen. Ein Scheduler sendet aktive Nachrichten im konfigurierten Intervall.
- **Konfiguration und Sprache:** Addons lassen sich einzeln aktivieren. Nachrichten sind auf Deutsch und Englisch verfügbar; Einstellungen und Sprachdateien werden beim Neuladen ergänzt.

## Konfiguration

Die Hauptkonfiguration liegt in `config.yml`. Erweiterungsspezifische Einstellungen befinden sich unter `Addons/`, Sprachdateien unter `language/`. AutoMessage und HoloPanel unterstützen SQLite und MySQL.
