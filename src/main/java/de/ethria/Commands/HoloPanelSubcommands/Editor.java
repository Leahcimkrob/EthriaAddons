package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Database.DatabaseManager;
import de.ethria.Database.DatabaseManager.Panel;
import de.ethria.Database.DatabaseManager.PanelLocation;
import de.ethria.Config.HoloPanelConfig;
import de.ethria.Language.LanguageManager;
import de.ethria.HoloPanel.HoloPanelEngine;
import de.ethria.HoloPanel.HoloPanelText;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;

/** HoloPanel overview, detail editor, and location tools. */
public final class Editor implements Listener {
    private static final int OVERVIEW_SIZE = 54;
    private static final int DETAIL_SIZE = 36;
    private static final double WALL_GAP = 0.05;
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final LanguageManager language;
    private final HoloPanelEngine engine;
    private YamlConfiguration config;
    private final NamespacedKey toolTypeKey;
    private final NamespacedKey toolPanelIdKey;
    private final Map<UUID, PendingSelection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, PendingPanelDelete> pendingDeletes = new ConcurrentHashMap<>();
    private final Set<UUID> toolUsers = ConcurrentHashMap.newKeySet();

    public Editor(JavaPlugin plugin, DatabaseManager database, LanguageManager language, HoloPanelEngine engine) {
        this.plugin = plugin;
        this.database = database;
        this.language = language;
        this.engine = engine;
        this.config = loadGuiConfig();
        toolTypeKey = new NamespacedKey(plugin, "holopanel_editor_tool");
        toolPanelIdKey = new NamespacedKey(plugin, "holopanel_editor_id");
    }

    public void reloadGuiConfig() {
        config = loadGuiConfig();
    }

    private YamlConfiguration loadGuiConfig() {
        return new HoloPanelConfig(plugin).loadGuiConfig();
    }

