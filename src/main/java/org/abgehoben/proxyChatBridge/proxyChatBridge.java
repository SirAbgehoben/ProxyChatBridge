package org.abgehoben.proxyChatBridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.abgehoben.proxyChatBridge.TextComponentParser.parseToDiscordFormat;

@Plugin(id = "velocitychatbridge", name = "Velocity Chat Bridge", version = "1.0-SNAPSHOT", description = "A plugin to bridge chat across Velocity servers")
public class proxyChatBridge extends ListenerAdapter {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Set<UUID> loggedInPlayers = new HashSet<>();
    private TextChannel defaultChannel;
    private JDA jda;

    private static final String TOKEN_FILE_PATH = "token.txt";
    private static final String DEFAULT_CHANNEL_ID_FILE_PATH = "default_channel_id.txt";

    @Inject
    public proxyChatBridge(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public String readTokenFile() {
        try {
            return new String(Files.readAllBytes(dataDirectory.resolve(TOKEN_FILE_PATH))).trim();
        } catch (IOException e) {
            logger.error("Error reading token file", e);
            return null;
        }
    }

    public void createDefaultTokenFile() {
        File tokenFile = dataDirectory.resolve(TOKEN_FILE_PATH).toFile();
        if (!tokenFile.exists()) {
            try {
                Files.createDirectories(dataDirectory);
                boolean created = tokenFile.createNewFile();
                if (created) {
                    logger.info("Created default token file at {}", tokenFile.getAbsolutePath());
                }
            } catch (IOException e) {
                logger.error("Error creating default token file", e);
            }
        }
    }

    public String readDefaultChannelIdFile() {
        File file = dataDirectory.resolve(DEFAULT_CHANNEL_ID_FILE_PATH).toFile();
        if (!file.exists()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))).trim();
        } catch (IOException e) {
            logger.error("Error reading default channel ID file", e);
            return null;
        }
    }

    public void writeDefaultChannelIdFile(String channelId) {
        try {
            createDefaultChannelIdFile();
            Files.write(dataDirectory.resolve(DEFAULT_CHANNEL_ID_FILE_PATH), channelId.getBytes());
        } catch (IOException e) {
            logger.error("Error writing default channel ID file", e);
        }
    }

    public void createDefaultChannelIdFile() {
        File channelIdFile = dataDirectory.resolve(DEFAULT_CHANNEL_ID_FILE_PATH).toFile();
        if (!channelIdFile.exists()) {
            try {
                Files.createDirectories(dataDirectory);
                boolean created = channelIdFile.createNewFile();
                if (created) {
                    logger.info("Created default channel ID file at {}", channelIdFile.getAbsolutePath());
                }
            } catch (IOException e) {
                logger.error("Error creating default channel ID file", e);
            }
        }
    }

    @Subscribe
    public void onMessageReceived(MessageReceivedEvent event) { //Received a message from discord
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) {
            return;
        }

        if (event.getChannel().equals(defaultChannel)) {
            String messageContent = event.getMessage().getContentDisplay();
            String user = event.getAuthor().getEffectiveName();
            String userRole = "Default";
            Color rawRoleColor = null;

            List<Role> roles = Objects.requireNonNull(event.getMember()).getRoles();
            if (!roles.isEmpty()) {
                userRole = roles.get(0).getName();
            }
            if (event.getMember().getRoles().get(0).getColor() != null) {
                rawRoleColor = roles.get(0).getColor();
            }

            String roleColor;
            if (rawRoleColor == null) {
                roleColor = "§f"; // default white color
            } else {
                roleColor = TextComponentParser.getMinecraftColorCode(rawRoleColor);
            }

            logger.info("Message received in default channel: {} from user: {} with role {} that has color {}color", messageContent, user, userRole, roleColor);

            TextComponent formattedMessage = Component.text()
                    .append(Component.text("[", NamedTextColor.DARK_GRAY))
                    .append(Component.text(roleColor + (userRole.equalsIgnoreCase("owner") ? "§l" : "") + userRole, NamedTextColor.WHITE))
                    .append(Component.text("][", NamedTextColor.DARK_GRAY))
                    .append(Component.text(event.getGuild().getName(), NamedTextColor.WHITE))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY))
                    .append(Component.text("<", NamedTextColor.WHITE))
                    .append(Component.text(user, NamedTextColor.WHITE))
                    .append(Component.text("> ", NamedTextColor.WHITE))
                    .append(Component.text(messageContent, NamedTextColor.WHITE))
                    .build();

            server.getAllPlayers().forEach(p -> p.sendMessage(formattedMessage));
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        createDefaultTokenFile();
        createDefaultChannelIdFile();

        String token = readTokenFile();
        if (token == null || token.isEmpty()) {
            logger.error("Token file is missing or empty. Please provide a valid bot token.");
            return;
        }

        JDABuilder builder = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGES)
                .setActivity(Activity.playing("AbgehobenNetwork"))
                .addEventListeners(this, new JdaReadyEventListener());

        try {
            jda = builder.build();
        } catch (Exception e) {
            logger.error("Error creating JDA instance", e);
        }
    }

    public class JdaReadyEventListener extends ListenerAdapter {
        @Override
        public void onReady(@NotNull ReadyEvent event) {
            logger.info("JDA is ready!");

            String defaultChannelId = readDefaultChannelIdFile();
            if (defaultChannelId != null && !defaultChannelId.isEmpty()) {
                defaultChannel = jda.getTextChannelById(defaultChannelId);
                if (defaultChannel == null) {
                    logger.warn("Could not find a text channel with ID: {}. Please set a new default channel using /setdefaultchannel", defaultChannelId);
                }
            } else {
                logger.warn("Default channel ID not found. Please set a default channel using /setdefaultchannel");
            }

            jda.updateCommands().addCommands(
                    Commands.slash("setdefaultchannel", "Sets the default text channel for the bot")
                            .addOption(OptionType.CHANNEL, "channel", "The channel to set as default", true)
            ).queue();
            logger.info("Registered slash commands with Discord.");
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if ("setdefaultchannel".equals(event.getName())) {
            if (event.getOption("channel") == null) {
                event.reply("Error: No channel provided.").setEphemeral(true).queue();
                return;
            }

            TextChannel channel = Objects.requireNonNull(event.getOption("channel")).getAsChannel().asTextChannel();

            defaultChannel = channel;
            writeDefaultChannelIdFile(channel.getId());

            event.reply("Default channel set to " + channel.getName()).setEphemeral(true).queue();
            logger.info("Default channel updated to: {} ({})", channel.getName(), channel.getId());
        }
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

            String messageInformationContent = formattedMessage.toString(); //this content is better than netflix
            String discordFormatedMessage = parseToDiscordFormat(messageInformationContent);

            if (defaultChannel != null && !discordFormatedMessage.isEmpty()) {
                defaultChannel.sendMessage(discordFormatedMessage).queue(
                        message -> logger.info("Sent message to Discord: {}", discordFormatedMessage),
                        error -> logger.error("Failed to send message to Discord", error)
                );
            }

            server.getAllPlayers().forEach(p -> p.sendMessage(formattedMessage));
        });

        event.setResult(PlayerChatEvent.ChatResult.denied());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        boolean isPostLogin = loggedInPlayers.contains(player.getUniqueId());

        String playerName = player.getUsername();
        String fromServer = event.getPreviousServer().map(s -> s.getServerInfo().getName()).orElse("Unknown");
        String toServer = event.getServer().getServerInfo().getName();

        if(!isPostLogin) {
            loggedInPlayers.add(player.getUniqueId());

            TextComponent joinMessage = Component.text()
                    .append(Component.text("[", NamedTextColor.DARK_GRAY))
                    .append(Component.text("+", NamedTextColor.GREEN))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerName, NamedTextColor.WHITE))
                    .append(Component.text(" joined ", NamedTextColor.GREEN))
                    .append(Component.text(toServer, NamedTextColor.WHITE))
                    .build();

            String messageInformationContent = joinMessage.toString();
            String discordFormatedMessage = parseToDiscordFormat(messageInformationContent);

            if (defaultChannel != null && !discordFormatedMessage.isEmpty()) {
                defaultChannel.sendMessage(discordFormatedMessage).queue();
            }
            server.getAllPlayers().forEach(p -> p.sendMessage(joinMessage));
        } else {
            TextComponent switchMessage = Component.text()
                    .append(Component.text("[", NamedTextColor.DARK_GRAY))
                    .append(Component.text("⇄", NamedTextColor.AQUA))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerName, NamedTextColor.WHITE))
                    .append(Component.text(" switched from ", NamedTextColor.GREEN))
                    .append(Component.text(fromServer, NamedTextColor.WHITE))
                    .append(Component.text(" to ", NamedTextColor.GREEN))
                    .append(Component.text(toServer, NamedTextColor.WHITE))
                    .build();

            String messageInformationContent = switchMessage.toString();
            String discordFormatedMessage = parseToDiscordFormat(messageInformationContent);

            if (defaultChannel != null && !discordFormatedMessage.isEmpty()) {
                defaultChannel.sendMessage(discordFormatedMessage).queue();
            }

            server.getAllPlayers().forEach(p -> p.sendMessage(switchMessage));
        }
        updateTabList();
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        loggedInPlayers.remove(player.getUniqueId());

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

        String messageInformationContent = leaveMessage.toString();
        String discordFormatedMessage = parseToDiscordFormat(messageInformationContent);

        if (defaultChannel != null && !discordFormatedMessage.isEmpty()) {
            defaultChannel.sendMessage(discordFormatedMessage).queue();
        }

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
                                    .tabList(tabList) // Ensure the tabList is set
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
            logger.error("Error getting weight from luckperms", e);
            return 0;
        }
    }
}