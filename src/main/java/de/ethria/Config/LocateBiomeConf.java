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

public class LocateBiomeConf implements Listener {

    private String folderPath = "plugins/EthriaAddons/Addons";
    private String configName = "locatebiome.yml";
    private String folderPathBackup = "plugins/EthriaAddons/backups";
    private boolean Backup;

    public LocateBiomeConf() {
        createFolder();
        createYML();
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
                fw.write("# Einstellungen für LocateBiome #\n");
                fw.write("# (true = an | false = aus) #\n");
                fw.write("Enabled: 'true'\n");
                fw.write("Perm: 'system.ethria.locatebiome'\n");
                fw.write("Money: '100000'\n");
                fw.write("# Timeout (Zeit in Minuten) #\n");
                fw.write("Timeout: '180'\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                List<String> lines = Files.readAllLines(file.toPath(), Charset.defaultCharset());

                boolean hasEnabled = false;
                boolean hasPerm = false;
                boolean hasMoney = false;
                boolean hasTimeout = false;

                for (String line : lines) {
                    if (!line.trim().startsWith("#")) {
                        if (line.startsWith("Enabled:")) {
                            hasEnabled = true;
                        } else if (line.startsWith("Perm:")) {
                            hasPerm = true;
                        } else if (line.startsWith("Money:")) {
                            hasMoney = true;
                        } else if (line.startsWith("Timeout:")) {
                            hasTimeout = true;
                        }
                    }
                }

                if (!hasEnabled) {
                    String wert = Backup("Enabled");
                    String value = "Enabled: '" + wert + "'\n";
                    write(file, value);
                }
                if (!hasPerm) {
                    String wert = Backup("Perm");
                    String value1 = "Perm: '" + wert + "'\n";
                    write(file, value1);
                }
                if (!hasMoney) {
                    String wert = Backup("Money");
                    String value1 = "Money: '" + wert + "'\n";
                    write(file, value1);
                }
                if (!hasTimeout) {
                    String wert = Backup("Timeout");
                    String value1 = "# Timeout (Zeit in Minuten) #\n";
                    String value2 = "Timeout: '" + wert + "'\n";
                    write(file, value1);
                    write(file, value2);
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
                case "Enabled":
                    wertContent = "true";
                    break;
                case "Perm":
                    wertContent = "system.ethria.locatebiome";
                    break;
                case "Money":
                    wertContent = "100000";
                    break;
                case "Timeout":
                    wertContent = "180";
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