    public void openOverview(Player player, int requestedPage) {
        try {
            List<Panel> panels = database.getPanels();
            int overviewSize = guiSize("overview.rows", OVERVIEW_SIZE, 2);
            int pageCapacity = overviewSize - 9;
            int pageCount = Math.max(1, (panels.size() + pageCapacity - 1) / pageCapacity);
            int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
            OverviewHolder holder = new OverviewHolder(page);
            Inventory inventory = Bukkit.createInventory(holder, overviewSize, language.message("holopanel.overview-title"));
            holder.inventory = inventory;
            fillButtonBar(inventory);
            int start = page * pageCapacity;
            for (int index = start; index < Math.min(start + pageCapacity, panels.size()); index++) {
                Panel panel = panels.get(index);
                int slot = index - start;
                ItemStack icon = decodeItem(panel.itemBase64());
                if (icon == null) icon = new ItemStack(Material.ARMOR_STAND);
                ItemMeta meta = icon.getItemMeta();
                meta.displayName(language.message("holopanel.overview-entry", "name", panel.name()));
                meta.lore(List.of(language.message("holopanel.overview-lore")));
                icon.setItemMeta(meta);
                inventory.setItem(slot, icon);
                holder.panelsBySlot.put(slot, panel.id());
            }
            if (panels.isEmpty()) inventory.setItem(pageCapacity / 2, named(Material.BARRIER, "holopanel.empty"));
            setButton(inventory, "button.close", "close");
            if (page > 0) setButton(inventory, "button.previous", "previous");
            if (page + 1 < pageCount) setButton(inventory, "button.next-page", "next");
            player.openInventory(inventory);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load HoloPanels for the editor.", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    private void openDetail(Player player, long id, int page) {
        try {
            Panel panel = database.getPanel(id);
            if (panel == null) {
                player.sendMessage(language.message("holopanel.not-found"));
                openOverview(player, page);
                return;
            }
            DetailHolder holder = new DetailHolder(panel.id(), page);
            Inventory inventory = Bukkit.createInventory(holder, guiSize("detail.rows", DETAIL_SIZE, 4),
                    language.message("holopanel.detail-title", "name", panel.name()));
            holder.inventory = inventory;
            fillButtonBar(inventory);
            setActionItem(inventory, "detail.button.item", "holopanel.action-item");
            setActionItem(inventory, "detail.button.edit", "holopanel.action-edit");
            setActionItem(inventory, "detail.button.delete", "holopanel.action-delete");
            setActionItem(inventory, "detail.button.tools", "holopanel.action-tools");
            setSharedButton(inventory, "back");
            player.openInventory(inventory);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load HoloPanel " + id + " for editing.", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    private void openItemEditor(Player player, Panel panel, int page) {
        ItemHolder holder = new ItemHolder(panel.id(), page);
        int size = guiSize("item-change.rows", 27, 2);
        int contentSize = size - 9;
        int itemSlot = configuredSlot("item-change.item-slot", contentSize / 2, contentSize);
        holder.itemSlot = itemSlot;
        Inventory inventory = Bukkit.createInventory(holder, size,
                language.message("holopanel.item-title", "name", panel.name()));
        holder.inventory = inventory;
        Material fillerMaterial = Material.matchMaterial(config.getString("item-change.filler-material", "GRAY_STAINED_GLASS_PANE"));
        if (fillerMaterial == null || !fillerMaterial.isItem()) fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack filler = named(fillerMaterial, "holopanel.empty-name");
        for (int slot = 0; slot < size; slot++) if (slot != itemSlot) inventory.setItem(slot, filler.clone());
        ItemStack stored = decodeItem(panel.itemBase64());
        if (stored != null) inventory.setItem(itemSlot, stored);
        fillButtonBar(inventory);
        setSharedButton(inventory, "back");
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ItemHolder holder) {
            if (event.getClickedInventory() == top && event.getWhoClicked() instanceof Player player) {
                int size = top.getSize();
                int backSlot = configuredSlot("button.back", defaultSlot("button.back", size), size);
                if (event.getRawSlot() == backSlot) {
                    event.setCancelled(true);
                    player.closeInventory();
                    return;
                }
            }
            if (event.getClickedInventory() == top && event.getRawSlot() != holder.itemSlot) event.setCancelled(true);
            else if (event.getClickedInventory() != top && event.getClick().isShiftClick()) event.setCancelled(true);
            return;
        }
        if (!(top.getHolder() instanceof EditorHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != top) return;
        int slot = event.getRawSlot();
        if (holder instanceof OverviewHolder overview) {
            int overviewSize = guiSize("overview.rows", OVERVIEW_SIZE, 2);
            if (slot == configuredSlot("button.close", overviewSize - 5, overviewSize)) player.closeInventory();
            else if (slot == configuredSlot("button.previous", overviewSize - 9, overviewSize) && overview.page > 0) openOverview(player, overview.page - 1);
            else if (slot == configuredSlot("button.next-page", overviewSize - 1, overviewSize)) openOverview(player, overview.page + 1);
            else if (overview.panelsBySlot.containsKey(slot)) openDetail(player, overview.panelsBySlot.get(slot), overview.page);
            return;
        }
        DetailHolder detail = (DetailHolder) holder;
        int detailSize = guiSize("detail.rows", DETAIL_SIZE, 4);
        if (slot == configuredSlot("button.back", defaultSlot("button.back", detailSize), detailSize)) openOverview(player, detail.page);
        else if (slot == configuredSlot("detail.button.item", 10, detailSize)) loadPanel(detail.id, panel -> openItemEditor(player, panel, detail.page), player);
        else if (slot == configuredSlot("detail.button.edit", 12, detailSize)) loadPanel(detail.id, panel -> openTextDialog(player, panel, detail.page), player);
        else if (slot == configuredSlot("detail.button.delete", 14, detailSize)) beginDeleteConfirmation(player, detail, true);
        else if (slot == configuredSlot("detail.button.tools", 16, detailSize)) {
            if (event.getClick().isLeftClick()) giveTool(player, detail.id, (byte) 1);
            else if (event.getClick().isRightClick()) giveTool(player, detail.id, (byte) 2);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ItemHolder holder)) return;
        ItemStack item = event.getView().getTopInventory().getItem(holder.itemSlot);
        String serialized = item == null || item.getType().isAir() || isPlaceholder(item)
                ? null : Base64.getEncoder().encodeToString(item.serializeAsBytes());
        try {
            database.updatePanelItem(holder.id, serialized);
            event.getPlayer().sendMessage(language.message("holopanel.item-saved"));
            if (event.getPlayer() instanceof Player player && player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> openDetail(player, holder.id, holder.page));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save HoloPanel item " + holder.id + ".", exception);
            event.getPlayer().sendMessage(language.message("holopanel.database-error"));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ItemHolder holder)) return;
        int size = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < size && slot != holder.itemSlot)) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isTool(event.getItem())) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;
        byte type = event.getItem().getItemMeta().getPersistentDataContainer().get(toolTypeKey, PersistentDataType.BYTE);
        long id = event.getItem().getItemMeta().getPersistentDataContainer().get(toolPanelIdKey, PersistentDataType.LONG);
        if (type == 2) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) deletePositionAt(player, id, block.getLocation());
            return;
        }
        PendingSelection selection = selections.get(player.getUniqueId());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selections.put(player.getUniqueId(), new PendingSelection(id, block.getLocation()));
            player.sendMessage(language.message("holopanel.first-corner", "x", String.valueOf(block.getX()),
                    "y", String.valueOf(block.getY()), "z", String.valueOf(block.getZ())));
            return;
        }
        if (selection == null || selection.id != id) {
            player.sendMessage(language.message("holopanel.select-first-corner"));
            return;
        }
        if (!selection.first.getWorld().equals(block.getWorld())) {
            player.sendMessage(language.message("holopanel.same-world"));
            return;
        }
        Placement placement = calculatePlacement(selection.first, block.getLocation(), player.getLocation());
        if (placement == null) {
            player.sendMessage(language.message("holopanel.invalid-area"));
            return;
        }
        try {
            long positionId = database.addPanelLocation(id, database.getServerName(), placement.location().getWorld().getName(),
                    placement.location().getX(), placement.location().getY(), placement.location().getZ(),
                    placement.location().getYaw(), placement.location().getPitch(), placement.widthBlocks(), placement.heightBlocks());
            Panel panel = database.getPanel(id);
            if (panel != null) spawnPanel(panel, positionId, placement.location(), placement.widthBlocks(), placement.heightBlocks());
            selections.remove(player.getUniqueId());
            player.sendMessage(language.message("holopanel.position-saved", "name", panel == null ? "" : panel.name()));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not update HoloPanel location " + id + ".", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
        pendingDeletes.remove(event.getPlayer().getUniqueId());
        toolUsers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChatExit(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String response = event.getMessage().trim();
        PendingPanelDelete pendingDelete = pendingDeletes.get(player.getUniqueId());
        if (pendingDelete != null && (response.equalsIgnoreCase("confirm") || response.equalsIgnoreCase("cancel"))) {
            event.setCancelled(true);
            boolean confirm = response.equalsIgnoreCase("confirm");
            Bukkit.getScheduler().runTask(plugin,
                    () -> handleDeleteConfirmation(player, pendingDelete.id, confirm));
            return;
        }

        String exitWord = config.getString("tools.exit-word", "exit").trim();
        if (exitWord.isEmpty() || !response.equalsIgnoreCase(exitWord)) return;

        if (!toolUsers.contains(player.getUniqueId()) && !selections.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            selections.remove(player.getUniqueId());
            removeAllTools(player);
            toolUsers.remove(player.getUniqueId());
            player.sendMessage(language.message("holopanel.tool-cancelled"));
        });
    }

    private void openTextDialog(Player player, Panel panel, int page) {
        String currentHeadline = panel.headline() == null ? "" : panel.headline();
        String currentDescription = panel.description() == null ? "" : panel.description();
        int descriptionHeight = Math.max(1, Math.min(512, config.getInt("text-editor.description-height", 160)));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(language.message("holopanel.text-dialog-title", "name", panel.name()))
                        .inputs(List.of(
                                DialogInput.text("headline", 300, language.message("holopanel.headline-label"), true,
                                        currentHeadline, 128, null),
                                DialogInput.text("description", 300, language.message("holopanel.description-label"), true,
                                        currentDescription, 2048, TextDialogInput.MultilineOptions.create(null, descriptionHeight))))
                        .canCloseWithEscape(true).build())
                .type(DialogType.confirmation(
                        ActionButton.create(language.message("holopanel.text-save"), null, 150,
                                DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player clicker) savePanelText(clicker, panel.id(), page,
                                            response.getText("headline"), response.getText("description"));
                                }, ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(language.message("holopanel.text-cancel"), null, 150, null))));
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.showDialog(dialog);
        });
    }

    private void savePanelText(Player player, long id, int page, String headline, String description) {
        try {
            database.updatePanelText(id, headline == null ? "" : headline, description == null ? "" : description);
            Panel panel = database.getPanel(id);
            if (panel != null) renderStoredPanel(panel);
            player.sendMessage(language.message("holopanel.text-saved"));
            openDetail(player, id, page);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save HoloPanel text " + id + ".", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    private void beginDeleteConfirmation(Player player, DetailHolder detail, boolean returnToGui) {
        try {
            Panel panel = database.getPanel(detail.id);
            if (panel == null) {
                player.sendMessage(language.message("holopanel.not-found"));
                openOverview(player, detail.page);
                return;
            }
            pendingDeletes.put(player.getUniqueId(), new PendingPanelDelete(detail.id, detail.page, returnToGui,
                    System.currentTimeMillis() + 30_000L));
            Component confirmation = language.message("holopanel.delete-confirmation", "name", panel.name())
                    .append(Component.space())
                    .append(language.message("holopanel.delete-confirm-button")
                            .clickEvent(ClickEvent.runCommand("/holopanel confirm " + detail.id)))
                    .append(Component.space())
                    .append(language.message("holopanel.delete-cancel-button")
                            .clickEvent(ClickEvent.runCommand("/holopanel cancel " + detail.id)));
            player.sendMessage(confirmation);
            player.sendMessage(language.message("holopanel.delete-confirm-command", "id", String.valueOf(detail.id)));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not prepare HoloPanel deletion " + detail.id + ".", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    public void beginDeleteConfirmation(Player player, String panelName) {
        try {
            Panel panel = findPanel(panelName);
            if (panel == null) {
                player.sendMessage(language.message("holopanel.not-found"));
                return;
            }
            beginDeleteConfirmation(player, new DetailHolder(panel.id(), 0), false);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not find HoloPanel '" + panelName + "' for deletion.", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    public void givePanelTool(Player player, String panelName, byte type) {
        try {
            Panel panel = findPanel(panelName);
            if (panel == null) {
                player.sendMessage(language.message("holopanel.not-found"));
                return;
            }
            giveTool(player, panel.id(), type);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not find HoloPanel '" + panelName + "' for its tool.", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    public void reloadHolograms(CommandSender sender) {
        try {
            engine.restoreAll();
            sender.sendMessage(language.message("holopanel.reloaded"));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not reload saved HoloPanels.", exception);
            sender.sendMessage(language.message("holopanel.database-error"));
        }
    }

    private Panel findPanel(String name) throws SQLException {
        return database.getPanels().stream()
                .filter(panel -> panel.name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public List<String> panelNames() {
        try {
            return database.getPanels().stream().map(Panel::name).toList();
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not list HoloPanels for command completion.", exception);
            return List.of();
        }
    }

    public void handleDeleteConfirmation(Player player, long id, boolean confirm) {
        PendingPanelDelete pending = pendingDeletes.get(player.getUniqueId());
        if (pending == null || pending.id != id) {
            player.sendMessage(language.message("holopanel.delete-confirmation-missing"));
            return;
        }
        pendingDeletes.remove(player.getUniqueId());
        if (System.currentTimeMillis() > pending.expiresAtMillis) {
            player.sendMessage(language.message("holopanel.delete-confirmation-expired"));
            return;
        }
        if (!confirm) {
            player.sendMessage(language.message("holopanel.delete-cancelled"));
            return;
        }
        try {
            Panel current = database.getPanel(id);
            if (current == null) {
                player.sendMessage(language.message("holopanel.delete-confirmation-missing"));
                return;
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not verify HoloPanel deletion " + id + ".", exception);
            player.sendMessage(language.message("holopanel.database-error"));
            return;
        }
        if (deletePanelById(player, id)) {
            if (pending.returnToGui) {
                player.closeInventory();
                openOverview(player, pending.page);
            }
        }
    }

    private boolean deletePanelById(Player player, long id) {
        try {
            List<PanelLocation> positions = database.getPanelLocations(id);
            database.deletePanel(id);
            for (PanelLocation position : positions) engine.remove(position.id());
            selections.entrySet().removeIf(entry -> entry.getValue().id == id);
            removeTools(player, id);
            player.sendMessage(language.message("holopanel.deleted"));
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete HoloPanel " + id + ".", exception);
            player.sendMessage(language.message("holopanel.database-error"));
            return false;
        }
    }

    private void deletePositionAt(Player player, long panelId, Location clicked) {
        try {
            PanelLocation position = nearestPositionAt(clicked);
            if (position == null) {
                player.sendMessage(language.message("holopanel.position-not-found"));
                return;
            }
            if (position.panelId() != panelId) {
                player.sendMessage(language.message("holopanel.delete-tool-wrong-panel"));
                return;
            }
            // Constrain the deletion by both IDs so this tool can only remove a
            // position belonging to the panel name embedded in the tool.
            database.deletePanelLocation(panelId, position.id());
            engine.remove(position.id());
            player.sendMessage(language.message("holopanel.position-deleted"));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete HoloPanel position for panel " + panelId + ".", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    private PanelLocation nearestPositionAt(Location clicked) throws SQLException {
        PanelLocation nearest = null;
        double closest = 4.0;
        double x = clicked.getX() + 0.5;
        double y = clicked.getY() + 0.5;
        double z = clicked.getZ() + 0.5;
        for (Panel panel : database.getPanels()) {
            for (PanelLocation location : panel.locations()) {
                if (!clicked.getWorld().getName().equals(location.world())) continue;
                if (location.server() != null && !location.server().isBlank()
                        && !database.getServerName().equalsIgnoreCase(location.server())) continue;
                double dx = x - location.x();
                double dy = y - location.y();
                double dz = z - location.z();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < closest) {
                    closest = distance;
                    nearest = location;
                }
            }
        }
        return nearest;
    }

    private void giveTool(Player player, long id, byte type) {
        ItemStack tool = new ItemStack(type == 1 ? Material.STICK : Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        meta.displayName(language.message(type == 1 ? "holopanel.set-tool" : "holopanel.delete-tool"));
        meta.lore(language.messages(type == 1 ? "holopanel.set-tool-lore" : "holopanel.delete-tool-lore"));
        meta.getPersistentDataContainer().set(toolTypeKey, PersistentDataType.BYTE, type);
        meta.getPersistentDataContainer().set(toolPanelIdKey, PersistentDataType.LONG, id);
        tool.setItemMeta(meta);
        if (player.getInventory().addItem(tool).isEmpty()) toolUsers.add(player.getUniqueId());
        player.sendMessage(language.message(type == 1 ? "holopanel.set-tool-given" : "holopanel.delete-tool-given"));
    }

    private void spawnPanel(Panel panel, long positionId, Location location, double widthBlocks, double heightBlocks) {
        engine.render(positionId, panelText(panel), location, widthBlocks, heightBlocks);
    }

    private void renderStoredPanel(Panel panel) {
        for (PanelLocation position : panel.locations()) {
            World world = Bukkit.getWorld(position.world());
            if (world == null) continue;
            spawnPanel(panel, position.id(), new Location(world, position.x(), position.y(), position.z(),
                    position.yaw() == null ? 0f : position.yaw(), position.pitch() == null ? 0f : position.pitch()),
                    position.widthBlocks(), position.heightBlocks());
        }
    }

    private String panelText(Panel panel) {
        return HoloPanelText.fromPanel(panel);
    }

    private Placement calculatePlacement(Location first, Location second, Location player) {
        int lowX = Math.min(first.getBlockX(), second.getBlockX()), highX = Math.max(first.getBlockX(), second.getBlockX());
        int lowY = Math.min(first.getBlockY(), second.getBlockY()), highY = Math.max(first.getBlockY(), second.getBlockY());
        int lowZ = Math.min(first.getBlockZ(), second.getBlockZ()), highZ = Math.max(first.getBlockZ(), second.getBlockZ());
        if (lowX != highX && lowZ != highZ) return null;
        double x = (lowX + highX + 1) / 2.0, z = (lowZ + highZ + 1) / 2.0;
        float yaw;
        if (lowZ == highZ) {
            boolean north = player.getZ() < lowZ + 0.5;
            z = lowZ + 0.5 + (north ? -1 : 1) * (0.5 + WALL_GAP);
            // Match TrackTimer's fixed-billboard orientation so the text faces
            // the side from which the placement tool user selected the wall.
            yaw = north ? 180f : 0f;
        } else {
            boolean west = player.getX() < lowX + 0.5;
            x = lowX + 0.5 + (west ? -1 : 1) * (0.5 + WALL_GAP);
            yaw = west ? 90f : -90f;
        }
        double widthBlocks = lowZ == highZ ? highX - lowX + 1.0 : highZ - lowZ + 1.0;
        double heightBlocks = highY - lowY + 1.0;
        return new Placement(new Location(first.getWorld(), x, highY + 0.75, z, yaw, 0f), widthBlocks, heightBlocks);
    }

    private void fillButtonBar(Inventory inventory) {
        ItemStack filler = configuredItem("filler", "holopanel.empty-name", Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = inventory.getSize() - 9; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler.clone());
    }

    private void setButton(Inventory inventory, String configPath, String key) {
        int size = configPath.startsWith("button.")
                ? guiSize("overview.rows", OVERVIEW_SIZE, 2)
                : guiSize("detail.rows", DETAIL_SIZE, 4);
        int slot = configuredSlot(configPath, defaultSlot(configPath, size), size);
        if (slot >= 0) inventory.setItem(slot, configuredItem(configPath, "holopanel.button." + key, Material.BARRIER));
    }

    private void setSharedButton(Inventory inventory, String key) {
        int size = inventory.getSize();
        String path = "button." + key;
        int slot = configuredSlot(path, defaultSlot(path, size), size);
        if (slot >= 0) inventory.setItem(slot, configuredItem(path, "holopanel.button." + key, Material.BARRIER));
    }

    private int defaultSlot(String path, int inventorySize) {
        return switch (path) {
            case "button.back" -> inventorySize - 5;
            case "button.close" -> inventorySize - 5;
            case "button.previous" -> inventorySize - 9;
            case "button.next-page" -> inventorySize - 1;
            default -> switch (path) {
                case "detail.button.item" -> 10;
                case "detail.button.edit" -> 12;
                case "detail.button.delete" -> 14;
                case "detail.button.tools" -> 16;
                default -> -1;
            };
        };
    }

    private void setActionItem(Inventory inventory, String configPath, String messageKey) {
        int slot = configuredSlot(configPath, -1, guiSize("detail.rows", DETAIL_SIZE, 4));
        if (slot >= 0) inventory.setItem(slot, configuredItem(configPath, messageKey, Material.PLAYER_HEAD));
    }

    private int configuredSlot(String path, int fallback, int inventorySize) {
        int slot = config.getInt(path + ".slot", fallback);
        return slot >= 0 && slot < inventorySize ? slot : fallback;
    }

    private int guiSize(String rowsPath, int fallbackSize, int minimumRows) {
        int rows = config.getInt(rowsPath, fallbackSize / 9);
        if (rows < minimumRows || rows > 6) rows = fallbackSize / 9;
        return rows * 9;
    }

    private ItemStack configuredItem(String path, String messageKey, Material fallback) {
        String type = config.getString(path + ".type", "material").toLowerCase(java.util.Locale.ROOT);
        String headDatabaseId = config.getString(path + ".head-database-id", "");
        ItemStack item = headDatabaseId.isBlank() ? null : headDatabaseItem(headDatabaseId);
        if (type.equals("itemsadder")) {
            String itemId = config.getString(path + ".item-id", "");
            if (plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder") && !itemId.isBlank()) {
                try {
                    Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                    Object result = customStack.getMethod("getInstance", String.class).invoke(null, itemId);
                    if (result != null) item = (ItemStack) customStack.getMethod("getItemStack").invoke(result);
                } catch (ReflectiveOperationException | ClassCastException exception) {
                    plugin.getLogger().warning("Could not load ItemsAdder GUI item '" + itemId + "': " + exception.getMessage());
                }
            }
        }
        if (item == null) {
            String fallbackName = config.getString(path + ".fallback-material", fallback.name());
            String materialName = config.getString(path + ".material", fallbackName);
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) material = Material.matchMaterial(fallbackName);
            item = new ItemStack(material == null || !material.isItem() ? fallback : material);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(language.message(messageKey));
            String loreKey = messageKey + "-lore";
            if (messageKey.startsWith("holopanel.action-")) {
                meta.lore(language.messages(loreKey));
            }
            if (meta instanceof SkullMeta skullMeta) {
                String owner = config.getString(path + ".owner", "");
                if (!owner.isBlank()) skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack headDatabaseItem(String id) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("HeadDatabase")) return null;
        try {
            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object api = apiClass.getDeclaredConstructor().newInstance();
            Object result = apiClass.getMethod("getItemHead", String.class).invoke(api, id);
            return result instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not load HeadDatabase head '" + id + "': " + exception.getMessage());
            return null;
        }
    }

    private ItemStack named(Material material, String key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(language.message(key));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack decodeItem(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Stored HoloPanel item is invalid.");
            return null;
        }
    }

    private boolean isPlaceholder(ItemStack item) {
        return item.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE
                && item.hasItemMeta() && item.getItemMeta().hasDisplayName();
    }

    private boolean isTool(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(toolTypeKey, PersistentDataType.BYTE)
                && item.getItemMeta().getPersistentDataContainer().has(toolPanelIdKey, PersistentDataType.LONG);
    }

    private void removeTools(Player player, long id) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isTool(item) && item.getItemMeta().getPersistentDataContainer().get(toolPanelIdKey, PersistentDataType.LONG) == id) {
                player.getInventory().setItem(slot, null);
            }
        }
        if (!hasTools(player)) toolUsers.remove(player.getUniqueId());
    }

    private boolean hasTools(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isTool(item)) return true;
        return false;
    }

    private void removeAllTools(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isTool(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        }
    }

    private void loadPanel(long id, java.util.function.Consumer<Panel> consumer, Player player) {
        try {
            Panel panel = database.getPanel(id);
            if (panel != null) consumer.accept(panel);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not load HoloPanel " + id + ".", exception);
            player.sendMessage(language.message("holopanel.database-error"));
        }
    }

    private abstract static class EditorHolder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class OverviewHolder extends EditorHolder {
        final int page;
        final Map<Integer, Long> panelsBySlot = new HashMap<>();
        OverviewHolder(int page) { this.page = page; }
    }
    private static final class DetailHolder extends EditorHolder {
        final long id; final int page;
        DetailHolder(long id, int page) { this.id = id; this.page = page; }
    }
    private static final class ItemHolder extends EditorHolder {
        final long id; final int page;
        int itemSlot;
        ItemHolder(long id, int page) { this.id = id; this.page = page; }
    }
    private record PendingSelection(long id, Location first) { }
    private record PendingPanelDelete(long id, int page, boolean returnToGui, long expiresAtMillis) { }
    private record Placement(Location location, double widthBlocks, double heightBlocks) { }
}
