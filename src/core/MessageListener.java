package core;

import net.dv8tion.jda.client.entities.Group;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.events.user.UserNameUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import raids.RaidLobby;

import java.util.ArrayList;
import java.util.HashMap;

public class MessageListener extends ListenerAdapter {
    public final String WHITE_GREEN_CHECK = "\u2705";


    private final HashMap<Long, Message> messageMap = new HashMap<>();
    private NovaBot novaBot;

    public MessageListener(NovaBot novaBot) {
        this.novaBot = novaBot;
    }

    @Override
    public void onUserNameUpdate(UserNameUpdateEvent event) {
        if (!novaBot.config.loggingEnabled()) return;

        final User user = event.getUser();
        novaBot.userUpdatesLog.sendMessage(user.getAsMention() + " has changed their username from " + event.getOldName() + " to " + event.getUser().getName()).queue();

        if (novaBot.guild.getMember(user).getEffectiveName().equalsIgnoreCase("novabot") && !user.isBot()) {
            Member member = novaBot.guild.getMember(user);
            novaBot.guild.getController().kick(member).queue(
                    success -> novaBot.userUpdatesLog.sendMessage("Kicked " + member.getEffectiveName() + " because their name was `novabot`").queue()
                                                            );
        }
    }

    @Override
    public void onMessageReceived(final MessageReceivedEvent event) {

        final User           author  = event.getAuthor();
        final Message        message = event.getMessage();
        final MessageChannel channel = event.getChannel();
        final String         msg     = message.getContentDisplay();

        messageMap.put(message.getIdLong(), message);

        if (event.isFromType(ChannelType.TEXT)) {
            final TextChannel textChannel = event.getTextChannel();

            if (novaBot.config.isRaidOrganisationEnabled() && novaBot.lobbyManager.isLobbyChannel(channel.getId())) {
                novaBot.parseRaidLobbyMsg(author, msg, textChannel);
            } else if (novaBot.config.isRaidOrganisationEnabled() && novaBot.config.isRaidChannel(channel.getId())) {
                novaBot.parseRaidChatMsg(author, msg, textChannel);
            } else if (channel.getId().equals(novaBot.config.getUserUpdatesId())) {
                novaBot.parseModMsg(message, textChannel);
            } else if (channel.getId().equals(novaBot.config.getCommandChannelId())) {
                novaBot.parseMsg(msg.toLowerCase().trim(), author, textChannel);
            }
        } else if (event.isFromType(ChannelType.PRIVATE)) {
            if (message.getEmbeds().size() > 0) {
                novaBot.novabotLog.info(String.format("[PRIV]<%s>: %s%s%s\n", author.getName(), msg, message.getEmbeds().get(0).getTitle(), message.getEmbeds().get(0).getDescription()));
            } else {
                novaBot.novabotLog.info(String.format("[PRIV]<%s>: %s\n", author.getName(), msg));
            }
            novaBot.parseMsg(msg, author, channel);
        } else if (event.isFromType(ChannelType.GROUP)) {
            final Group  group     = event.getGroup();
            final String groupName = (group.getName() != null) ? group.getName() : "";
            novaBot.novabotLog.info(String.format("[GRP: %s]<%s>: %s\n", groupName, author.getName(), msg));
        }
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!novaBot.config.loggingEnabled()) return;

        final long  id      = event.getMessageIdLong();
        TextChannel channel = novaBot.guild.getTextChannelById(event.getChannel().getId());


        Message foundMessage = messageMap.get(id);

