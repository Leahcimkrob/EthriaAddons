package de.ethria.Commands.AutoMessageSubcommands;

import de.ethria.main.Main;
import de.ethria.Language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;

import java.io.IOException;
import java.util.List;

public final class Config implements Listener {
    private final Main plugin;
    private final LanguageManager language;
    private final YamlConfiguration gui;

    public Config(Main plugin, LanguageManager language, YamlConfiguration gui) {
        this.plugin = plugin; this.language = language; this.gui = gui;
    }

    public void open(Player player) {
        ConfigHolder holder = new ConfigHolder();
        int rows = Math.max(3, Math.min(6, gui.getInt("config.rows", 4)));
        int size = rows * 9;
        Inventory inventory = Bukkit.createInventory(holder, size, language.message("automessage.config-title"));
        int interval = Math.max(1, plugin.autoMessageConfig().getInt("AutomessageInterval", 30));
        String mode = plugin.autoMessageConfig().getString("AutomessageView", "RANDOM");
        inventory.setItem(gui.getInt("config.interval-slot", 11), setting(Material.CLOCK, "automessage.config-interval", Integer.toString(interval)));
        inventory.setItem(gui.getInt("config.view-slot", 15), setting(Material.SPYGLASS, "automessage.config-view", mode));
        int defaultTextSlot = gui.getInt("config.default-text-slot", 13);
        inventory.setItem(defaultTextSlot, settingLore(Material.PAPER, "automessage.config-default-text",
                plugin.autoMessageConfig().getStringList("AutomessageDefaultText")));
        Material filler = material("filler.material", Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = size - 9; slot < size; slot++) inventory.setItem(slot, named(filler, "automessage.empty"));
        int closeSlot = gui.getInt("button.close.slot", size - 5);
        if (closeSlot < size - 9 || closeSlot >= size) closeSlot = size - 5;
        inventory.setItem(closeSlot, configuredButton("button.close", "automessage.close", Material.BARRIER));
        player.openInventory(inventory);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ConfigHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == gui.getInt("config.interval-slot", 11)) {
            int current = Math.max(1, plugin.autoMessageConfig().getInt("AutomessageInterval", 30));
            int next = event.isRightClick() ? Math.max(1, current - 1) : current + 1;
            save("AutomessageInterval", next);
            open(player);
        } else if (event.getRawSlot() == gui.getInt("config.view-slot", 15)) {
            List<String> modes = List.of("RANDOM", "ASC", "DESC");
            String current = plugin.autoMessageConfig().getString("AutomessageView", "RANDOM").toUpperCase();
            int idx = modes.indexOf(current);
            int next = event.isRightClick() ? Math.floorMod(idx - 1, modes.size()) : (idx + 1) % modes.size();
            save("AutomessageView", modes.get(next));
            open(player);
        } else if (event.getRawSlot() == gui.getInt("config.default-text-slot", 13)) {
            openDefaultTextDialog(player);
        } else if (event.getRawSlot() == closeSlot(event.getView().getTopInventory().getSize())) player.closeInventory();
    }

    private void openDefaultTextDialog(Player player) {
        List<String> currentLines = plugin.autoMessageConfig().getStringList("AutomessageDefaultText");
        int width = Math.max(1, Math.min(1024, gui.getInt("config.default-text-width", 300)));
        int height = Math.max(1, Math.min(512, gui.getInt("config.default-text-height", 120)));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(language.message("automessage.config-default-text-title"))
                        .inputs(List.of(DialogInput.text("text", width,
                                language.message("automessage.config-default-text"), true,
                                String.join("\n", currentLines), 4096,
                                TextDialogInput.MultilineOptions.create(null, height))))
                        .canCloseWithEscape(true).build())
                .type(DialogType.confirmation(
                        ActionButton.create(language.message("automessage.save"), null, 150,
                                DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player clicker) {
                                        String text = response.getText("text");
                                        save("AutomessageDefaultText", text == null || text.isEmpty() ? List.of() : List.of(text.split("\\R", -1)));
                                        open(clicker);
                                    }
                                }, ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(language.message("automessage.cancel"), null, 150,
                                DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player clicker) open(clicker);
                                }, ClickCallback.Options.builder().uses(1).build())))));
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) player.showDialog(dialog); });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigHolder) event.setCancelled(true);
    }

    private void save(String path, Object value) {
        plugin.autoMessageConfig().set(path, value);
        try {
            plugin.autoMessageConfig().save(plugin.autoMessageConfigFile());
            plugin.reloadAutoMessageConfig();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save AutoMessage setting " + path + ": " + exception.getMessage());
        }
    }

    private ItemStack setting(Material material, String key, String value) {
        ItemStack item = named(material, key);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(language.message("automessage.config-current", "value", value)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack settingLore(Material material, String key, List<String> lines) {
        ItemStack item = named(material, key);
        ItemMeta meta = item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(language.messages("automessage.config-default-text-lore"));
        lore.addAll(lines.stream()
                .map(line -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line))
                .toList());
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, String key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta(); meta.displayName(language.message(key)); item.setItemMeta(meta); return item;
    }

    private Material material(String path, Material fallback) {
        Material result = Material.matchMaterial(gui.getString(path, fallback.name()));
        return result == null ? fallback : result;
    }

    private int closeSlot(int size) {
        int slot = gui.getInt("button.close.slot", size - 5);
        return slot >= size - 9 && slot < size ? slot : size - 5;
    }

    private ItemStack configuredButton(String path, String key, Material fallback) {
        ItemStack item = null;
        String headId = gui.getString(path + ".head-database-id", "");
        if (!headId.isBlank() && plugin.getServer().getPluginManager().isPluginEnabled("HeadDatabase")) {
            try {
                Class<?> api = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
                Object result = api.getMethod("getItemHead", String.class).invoke(api.getDeclaredConstructor().newInstance(), headId);
                if (result instanceof ItemStack stack) item = stack;
            } catch (ReflectiveOperationException | LinkageError exception) {
                plugin.getLogger().warning("Could not load HeadDatabase button '" + headId + "': " + exception.getMessage());
            }
        }
        String type = gui.getString(path + ".type", "material");
        if (item == null && "itemsadder".equalsIgnoreCase(type) && plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object stack = api.getMethod("getInstance", String.class).invoke(null, gui.getString(path + ".item-id", ""));
                if (stack != null) item = (ItemStack) api.getMethod("getItemStack").invoke(stack);
            } catch (ReflectiveOperationException | LinkageError exception) {
                plugin.getLogger().warning("Could not load ItemsAdder button at " + path + ": " + exception.getMessage());
            }
        }
        if (item == null) item = new ItemStack(material(path + ".material", material(path + ".fallback-material", fallback)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(language.message(key));
            if (meta instanceof SkullMeta skull) {
                String owner = gui.getString(path + ".owner", "");
                if (!owner.isBlank()) skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class ConfigHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
