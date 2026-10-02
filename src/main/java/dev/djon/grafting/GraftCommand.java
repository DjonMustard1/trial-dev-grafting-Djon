package dev.djon.grafting;

import dev.djon.grafting.art.Fanart;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * {@code /graft [list | reload | clear | projectiles | size <1-10>]}
 */
public final class GraftCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("list", "reload", "clear", "projectiles", "size");

    private final GraftingPlugin plugin;

    public GraftCommand(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "reload" -> {
                if (requireAdmin(sender)) {
                    int count = plugin.reloadFanart();
                    sender.sendMessage(Component.text("Loaded " + count + " fanart.", NamedTextColor.GREEN));
                }
            }
            case "clear" -> {
                if (requireAdmin(sender)) {
                    int removed = plugin.grafts().clearAll(Bukkit.getWorlds());
                    sender.sendMessage(Component.text("Removed " + removed + " grafts.", NamedTextColor.GREEN));
                }
            }
            case "projectiles" -> {
                if (sender instanceof Player player && player.hasPermission("grafting.use")) {
                    plugin.listener().toggleProjectileMode(player);
                } else {
                    sender.sendMessage(Component.text("Only players with grafting.use can do that.", NamedTextColor.RED));
                }
            }
            case "size" -> size(sender, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void size(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("grafting.use")) {
            sender.sendMessage(Component.text("Only players with grafting.use can do that.", NamedTextColor.RED));
            return;
        }
        int max = GraftManager.MAX_WIDTH;
        int width;
        try {
            width = args.length < 2 ? -1 : Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            width = -1;
        }
        if (width < 1 || width > max) {
            sender.sendMessage(Component.text("Usage: /" + label + " size <1-" + max + ">  (now "
                    + plugin.listener().width(player) + ")", NamedTextColor.RED));
            return;
        }
        plugin.listener().setWidth(player, width);
        sender.sendMessage(Component.text("Graft size set to " + width + "x" + width + ".", NamedTextColor.LIGHT_PURPLE));
    }

    private void list(CommandSender sender) {
        List<Fanart> entries = plugin.grafts().library().entries();
        if (entries.isEmpty()) {
            sender.sendMessage(Messages.noArt(plugin.grafts().library()));
            return;
        }
        sender.sendMessage(Component.text("Fanart library (" + entries.size() + "):", NamedTextColor.GOLD));
        for (Fanart art : entries) {
            Component line = Component.text(" - " + art.name(), NamedTextColor.GRAY);
            if (art.credit().hasAttribution()) {
                line = line.append(Component.text(": ", NamedTextColor.GRAY)).append(Messages.credit(art.credit()));
            }
            sender.sendMessage(line);
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Grafting", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" Shift + right-click a block or mob to graft fanart onto it.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" Shift + right-click the air to toggle projectile grafting.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" Shift + middle-click, or /" + label + " size <1-" + GraftManager.MAX_WIDTH
                + ">, to change graft size.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " list | reload | clear | projectiles | size", NamedTextColor.GRAY));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("grafting.admin")) {
            return true;
        }
        sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("size")) {
            return IntStream.rangeClosed(1, GraftManager.MAX_WIDTH).mapToObj(String::valueOf)
                    .filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
