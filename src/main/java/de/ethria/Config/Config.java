package de.ethria.Config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Config implements Listener {

    private String folderPath = "plugins/EthriaAddons";
    private String folderPathBackup = "plugins/EthriaAddons/backups";
    private String configName = "config.yml";
    private boolean Backup;

    public Config() {

    }

    public void createFolder() {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
        }
    }

    public void createYML() {
        File file = new File(folderPath, configName);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if(!file.exists()) {
            try (FileWriter fw = new FileWriter(file)) {
                fw.write("# Dies ist der Prefix, der vor allen Plugin-Nachrichten angezeigt wird. #\n");
                fw.write("Prefix: '&9&lEthria &8≫ '\n\n");
                fw.write("# Ab hier kann festgelegt werden, felche Addons aktiviert sein sollen. #\n");
                fw.write("# (true = an | false = aus) #\n");
                fw.write("DragonEgg: 'true'\n");
                fw.write("BarrierBedrock: 'true'\n");

                fw.write("# PlotSquared-Check #\n");
                fw.write("PlotSquaredCheck: 'true'\n");
                fw.write("# Bitte gebe hier die Plotwelt an. #\n");
                fw.write("PlotSquaredWorld: 'world'\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                List<String> lines = Files.readAllLines(file.toPath(), Charset.defaultCharset());

                boolean hasPrefix = false;
                boolean hasDragonEgg = false;
                boolean hasBarrierBedrock = false;
                boolean hasPlotSquaredCheck = false;
                boolean hasPlotSquaredWorld = false;

                for (String line : lines) {
                    if (!line.trim().startsWith("#")) {
                        if (line.startsWith("Prefix:")) {
                            hasPrefix = true;
                        } else if (line.startsWith("DragonEgg:")) {
                            hasDragonEgg = true;
                        } else if (line.startsWith("BarrierBedrock:")) {
                            hasBarrierBedrock = true;
                        } else if (line.startsWith("PlotSquaredCheck:")) {
                            hasPlotSquaredCheck = true;
                        } else if (line.startsWith("PlotSquaredWorld:")) {
                            hasPlotSquaredWorld = true;
                        }
                    }
                }

                if (!hasPrefix) {
                    String wert = Backup("Prefix");
                    String value = "Prefix: '" + wert + "'\n\n";
                    write(file, value);
                }
                if (!hasDragonEgg) {
                    String wert = Backup("DragonEgg");
                    String value1 = "DragonEgg: '" + wert + "'\n\n";
                    write(file, value1);
                }
                if (!hasBarrierBedrock) {
                    String wert = Backup("BarrierBedrock");
                    String value1 = "BarrierBedrock: '" + wert + "'\n\n";
                    write(file, value1);
                }
                if (!hasPlotSquaredCheck) {
                    String wert = Backup("PlotSquaredCheck");
                    String value1 = "PlotSquaredCheck: '" + wert + "'\n";
                    write(file, value1);
                }
                if (!hasPlotSquaredWorld) {
                    String wert = Backup("PlotSquaredWorld");
                    String value1 = "PlotSquaredWorld: '" + wert + "'\n";
                    write(file, value1);
                }
                EndBackup();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String ReadYML(String wert) {
        File configPath = new File(folderPath, configName);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configPath);

        if (config.isString(wert)) {
            String rawValue = config.getString(wert);
            if (rawValue != null) {
                return ChatColor.translateAlternateColorCodes('&', rawValue);
            }
        }
        return null;
    }

    private String Backup(String wert) {
        if (!Backup) {
            Backup = true;
            File folderBackup = new File(folderPathBackup);
            if (!folderBackup.exists()) {
                boolean created = folderBackup.mkdirs();
            }
            LocalDateTime currentTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
            String backupTime = currentTime.format(formatter);
            String backupName = "Backup_" + configName + "_" + backupTime + ".yml";
            File backupFile = new File(folderPathBackup, backupName);
            File originalFile = new File(folderPath, configName);

            if (originalFile.exists()) {
                try (InputStream in = Files.newInputStream(originalFile.toPath());
                     OutputStream out = Files.newOutputStream(backupFile.toPath())) {

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }

                    try (FileWriter writer = new FileWriter(backupFile, true)) {
                        String value1 = "\n\n\n########################################################################################\n";
                        String value2 = "#####          Dieses Backup wurde aufgrund eines fehlenden Wert erstellt.         #####\n";
                        String value3 = "##### Es dient lediglich als Sicherung falls Einstellungen verloren gehen sollten. #####\n";
                        String value4 = "########################################################################################\n";
                        writer.write(value1);
                        writer.write(value2);
                        writer.write(value3);
                        writer.write(value4);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write additional information to the backup file.");
                    }

                } catch (IOException e) {
                    throw new RuntimeException("Failed to create a backup.");
                }
            } else {
                throw new RuntimeException("Original file does not exist.");
            }
        }
        String wertContent = ReadYML(wert);
        if(wertContent == null) {
            switch (wert) {
                case "Prefix":
                    wertContent = "&9&lEthria &8≫ ";
                    break;
                case "DragonEgg":
                    wertContent = "true";
                    break;
                case "BarrierBedrock":
                    wertContent = "true";
                    break;
                case "PlotSquaredCheck":
                    wertContent = "true";
                    break;
                case "PlotSquaredWorld":
                    wertContent = "world";
                    break;
            }
        }
        return wertContent;
    }

    private void EndBackup() {
        if(Backup) {
            Backup = false;
        }
    }

    private void write(File file, String value) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
