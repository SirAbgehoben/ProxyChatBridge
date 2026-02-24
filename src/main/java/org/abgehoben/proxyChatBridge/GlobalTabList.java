package org.abgehoben.proxyChatBridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.WeightNode;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.abgehoben.proxyChatBridge.ProxyChatBridge.getLuckPermsGroupName;

@SuppressWarnings("ClassCanBeRecord")
public class GlobalTabList {
    private final ProxyServer server;
    private final Logger logger;
    private final Object plugin;


    public GlobalTabList(ProxyChatBridge proxyChatBridge, ProxyServer server, Logger logger) {
        this.plugin = proxyChatBridge;
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void connect(ServerConnectedEvent event) {
        server.getScheduler().buildTask(plugin, this::updateTabList).delay(1, MILLISECONDS).schedule();
    }

    @Subscribe
    public void disconnect(DisconnectEvent event) {
        server.getScheduler().buildTask(plugin, this::updateTabList).delay(1, MILLISECONDS).schedule();
    }

    private void updateTabList() {
        server.getScheduler().buildTask(plugin, () -> {
            List<Player> allPlayers = new ArrayList<>(server.getAllPlayers());
            allPlayers.sort(Comparator.comparingInt(this::getLuckPermsWeight)); //Maybe also do this asynchronously

            for (Player player : allPlayers) {
                TabList tabList = player.getTabList();

                tabList.clearAll();

                for (Player target : allPlayers) {
                    getLuckPermsGroupName(target).thenAccept(groupName -> {
                        String serverName = target.getCurrentServer()
                                .map(s -> s.getServerInfo().getName())
                                .orElse("Unknown");

                        Component tabListEntry = Component.text()
                                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                                .append(Component.text(groupName))
                                .append(Component.text("][", NamedTextColor.DARK_GRAY))
                                .append(Component.text(serverName, NamedTextColor.WHITE))
                                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                                .append(Component.text(target.getUsername(), NamedTextColor.WHITE))
                                .build();

                        tabList.addEntry(TabListEntry.builder()
                                .tabList(tabList)
                                .profile(target.getGameProfile())
                                .displayName(tabListEntry)
                                .latency((int) target.getPing())
                                .build());
                    });
                }
            }
        }).schedule();
    }

    private int getLuckPermsWeight(Player player) {
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
            logger.error("Error getting weight from luckperms", e);
            return 0;
        }
    }
}
