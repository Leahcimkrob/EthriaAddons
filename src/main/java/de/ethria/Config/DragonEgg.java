package de.ethria.Config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.ArrayList;

public class DragonEgg {

    private ArrayList<String> FDragonEggMaterial = new ArrayList<>();
    private ArrayList<String> FDragonEggID = new ArrayList<>();
    private ArrayList<String> FDragonEggName = new ArrayList<>();
    private ArrayList<String> FDragonEggLore = new ArrayList<>();


    private String folderPath = "plugins/EthriaAddons/Addons";
    private String configName = "dragonegg.yml";

    public DragonEgg() {
    }

    public DragonEgg(ArrayList<String> fDragonEggMaterial, ArrayList<String> fDragonEggID, ArrayList<String> fDragonEggName, ArrayList<String> fDragonEggLore) {
        this.FDragonEggMaterial = fDragonEggMaterial;
        this.FDragonEggID = fDragonEggID;
        this.FDragonEggName = fDragonEggName;
        this.FDragonEggLore = fDragonEggLore;
    }

    public void createFolder() {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
        }
    }

    public void createConfig() {
        File file = new File(folderPath, configName);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        if(!file.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("# Einstellungen für das Abbau-Item #");
                writer.println("SHEARS:");
                writer.println("  NAME: '&f&l&kX &d&lDragon-Egg-Schneider &f&l&kX'");
                writer.println("  ID: '1'");
                writer.println("  Lore: '&5Dragon-Egg-Schneider'");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void readConfig() {
        File file = new File(folderPath, configName);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            String currentMaterial = "";
            String currentName = "";
            String currentID = "";
            String currentLore = "";

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                if (line.endsWith(":")) {
                    currentMaterial = line.substring(0, line.length() - 1);
                } else if (line.startsWith("  NAME:")) {
                    currentName = line.substring(line.indexOf("'") + 1, line.lastIndexOf("'"));
                } else if (line.startsWith("  ID:")) {
                    currentID = line.substring(line.indexOf("'") + 1, line.lastIndexOf("'"));
                } else if (line.startsWith("  Lore:")) {
                    currentLore = line.substring(line.indexOf("'") + 1, line.lastIndexOf("'"));

                    FDragonEggMaterial.add(currentMaterial);
                    FDragonEggName.add(currentName);
                    FDragonEggID.add(currentID);
                    FDragonEggLore.add(currentLore);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
