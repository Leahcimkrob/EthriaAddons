package de.ethria.Commands.AutoMessageSubcommands;

import de.ethria.Config.YamlDefaults;
import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.dialog.Dialog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
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
import java.util.Base64;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class Editor implements Listener {
    private final Main plugin;
    private final DatabaseManager database;
    private final LanguageManager language;
    private YamlConfiguration config;

    public Editor(Main plugin, DatabaseManager database, LanguageManager language) {
        this.plugin = plugin;
        this.database = database;
        this.language = language;
        reloadConfig();
    }

    public void reloadConfig() {
        File file = new File(plugin.getDataFolder(), "Addons/AutoMesssage/automessageGUi.yml");
        config = YamlDefaults.loadAndUpdate(plugin, "Addons/AutoMesssage/automessageGUi.yml", file);
    }

    public void open(Player player) {
        try {
            List<DatabaseManager.AutoMessage> messages = database.getAutoMessages();
            List<String> names = messages.stream().map(DatabaseManager.AutoMessage::name).toList();
            int rows = Math.max(1, Math.min(6, config.getInt("overview.rows", 6)));
            int size = rows * 9;
            Holder holder = new Holder();
            Component title = language.message("automessage.editor-title", "names", names.isEmpty() ? "-" : String.join(", ", names));
            Inventory inventory = Bukkit.createInventory(holder, size, title);
            holder.inventory = inventory;
            List<Integer> messageSlots = config.getIntegerList("overview.layout.message-slots");
            List<Integer> statusSlots = config.getIntegerList("overview.layout.status-slots");
            if (messageSlots.size() != statusSlots.size() || messageSlots.isEmpty()) {
                messageSlots = new java.util.ArrayList<>();
                statusSlots = new java.util.ArrayList<>();
                for (int slot = 0; slot < size - 9; slot += 9) {
                    for (int column = 0; column < 9; column++) {
                        int messageSlot = slot + column;
                        int statusSlot = messageSlot + 9;
                        if (statusSlot < size - 9) {
                            messageSlots.add(messageSlot);
                            statusSlots.add(statusSlot);
                        }
                    }
                }
            }
            int capacity = messageSlots.size();
            for (int i = 0; i < Math.min(messages.size(), capacity); i++) {
                DatabaseManager.AutoMessage message = messages.get(i);
                String name = message.name();
                int messageSlot = messageSlots.get(i);
                int statusSlot = statusSlots.get(i);
                if (messageSlot < 0 || messageSlot >= size - 9 || statusSlot < 0 || statusSlot >= size - 9) continue;
                ItemStack item = decodeItem(message.itemBase64());
                if (item == null || item.getType().isAir()) item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(language.message("automessage.editor-entry", "name", name));
                meta.lore(null);
                meta.lore(language.messages("automessage.editor-lore", "name", name));
                item.setItemMeta(meta);
                inventory.setItem(messageSlot, item);
                holder.names.put(messageSlot, name);
                String statusPath = "overview.status.material-" + (message.active() ? "on" : "off");
                String statusMaterialName = config.getString(statusPath,
                        config.getString("overview.status.material_" + (message.active() ? "on" : "off"),
                                message.active() ? "GREEN_WOOL" : "RED_WOOL"));
                Material statusMaterial = Material.matchMaterial(statusMaterialName);
                if (statusMaterial == null || !statusMaterial.isItem()) statusMaterial = message.active() ? Material.GREEN_WOOL : Material.RED_WOOL;
                inventory.setItem(statusSlot, button(statusMaterial, message.active() ? "automessage.status-active" : "automessage.status-inactive"));
                holder.statusNames.put(statusSlot, name);
            }
            Material filler = resolveMaterial("filler", Material.GRAY_STAINED_GLASS_PANE);
            for (int slot = size - 9; slot < size; slot++) inventory.setItem(slot, button(filler, "automessage.empty"));
            if (messages.size() > capacity) {
                inventory.setItem(configuredSlot("button.previous.slot", size - 9, size), configuredButton("button.previous", "automessage.previous", Material.ARROW));
                inventory.setItem(configuredSlot("button.next-page.slot", size - 1, size), configuredButton("button.next-page", "automessage.next", Material.ARROW));
            }
            inventory.setItem(configuredSlot("button.close.slot", size - 5, size), configuredButton("button.close", "automessage.close", Material.BARRIER));
            player.openInventory(inventory);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load AutoMessages for editor.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ItemHolder itemHolder) {
            if (event.getClickedInventory() == event.getView().getTopInventory() && event.getRawSlot() != itemHolder.slot) event.setCancelled(true);
            else if (event.getClickedInventory() != event.getView().getTopInventory() && event.getClick().isShiftClick()) event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() == configuredSlot("button.close.slot", size - 5, size)) { player.closeInventory(); return; }
        if (event.getRawSlot() == configuredSlot("button.previous.slot", size - 9, size)
                || event.getRawSlot() == configuredSlot("button.next-page.slot", size - 1, size)) return;
        String name = holder.names.get(event.getRawSlot());
        if (name == null) {
            String statusName = holder.statusNames.get(event.getRawSlot());
            if (statusName != null) toggleActive(player, new SettingsHolder(findId(statusName), statusName), true);
            return;
        }
        if (event.getClick().isRightClick()) {
            openSettings(player, name);
        } else if (event.getClick().isShiftClick() && event.getClick().isLeftClick()) {
            view(player, name);
        }
    }

    public void view(org.bukkit.command.CommandSender sender, String name) {
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(name);
            if (message == null) {
                sender.sendMessage(language.message("automessage.not-found", "name", name));
            } else if (!database.getServerName().equalsIgnoreCase(message.serverName())) {
                sender.sendMessage(language.message("automessage.wrong-server", "server", String.valueOf(message.serverName())));
            } else if (message.description() == null || message.description().isBlank()) {
                sender.sendMessage(language.message("automessage.empty-message", "name", name));
            } else {
                String prefix = plugin.getConfig().getString("AutomessagePrefix", "");
                for (String line : message.description().split("\\n", -1)) {
                    Bukkit.broadcast(MiniMessage.miniMessage().deserialize(prefix + line));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load AutoMessage '" + name + "'.", exception);
            sender.sendMessage(language.message("automessage.database-error"));
        }
    }

    @EventHandler public void onSettingsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() == configuredSlot("button.back.slot", size - 5, size)) open(player);
        else if (event.getRawSlot() == config.getInt("detail.button.item.slot", 10)) openItemEditor(player, holder);
        else if (event.getRawSlot() == config.getInt("detail.button.edit.slot", 12)) {
            if (event.getClick().isRightClick()) openTextDialog(player, holder);
            else if (event.getClick().isLeftClick()) preview(player, holder.name);
        }
        else if (event.getRawSlot() == config.getInt("detail.button.status.slot", 14)) toggleActive(player, holder);
        else if (event.getRawSlot() == config.getInt("detail.button.delete.slot", 16)) confirmDelete(player, holder);
    }

    private void preview(Player player, String name) {
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(name);
            if (message == null) player.sendMessage(language.message("automessage.not-found", "name", name));
            else if (message.description() == null || message.description().isBlank()) player.sendMessage(language.message("automessage.empty-message", "name", name));
            else {
                player.sendMessage(language.message("automessage.preview-divider"));
                String prefix = plugin.getConfig().getString("AutomessagePrefix", "");
                for (String line : message.description().split("\\n", -1)) player.sendMessage(MiniMessage.miniMessage().deserialize(prefix + line));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not preview AutoMessage '" + name + "'.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    private void confirmDelete(Player player, SettingsHolder holder) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(io.papermc.paper.registry.data.dialog.DialogBase.builder(language.message("automessage.delete-title", "name", holder.name))
                        .canCloseWithEscape(true).build())
                .type(DialogType.confirmation(
                        ActionButton.create(language.message("automessage.delete-confirm"), null, 150,
                                DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player clicker) deleteMessage(clicker, holder);
                                }, ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(language.message("automessage.cancel"), null, 150, null))));
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) player.showDialog(dialog); });
    }

    private void deleteMessage(Player player, SettingsHolder holder) {
        try {
            database.deleteAutoMessage(holder.id);
            player.sendMessage(language.message("automessage.deleted", "name", holder.name));
            open(player);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not delete AutoMessage '" + holder.name + "'.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    @EventHandler public void onItemEditorClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ItemHolder holder)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getClickedInventory() == event.getView().getTopInventory() && event.getRawSlot() == configuredSlot("button.back.slot", size - 5, size)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                saveItemAndReturn(player, holder, event.getView().getTopInventory());
            }
            return;
        }
        if (event.getClickedInventory() == event.getView().getTopInventory() && event.getRawSlot() != holder.slot) event.setCancelled(true);
        else if (event.getClickedInventory() != event.getView().getTopInventory() && event.getClick().isShiftClick()) event.setCancelled(true);
    }

    @EventHandler public void onItemEditorClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ItemHolder holder)) return;
        if (holder.cancelSave) return;
        saveItemAndReturn(event.getPlayer() instanceof Player player ? player : null, holder, event.getInventory());
    }

    private void saveItemAndReturn(Player player, ItemHolder holder, Inventory inventory) {
        ItemStack item = inventory.getItem(holder.slot);
        String encoded = item == null || item.getType().isAir() ? null : Base64.getEncoder().encodeToString(item.serializeAsBytes());
        try {
            database.updateAutoMessageItem(holder.id, encoded);
            if (player != null && player.isOnline()) Bukkit.getScheduler().runTask(plugin, () -> openSettings(player, holder.name));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save AutoMessage item.", exception);
            if (player != null) player.sendMessage(language.message("automessage.database-error"));
        }
    }

    private void openSettings(Player player, String name) {
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(name);
            if (message == null) return;
            int rows = Math.max(2, Math.min(6, config.getInt("detail.rows", 4)));
            int size = rows * 9;
            SettingsHolder holder = new SettingsHolder(message.id(), message.name());
            Inventory inventory = Bukkit.createInventory(holder, size,
                    language.message("automessage.settings-title", "name", message.name()));
            holder.inventory = inventory;
            int itemSlot = config.getInt("detail.button.item.slot", 10);
            int textSlot = config.getInt("detail.button.edit.slot", 12);
            int deleteSlot = config.getInt("detail.button.delete.slot", 16);
            ItemStack stored = decodeItem(message.itemBase64());
            inventory.setItem(itemSlot, button(Material.CHEST, "automessage.settings-item"));
            inventory.setItem(textSlot, buttonWithLore(Material.CRAFTING_TABLE, "automessage.settings-text", "automessage.settings-text-lore"));
            inventory.setItem(deleteSlot, configuredButton("detail.button.delete", "automessage.delete-button", Material.BARRIER));
            fillButtonBar(inventory);
            inventory.setItem(configuredSlot("button.back.slot", size - 5, size), configuredButton("button.back", "automessage.back", Material.ARROW));
            player.openInventory(inventory);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load AutoMessage settings.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    private void toggleActive(Player player, SettingsHolder holder) { toggleActive(player, holder, false); }

    private void toggleActive(Player player, SettingsHolder holder, boolean returnOverview) {
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(holder.name);
            if (message == null) return;
            database.updateAutoMessageActive(holder.id, !message.active());
            if (returnOverview) open(player); else openSettings(player, holder.name);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not update AutoMessage status.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    private long findId(String name) {
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(name);
            return message == null ? -1 : message.id();
        } catch (SQLException exception) { return -1; }
    }

    private ItemStack button(Material material, String key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(language.message(key));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buttonWithLore(Material material, String key, String loreKey) {
        ItemStack item = button(material, key);
        ItemMeta meta = item.getItemMeta();
        meta.lore(language.messages(loreKey));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack configuredButton(String path, String key, Material fallback) {
        String id = config.getString(path + ".head-database-id", "");
        ItemStack item = id.isBlank() ? null : headDatabaseItem(id);
        if (item == null) item = new ItemStack(resolveMaterial(path, fallback));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(language.message(key));
            if (meta instanceof SkullMeta skull) {
                String owner = config.getString(path + ".owner", "");
                if (!owner.isBlank()) skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private int configuredSlot(String path, int fallback, int size) {
        int slot = config.getInt(path, fallback);
        return slot >= 0 && slot < size ? slot : fallback;
    }

    private Material resolveMaterial(String path, Material fallback) {
        String type = config.getString(path + ".type", "material").toLowerCase(java.util.Locale.ROOT);
        if (type.equals("player-head") || type.equals("player_head")) {
            String id = config.getString(path + ".head-database-id", "");
            ItemStack head = id.isBlank() ? null : headDatabaseItem(id);
            if (head != null) return head.getType();
        }
        String configured = config.getString(path + ".material", config.getString(path + ".fallback-material"));
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? fallback : material;
    }

    private ItemStack headDatabaseItem(String id) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("HeadDatabase")) return null;
        try {
            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object api = apiClass.getDeclaredConstructor().newInstance();
            Object item = apiClass.getMethod("getItemHead", String.class).invoke(api, id);
            return item instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not load HeadDatabase head '" + id + "': " + exception.getMessage());
            return null;
        }
    }

    private void fillButtonBar(Inventory inventory) {
        Material filler = resolveMaterial("filler", Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = inventory.getSize() - 9; slot < inventory.getSize(); slot++) inventory.setItem(slot, button(filler, "automessage.empty"));
    }

    private void openItemEditor(Player player, SettingsHolder holder) {
        int rows = Math.max(2, Math.min(6, config.getInt("item-change.rows", 4)));
        int size = rows * 9;
        int itemSlot = Math.max(0, Math.min(size - 10, config.getInt("item-change.item-slot", 13)));
        ItemHolder itemHolder = new ItemHolder(holder.id, holder.name, itemSlot);
        Inventory inventory = Bukkit.createInventory(itemHolder, size,
                language.message("automessage.item-title", "name", holder.name));
        itemHolder.inventory = inventory;
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(holder.name);
            ItemStack stored = message == null ? null : decodeItem(message.itemBase64());
            Material itemFiller = resolveMaterial("item-change.filler-material", Material.RED_STAINED_GLASS_PANE);
            for (int slot = 0; slot < size - 9; slot++) inventory.setItem(slot, button(itemFiller, "automessage.empty"));
            Material buttonFiller = resolveMaterial("filler", Material.GRAY_STAINED_GLASS_PANE);
            for (int slot = size - 9; slot < size; slot++) inventory.setItem(slot, button(buttonFiller, "automessage.empty"));
            inventory.setItem(configuredSlot("button.back.slot", size - 5, size), configuredButton("button.back", "automessage.back", Material.ARROW));
            inventory.setItem(itemSlot, stored == null ? new ItemStack(Material.PAPER) : stored);
        } catch (SQLException exception) {
            player.sendMessage(language.message("automessage.database-error"));
            return;
        }
        player.openInventory(inventory);
    }

    private void openTextDialog(Player player, SettingsHolder holder) {
        int nameWidth = Math.max(1, Math.min(1024, config.getInt("text-editor.name-width", 300)));
        int textWidth = Math.max(1, Math.min(1024, config.getInt("text-editor.message-width", 300)));
        int textHeight = Math.max(1, Math.min(512, config.getInt("text-editor.message-height", 160)));
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(holder.name);
            String currentName = message == null ? holder.name : message.name();
            String currentText = message == null || message.description() == null ? "" : message.description();
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(language.message("automessage.text-dialog-title", "name", currentName))
                            .inputs(List.of(
                                    DialogInput.text("name", nameWidth, language.message("automessage.name-label"), true, currentName, 128, null),
                                    DialogInput.text("message", textWidth, language.message("automessage.message-label"), true,
                                            currentText, 2048, TextDialogInput.MultilineOptions.create(null, textHeight))))
                            .canCloseWithEscape(true).build())
                    .type(DialogType.confirmation(
                            ActionButton.create(language.message("automessage.save"), null, 150,
                                    DialogAction.customClick((response, audience) -> {
                                        if (audience instanceof Player clicker) saveText(clicker, holder, response.getText("name"), response.getText("message"));
                                    }, ClickCallback.Options.builder().uses(1).build())),
                            ActionButton.create(language.message("automessage.cancel"), null, 150,
                                    DialogAction.customClick((response, audience) -> {
                                        if (audience instanceof Player clicker) openSettings(clicker, holder.name);
                                    }, ClickCallback.Options.builder().uses(1).build())))));
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) player.showDialog(dialog); });
        } catch (SQLException exception) {
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    private void saveText(Player player, SettingsHolder holder, String name, String text) {
        try {
            String normalized = name == null || name.isBlank() ? holder.name : name.trim();
            database.updateAutoMessageName(holder.id, normalized);
            database.updateAutoMessageText(holder.id, text == null ? "" : text);
            player.sendMessage(language.message("automessage.settings-saved"));
            openSettings(player, normalized);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save AutoMessage settings.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    private ItemStack decodeItem(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try { return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)); }
        catch (IllegalArgumentException exception) { return null; }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder || event.getView().getTopInventory().getHolder() instanceof SettingsHolder) event.setCancelled(true);
        else if (event.getView().getTopInventory().getHolder() instanceof ItemHolder holder
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize() && slot != holder.slot)) event.setCancelled(true);
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, String> names = new HashMap<>();
        final Map<Integer, String> statusNames = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class SettingsHolder implements InventoryHolder {
        final long id; final String name; Inventory inventory;
        SettingsHolder(long id, String name) { this.id = id; this.name = name; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class ItemHolder implements InventoryHolder {
        final long id; final String name; final int slot; Inventory inventory; boolean cancelSave;
        ItemHolder(long id, String name, int slot) { this.id = id; this.name = name; this.slot = slot; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
