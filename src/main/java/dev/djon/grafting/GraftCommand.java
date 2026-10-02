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

/**
 * /graft [list | reload | clear | projectiles]
 */
public final class GraftCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("list", "reload", "clear", "projectiles");

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
            default -> help(sender, label);
        }
        return true;
    }

    private void list(CommandSender sender) {
        List<Fanart> entries = plugin.grafts().library().entries();
        if (entries.isEmpty()) {
            sender.sendMessage(Messages.noArt(plugin.grafts().library()));
            return;
        }
        sender.sendMessage(Component.text("Fanart library (" + entries.size() + "):", NamedTextColor.GOLD));
        for (Fanart art : entries) {
            sender.sendMessage(Component.text(" - " + art.name() + ": ", NamedTextColor.GRAY)
                    .append(Messages.credit(art.credit())));
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Grafting", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" Shift + right-click a block or mob to graft fanart onto it.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" Shift + right-click the air to toggle projectile grafting.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " list | reload | clear | projectiles", NamedTextColor.GRAY));
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
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