        if (foundMessage == null) {
            novaBot.userUpdatesLog.sendMessageFormat("A message was deleted from %s, but the message could not be retrieved from the log", channel).queue();
            return;
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(String.format("A message was deleted from %s", channel.getName()), null);
        embedBuilder.addField("Channel", channel.getAsMention(), true);
        embedBuilder.setDescription(String.format("%s%n %s:%n %s",
                                                  foundMessage.getCreationTime().atZoneSameInstant(novaBot.config.getTimeZone()).format(novaBot.formatter),
                                                  foundMessage.getAuthor().getAsMention(),
                                                  foundMessage.getContentDisplay()));

        novaBot.userUpdatesLog.sendMessage(embedBuilder.build()).queue();
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {

        if (!novaBot.config.isRaidOrganisationEnabled()) return;

        if (event.getUser().isBot()) return;

        if (!event.getReactionEmote().getName().equals(WHITE_GREEN_CHECK)) return;

        Message message = event.getChannel().getMessageById(event.getMessageId()).complete();

        if (!message.getAuthor().isBot()) return;

        novaBot.novabotLog.debug("white green check reaction added to a bot message that contains an embed!");

        String content;
        if (message.getEmbeds().size() > 0) {
            content = message.getEmbeds().get(0).getDescription();
        } else {
            content = message.getContentDisplay();
        }

        int    joinIndex = content.indexOf("!joinraid") + 10;
        String lobbyCode = content.substring(joinIndex, content.substring(joinIndex).indexOf("`") + joinIndex).trim();

        novaBot.novabotLog.info("Message clicked was for lobbycode " + lobbyCode);

        RaidLobby lobby = novaBot.lobbyManager.getLobby(lobbyCode);

        if (lobby == null) {
            event.getChannel().sendMessageFormat("%s, that lobby has ended and cannot be joined.", event.getMember()).queue();
            return;
        }

        if (!lobby.containsUser(event.getUser().getId())) {

            lobby.joinLobby(event.getUser().getId());

            if (event.getChannelType() == ChannelType.PRIVATE) {
                event.getChannel().sendMessageFormat("%s you have been placed in %s. There are now %s users in the lobby.", event.getUser(), lobby.getChannel(), lobby.memberCount()).queue();
            }

            novaBot.alertRaidChats(novaBot.config.getRaidChats(lobby.spawn.getGeofences()), String.format(
                    "%s joined %s raid in %s. There are now %s users in the lobby. Join the lobby by clicking the ✅ or by typing `!joinraid %s`.",
                    novaBot.guild.getMember(event.getUser()).getAsMention(),
                    (lobby.spawn.bossId == 0 ? String.format("lvl %s egg", lobby.spawn.raidLevel) : lobby.spawn.properties.get("pkmn")),
                    lobby.getChannel().getAsMention(),
                    lobby.memberCount(),
                    lobby.lobbyCode
                                                                                                         ));
        }
    }

    @Override
    public void onGuildMemberJoin(final GuildMemberJoinEvent event) {
        if (!novaBot.config.loggingEnabled()) return;

        final Member member = event.getMember();

        novaBot.guild.getInvites().queue(success -> {
            String theCode = null;

            for (Invite newInvite : success) {
                if (theCode != null) break;

                boolean found = false;
                for (Invite oldInvite : novaBot.invites) {
                    if (oldInvite.getCode().equals(newInvite.getCode())) {
                        found = true;

                        if (newInvite.getUses() > oldInvite.getUses()) {
                            theCode = newInvite.getCode();

                            RaidLobby lobby = novaBot.lobbyManager.getLobbyByChannelId(newInvite.getChannel().getId());

                            if (lobby != null) {
                                lobby.joinLobby(member.getUser().getId());
                            }
                            break;
                        }
                    }
                }

                if (!found && newInvite.getUses() == 1) {
                    theCode = newInvite.getCode();
                    break;
                }
            }
            novaBot.invites = (ArrayList<Invite>) success;

            novaBot.userUpdatesLog.sendMessage(
                    member.getAsMention() +
                    " joined with code " + theCode + ". The account was created " +
                    member.getUser().getCreationTime().atZoneSameInstant(novaBot.config.getTimeZone()).format(novaBot.formatter)).queue();
            novaBot.dbManager.logNewUser(member.getUser().getId());

            if (member.getEffectiveName().equalsIgnoreCase("novaBot") && !member.getUser().isBot()) {
                novaBot.guild.getController().kick(member).queue(
                        s -> novaBot.userUpdatesLog.sendMessage("Kicked " + member.getEffectiveName() + " because their name was `novabot`").queue()
                                                                );
            }
        });
    }

    @Override
    public void onGuildMemberRoleAdd(final GuildMemberRoleAddEvent event) {
        if (!novaBot.config.loggingEnabled()) return;

        final User    user    = event.getMember().getUser();
        StringBuilder roleStr = new StringBuilder();
        for (final Role role : event.getRoles()) {
            if (novaBot.lobbyManager.isLobbyRoleId(role.getId())) continue;

            roleStr.append(role.getName()).append(" ");
        }

        if (roleStr.length() != 0) {
            novaBot.roleLog.sendMessage(user.getAsMention() + " had " + roleStr + "role(s) added").queue();
        }
    }

    @Override
    public void onGuildMemberRoleRemove(final GuildMemberRoleRemoveEvent event) {
        if (!novaBot.config.loggingEnabled()) return;

        final User    user    = event.getMember().getUser();
        StringBuilder roleStr = new StringBuilder();
        for (final Role role : event.getRoles()) {
            if (novaBot.lobbyManager.isLobbyRoleId(role.getId())) continue;
            roleStr.append(role.getName()).append(" ");
        }

        if (roleStr.length() != 0) {
            novaBot.roleLog.sendMessage(user.getAsMention() + " had " + roleStr + "role(s) removed").queue();
        }
    }

    @Override
    public void onGuildMemberNickChange(final GuildMemberNickChangeEvent event) {
        if (!novaBot.config.loggingEnabled()) return;

        final User user = event.getMember().getUser();
        novaBot.userUpdatesLog.sendMessage(user.getAsMention() + " has changed their nickname from " + event.getPrevNick() + " to " + event.getNewNick()).queue();

        if (novaBot.guild.getMember(user).getEffectiveName().equalsIgnoreCase("novaBot") && !user.isBot()) {
            Member member = novaBot.guild.getMember(user);
            novaBot.guild.getController().kick(member).queue(
                    success -> novaBot.userUpdatesLog.sendMessage("Kicked " + member.getEffectiveName() + " because their name was `novabot`").queue()
                                                            );
        }
    }


}
