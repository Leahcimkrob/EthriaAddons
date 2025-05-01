package de.ethria.Config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.ArrayList;

public class BarrierBedrock {

    private ArrayList<String> FBarrierBedrockMaterial = new ArrayList<>();
    private ArrayList<String> FBarrierBedrockID = new ArrayList<>();
    private ArrayList<String> FBarrierBedrockName = new ArrayList<>();
    private ArrayList<String> FBarrierBedrockLore = new ArrayList<>();


    private String folderPath = "plugins/EthriaAddons/Addons";
    private String configName = "barrierbedrock.yml";

    public BarrierBedrock() {
    }

    public BarrierBedrock(ArrayList<String> fBarrierBedrockMaterial, ArrayList<String> fBarrierBedrockID, ArrayList<String> fBarrierBedrockName, ArrayList<String> fBarrierBedrockLore) {
        this.FBarrierBedrockMaterial = fBarrierBedrockMaterial;
        this.FBarrierBedrockID = fBarrierBedrockID;
        this.FBarrierBedrockName = fBarrierBedrockName;
        this.FBarrierBedrockLore = fBarrierBedrockLore;
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
                writer.println("  NAME: '&e&lTriforce'");
                writer.println("  ID: '3300003'");
                writer.println("  Lore: '&7Kann &cBarrieren &7und &cBedrock &7abbauen'");
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

                    FBarrierBedrockMaterial.add(currentMaterial);
                    FBarrierBedrockName.add(currentName);
                    FBarrierBedrockID.add(currentID);
                    FBarrierBedrockLore.add(currentLore);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
