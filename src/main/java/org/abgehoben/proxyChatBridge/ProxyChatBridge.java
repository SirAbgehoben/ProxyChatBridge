package org.abgehoben.proxyChatBridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.WeightNode;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Plugin(id = "velocitychatbridge", name = "Velocity Chat Bridge", version = "1.0-SNAPSHOT", description = "A plugin to bridge chat across Velocity servers")
public class ProxyChatBridge {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public ProxyChatBridge(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Call updateTabList() once when the proxy initializes
        updateTabList();
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String originalMessage = event.getMessage();
        String serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unknown");

        getLuckPermsGroupName(player).thenAccept(luckPermsGroupName -> {
            TextComponent formattedMessage = Component.text()
                    .append(Component.text("[", NamedTextColor.DARK_GRAY))
                    .append(Component.text(luckPermsGroupName))
                    .append(Component.text("][", NamedTextColor.DARK_GRAY))
                    .append(Component.text(serverName, NamedTextColor.WHITE))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY))
                    .append(Component.text("<", NamedTextColor.WHITE))
                    .append(Component.text(player.getUsername(), NamedTextColor.WHITE))
                    .append(Component.text("> ", NamedTextColor.WHITE))
                    .append(Component.text(originalMessage, NamedTextColor.WHITE))
                    .build();

            server.getAllPlayers().forEach(p -> p.sendMessage(formattedMessage));
        });

        event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        String fromServer = event.getPreviousServer().map(s -> s.getServerInfo().getName()).orElse("Unknown");
        String toServer = event.getServer().getServerInfo().getName();

        TextComponent switchMessage = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("⇄", NamedTextColor.AQUA))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(" switch from ", NamedTextColor.GREEN))
                .append(Component.text(fromServer, NamedTextColor.WHITE))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(toServer, NamedTextColor.WHITE))
                .build();

        server.getAllPlayers().forEach(p -> p.sendMessage(switchMessage));
        updateTabList();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        String serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unknown");

        TextComponent joinMessage = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("+", NamedTextColor.GREEN))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(" joined ", NamedTextColor.GREEN))
                .append(Component.text(serverName, NamedTextColor.WHITE))
                .build();

        server.getAllPlayers().forEach(p -> p.sendMessage(joinMessage));
        // Update the tablist for all players when a new player joins
        updateTabList();
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        String serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unknown");

        TextComponent leaveMessage = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("-", NamedTextColor.RED))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.text(" left ", NamedTextColor.RED))
                .append(Component.text(serverName, NamedTextColor.WHITE))
                .build();

        server.getAllPlayers().forEach(p -> p.sendMessage(leaveMessage));
        updateTabList();
    }

    private void updateTabList() {
        server.getAllPlayers().forEach(player -> {
            TabList tabList = player.getTabList();

            // Remove existing entries (compatible with older Velocity versions)
            for (TabListEntry entry : new ArrayList<>(tabList.getEntries())) {
                tabList.removeEntry(entry.getProfile().getId());
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            server.getAllPlayers().stream()
                    .sorted(Comparator.comparingInt(this::getLuckPermsWeight).reversed())
                    .forEachOrdered(target -> {
                        String serverName = target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("Unknown");
                        CompletableFuture<Void> future = getLuckPermsGroupName(target).thenAccept(groupName -> {
                            Component tabListEntry = Component.text()
                                    .append(Component.text("[", NamedTextColor.DARK_GRAY))
                                    .append(Component.text(groupName))
                                    .append(Component.text("][", NamedTextColor.DARK_GRAY))
                                    .append(Component.text(serverName, NamedTextColor.WHITE))
                                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                                    .append(Component.text(target.getUsername(), NamedTextColor.WHITE))
                                    .build();

                            tabList.addEntry(TabListEntry.builder()
                                    .tabList(tabList) // Ensure tabList is set
                                    .profile(target.getGameProfile())
                                    .displayName(tabListEntry)
                                    .latency((int) target.getPing()) // Cast long to int
                                    .build());
                        });
                        futures.add(future);
                    });

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        });
    }

    private CompletableFuture<String> getLuckPermsGroupName(Player player) {
        UserManager userManager = LuckPermsProvider.get().getUserManager();
        return userManager.loadUser(player.getUniqueId()).thenApply(user -> {
            if (user != null) {
                Optional<InheritanceNode> inheritanceNode = user.getNodes().stream()
                        .filter(NodeType.INHERITANCE::matches)
                        .map(NodeType.INHERITANCE::cast)
                        .findFirst();

                if (inheritanceNode.isPresent()) {
                    String groupName = inheritanceNode.get().getGroupName();
                    Group group = LuckPermsProvider.get().getGroupManager().getGroup(groupName);
                    if (group != null) {
                        return group.getDisplayName();
                    }
                }
            }
            return "Default";
        });
    }


    private int getLuckPermsWeight(Player player) {
        // Access LuckPerms API using static provider
        LuckPerms luckPerms = LuckPermsProvider.get();
        UserManager userManager = luckPerms.getUserManager();
        CompletableFuture<Integer> weightFuture = userManager.loadUser(player.getUniqueId()).thenApply(user -> {
            if (user != null) {
                return user.getNodes().stream()
                        .filter(NodeType.WEIGHT::matches)
                        .map(NodeType.WEIGHT::cast)
                        .mapToInt(WeightNode::getWeight)
                        .findFirst()
                        .orElse(0);
            }
            return 0;
        });

        try {
            return weightFuture.get();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}