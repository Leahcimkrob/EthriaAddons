package de.ethria.Commands;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import de.ethria.Language.LanguageManager;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Base64;

/** Shows an item's complete data in a scrollable Paper dialog. */
public final class ViewNbtCommand implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final LanguageManager language;
    private YamlConfiguration gui;
    public ViewNbtCommand(JavaPlugin plugin, LanguageManager language) {
        this.plugin = plugin; this.language = language; reloadGuiConfig();
    }

    public void reloadGuiConfig() {
        gui = de.ethria.Config.YamlDefaults.loadAndUpdate(plugin, "Addons/viewnbtGUI.yml",
                new java.io.File(plugin.getDataFolder(), "Addons/viewnbtGUI.yml"));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(language.message("viewnbt.player-only")); return true; }
        if (!player.hasPermission("ethriaaddons.command.viewnbt")) { player.sendMessage(language.message("command.no-permission")); return true; }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) { player.sendMessage(language.message("viewnbt.no-item")); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("compare")) {
            if (args.length == 1) { openCompare(player); return true; }
            ItemStack other = args.length > 1 ? findItem(player, args[1]) : null;
            if (other == null) { player.sendMessage(language.message("viewnbt.compare-usage")); return true; }
            String differences = compare(itemData(item), itemData(other));
            show(player, withBase64(differences, item, other),
                    language.message("viewnbt.compare-title"));
            return true;
        }
        // ItemStack#serialize() includes Paper/Bukkit item metadata (name, lore, enchants,
        // model data, plugin data and other serializable fields) and does not mutate the stack.
        String text = toJson(item.serialize());
        show(player, text, language.message("viewnbt.title"));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ethriaaddons.command.viewnbt")) return List.of();
        if (args.length == 1) return "compare".startsWith(args[0].toLowerCase(java.util.Locale.ROOT)) ? List.of("compare") : List.of();
        if (args.length == 2 && args[0].equalsIgnoreCase("compare")) {
            String prefix = args[1];
            return java.util.stream.IntStream.range(0, 41).mapToObj(String::valueOf)
                    .filter(slot -> slot.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private void openCompare(Player player) {
        CompareHolder holder = new CompareHolder();
        int rows = Math.max(3, Math.min(6, gui.getInt("compare.rows", 3)));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, language.message("viewnbt.compare-gui-title"));
        holder.inventory = inventory;
        fillBackground(inventory);
        int first = slot("compare.first-slot", 11, inventory.getSize());
        int compare = slot("compare.button.slot", 13, inventory.getSize());
        int second = slot("compare.second-slot", 15, inventory.getSize());
        holder.firstSlot = first; holder.buttonSlot = compare; holder.secondSlot = second;
        inventory.setItem(first, null); inventory.setItem(second, null);
        inventory.setItem(compare, configuredItem("compare.button", Material.ANVIL, "viewnbt.compare-button"));
        player.openInventory(inventory);
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = configuredItem("filler", Material.GRAY_STAINED_GLASS_PANE, "viewnbt.empty-name");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler.clone());
    }

    private int slot(String path, int fallback, int size) {
        int value = gui.getInt(path, fallback);
        return value >= 0 && value < size ? value : fallback;
    }

    private ItemStack configuredItem(String path, Material fallback, String nameKey) {
        String headId = gui.getString(path + ".head-database-id", "");
        ItemStack item = headId.isBlank() ? null : headDatabaseItem(headId);
        String materialName = gui.getString(path + ".material", gui.getString(path + ".fallback-material", fallback.name()));
        Material material = Material.matchMaterial(materialName);
        if (item == null) item = new ItemStack(material == null ? fallback : material);
        String modelId = gui.getString(path + ".item-id", "");
        if (!modelId.isBlank()) try {
            ItemMeta meta = item.getItemMeta(); meta.setCustomModelData(Integer.parseInt(modelId)); item.setItemMeta(meta);
        } catch (NumberFormatException exception) { plugin.getLogger().warning("Invalid custom model item-id at " + path); }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(language.message(nameKey)); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack headDatabaseItem(String id) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("HeadDatabase")) return null;
        try {
            Class<?> api = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object instance = api.getDeclaredConstructor().newInstance();
            Object result = api.getMethod("getItemHead", String.class).invoke(instance, id);
            return result instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Could not load HeadDatabase head '" + id + "': " + ex.getMessage());
            return null;
        }
    }

    @EventHandler public void onCompareClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CompareHolder)) return;
        CompareHolder holder = (CompareHolder) event.getView().getTopInventory().getHolder();
        int rawSlot = event.getRawSlot();
        boolean topClick = event.getClickedInventory() == event.getView().getTopInventory();
        if (!topClick) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }
        if (rawSlot != holder.firstSlot && rawSlot != holder.secondSlot) event.setCancelled(true);
        if (rawSlot == holder.firstSlot || rawSlot == holder.secondSlot) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getView().getTopInventory();
        if (rawSlot != holder.buttonSlot) return;
        ItemStack a = inv.getItem(holder.firstSlot), b = inv.getItem(holder.secondSlot);
        if (isEmpty(a) || isEmpty(b)) { player.sendMessage(language.message("viewnbt.compare-missing")); return; }
        String result = compare(itemData(a), itemData(b));
        player.closeInventory();
        show(player, withBase64(result, a, b), language.message("viewnbt.compare-title"));
    }

    @EventHandler public void onCompareDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CompareHolder holder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize()
                && slot != holder.firstSlot && slot != holder.secondSlot)) event.setCancelled(true);
    }

    @EventHandler public void onCompareClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CompareHolder)) return;
        CompareHolder holder = (CompareHolder) event.getView().getTopInventory().getHolder();
        for (int slot : new int[]{holder.firstSlot, holder.secondSlot}) {
            ItemStack item = event.getView().getTopInventory().getItem(slot);
            if (isEmpty(item)) continue;
            Map<Integer, ItemStack> overflow = event.getPlayer().getInventory().addItem(item);
            overflow.values().forEach(left -> event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), left));
        }
    }

    private boolean isEmpty(ItemStack item) { return item == null || item.getType().isAir(); }

    private static final class CompareHolder implements InventoryHolder {
        private Inventory inventory;
        private int firstSlot, secondSlot, buttonSlot;
        @Override public Inventory getInventory() { return inventory; }
    }

    private ItemStack findItem(Player player, String selector) {
        try {
            int slot = Integer.parseInt(selector);
            if (slot >= 0 && slot < player.getInventory().getSize()) {
                ItemStack item = player.getInventory().getItem(slot);
                return item == null || item.getType().isAir() ? null : item.clone();
            }
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private String compare(Map<String, Object> first, Map<String, Object> second) {
        StringBuilder out = new StringBuilder();
        appendDiff(out, "", first, second);
        return out.toString();
    }

    private String withBase64(String differences, ItemStack original, ItemStack modified) {
        StringBuilder result = new StringBuilder(differences);
        if (!differences.isEmpty()) result.append('\n');
        result.append("Original:\n")
                .append(Base64.getEncoder().encodeToString(original.serializeAsBytes()))
                .append("\n\nModifiziert:\n")
                .append(Base64.getEncoder().encodeToString(modified.serializeAsBytes()));
        return result.toString();
    }

    private void appendDiff(StringBuilder out, String path, Map<?, ?> first, Map<?, ?> second) {
        TreeSet<String> keys = new TreeSet<>();
        first.keySet().forEach(key -> keys.add(String.valueOf(key)));
        second.keySet().forEach(key -> keys.add(String.valueOf(key)));
        for (String key : keys) {
            Object a = first.get(key), b = second.get(key);
            String next = path.isEmpty() ? key : path + "." + key;
            if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
                appendDiff(out, next, ma, mb);
            } else if (!deepEquals(a, b)) {
                out.append(next).append(":\n  ").append(plain("viewnbt.first-item")).append(": ")
                        .append(toJson(a)).append("\n  ").append(plain("viewnbt.second-item"))
                        .append(": ").append(toJson(b)).append("\n\n");
            }
        }
    }

    private boolean deepEquals(Object a, Object b) {
        if (a instanceof byte[] x && b instanceof byte[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof int[] x && b instanceof int[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof long[] x && b instanceof long[] y) return java.util.Arrays.equals(x, y);
        return java.util.Objects.equals(a, b);
    }

    private String plain(String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(language.message(key));
    }

    private Map<String, Object> itemData(ItemStack item) {
        Map<String, Object> data = new java.util.LinkedHashMap<>(item.serialize());
        Map<String, Object> publicValues = new java.util.TreeMap<>();
        var container = item.getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) publicValues.put(key.toString(), persistentValue(container, key));
        if (!publicValues.isEmpty()) data.put("PublicBukkitValues", publicValues);
        return data;
    }

    private Object persistentValue(io.papermc.paper.persistence.PersistentDataContainerView container, NamespacedKey key) {
        if (container.has(key, PersistentDataType.STRING)) return container.get(key, PersistentDataType.STRING);
        if (container.has(key, PersistentDataType.INTEGER)) return container.get(key, PersistentDataType.INTEGER);
        if (container.has(key, PersistentDataType.LONG)) return container.get(key, PersistentDataType.LONG);
        if (container.has(key, PersistentDataType.DOUBLE)) return container.get(key, PersistentDataType.DOUBLE);
        if (container.has(key, PersistentDataType.FLOAT)) return container.get(key, PersistentDataType.FLOAT);
        if (container.has(key, PersistentDataType.BYTE)) return container.get(key, PersistentDataType.BYTE);
        if (container.has(key, PersistentDataType.SHORT)) return container.get(key, PersistentDataType.SHORT);
        if (container.has(key, PersistentDataType.BYTE_ARRAY)) return container.get(key, PersistentDataType.BYTE_ARRAY);
        if (container.has(key, PersistentDataType.INTEGER_ARRAY)) return container.get(key, PersistentDataType.INTEGER_ARRAY);
        if (container.has(key, PersistentDataType.LONG_ARRAY)) return container.get(key, PersistentDataType.LONG_ARRAY);
        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            var nested = container.get(key, PersistentDataType.TAG_CONTAINER);
            Map<String, Object> values = new java.util.TreeMap<>();
            for (NamespacedKey child : nested.getKeys()) values.put(child.toString(), persistentValue(nested, child));
            return values;
        }
        return "<unknown persistent data type>";
    }

    private String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            StringBuilder json = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (json.length() > 1) json.append(',');
                json.append('\n').append("  ").append(quote(String.valueOf(entry.getKey()))).append(": ")
                        .append(indent(toJson(entry.getValue()), 2));
            }
            if (json.length() > 1) json.append('\n');
            return json.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder json = new StringBuilder("[");
            for (Object element : values) {
                if (json.length() > 1) json.append(',');
                json.append('\n').append("  ").append(indent(toJson(element), 2));
            }
            if (json.length() > 1) json.append('\n');
            return json.append(']').toString();
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof byte[] bytes) return quote(java.util.Base64.getEncoder().encodeToString(bytes));
        return quote(String.valueOf(value));
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private String indent(String text, int spaces) {
        return text.replace("\n", "\n" + " ".repeat(spaces));
    }

    private void show(Player player, String text, Component title) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .inputs(List.of(io.papermc.paper.registry.data.dialog.input.DialogInput.text(
                                "item_data", 400, language.message("viewnbt.data-label"), true,
                                text, Math.max(4096, text.length()),
                                io.papermc.paper.registry.data.dialog.input.TextDialogInput.MultilineOptions.create(null, 400))))
                        .canCloseWithEscape(true).build())
                .type(DialogType.confirmation(
                        ActionButton.create(language.message("viewnbt.copy"), null, 150,
                                DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player clicker) {
                                        clicker.sendMessage(language.message("viewnbt.copied"));
                                    }
                                }, ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(language.message("viewnbt.close"), null, 150,
                                DialogAction.customClick((response, audience) -> {},
                                        ClickCallback.Options.builder().uses(1).build())))));
        player.showDialog(dialog);
    }
}
