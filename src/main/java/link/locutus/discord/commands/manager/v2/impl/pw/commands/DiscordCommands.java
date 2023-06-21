package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.test.IAChannel;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class DiscordCommands {
    @Command(desc = "Modify the permissions for a list of nations in a channel.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public static String channelPermissions(@Me Member author, @Me Guild guild, TextChannel channel, Set<DBNation> nations, Permission permission,
                                            @Arg("Negate the permission") @Switch("n") boolean negate,
                                            @Arg("Remove the permission from all other users")
                                            @Switch("r") boolean removeOthers,
                                            @Arg("Log the changes to user permissions that are made")
                                            @Switch("l") boolean listChanges,
                                            @Switch("p") boolean pingAddedUsers) throws ExecutionException, InterruptedException {
        if (!author.hasPermission(channel, Permission.MANAGE_PERMISSIONS))
            throw new IllegalArgumentException("You do not have " + Permission.MANAGE_PERMISSIONS + " in " + channel.getAsMention());

        Set<Member> members = new HashSet<>();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) {
                    members.add(member);
                }
            }
        }

        List<String> changes = new ArrayList<>();

        Set<Member> toRemove = new HashSet<>();

        for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
            Member member = override.getMember();
            if (member == null || member.getUser().isBot()) continue;

            boolean allowed = (override.getAllowedRaw() & permission.getRawValue()) > 0;
            boolean denied = (override.getDeniedRaw() & permission.getRawValue()) > 0;
            boolean contains = members.contains(member);
            boolean isSet = negate ? denied : allowed;
            if (contains && isSet) {
                members.remove(member);
            } else if (!contains && isSet && removeOthers) {
                toRemove.add(member);
            }
        }
        Function<Member, String> nameFuc = Member::getEffectiveName;
        if (pingAddedUsers) {
            listChanges = true;
            nameFuc = IMentionable::getAsMention;
        }

        List<Future<?>> tasks = new ArrayList<>();
        for (Member member : members) {
            PermissionOverrideAction override = channel.upsertPermissionOverride(member);
            PermissionOverrideAction action;
            if (negate) {
                action = override.deny(permission);
            } else {
                action = override.grant(permission);
            }
            tasks.add(RateLimitUtil.queue(action));

            changes.add("Set " + permission + "=" + !negate + " for " + nameFuc.apply(member));
        }

        for (Member member : toRemove) {
            tasks.add(RateLimitUtil.queue(channel.upsertPermissionOverride(member).clear(permission)));
            changes.add("Clear " + permission + " for " + nameFuc.apply(member));
        }

        for (Future<?> task : tasks) {
            task.get();
        }

        StringBuilder response = new StringBuilder("Done.");
        if (listChanges && !changes.isEmpty()) {
            response.append("\n- ").append(StringMan.join(changes, "\n- "));
        }
        return response.toString();
    }

    @Command(desc = "Have the bot say the provided message, with placeholders replaced.")
    public String say(NationPlaceholders placeholders, ValueStore store, @Me GuildDB db, @Me Guild guild, @Me IMessageIO channel, @Me User author, @Me DBNation me, @TextArea String msg) {
        msg = DiscordUtil.trimContent(msg);
        msg = msg.replace("@", "@\u200B");
        msg = msg.replace("&", "&\u200B");
        msg = msg + "\n\n- " + author.getAsMention();

        msg = placeholders.format(store, msg);
        return msg;
    }

    @Command(desc = "Import all emojis from another guild", aliases = {"importEmoji", "importEmojis"})
    @RolePermission(Roles.ADMIN)
    public String importEmojis(@Me IMessageIO channel, Guild guild) throws ExecutionException, InterruptedException {
        if (!Settings.INSTANCE.DISCORD.CACHE.EMOTE) {
            throw new IllegalStateException("Please enable DISCORD.CACHE.EMOTE in " + Settings.INSTANCE.getDefaultFile());
        }
        if (!Settings.INSTANCE.DISCORD.INTENTS.EMOJI) {
            throw new IllegalStateException("Please enable DISCORD.INTENTS.EMOJI in " + Settings.INSTANCE.getDefaultFile());
        }
        List<RichCustomEmoji> emotes = guild.getEmojis();

        List<Future<?>> tasks = new ArrayList<>();
        for (RichCustomEmoji emote : emotes) {
            if (emote.isManaged() || !emote.isAvailable()) {
                continue;
            }

            String url = emote.getImageUrl();
            byte[] bytes = FileUtil.readBytesFromUrl(PagePriority.DISCORD_EMOJI_URL.ordinal(), url);

            channel.send("Creating emote: " + emote.getName() + " | " + url);

            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                tasks.add(RateLimitUtil.queue(guild.createEmoji(emote.getName(), icon)));
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        return "Done!";
    }

    @Command(desc = """
            Generate a card which runs a command when users react to it.
            Put commands inside "quotes".
            Prefix a command with a #channel e.g. `"#channel {prefix}embedcommand"` to have the command output go there

            Prefix the command with:`~{prefix}command` to remove the user's reaction upon use and keep the card
            `_{prefix}command` to remove ALL reactions upon use and keep the card
            `.{prefix}command` to keep the card upon use

            Example:
            `{prefix}embed 'Some Title' 'My First Embed' '~{prefix}fun say Hello {nation}' '{prefix}fun say "Goodbye {nation}"'`""",
            aliases = {"card", "embed"})
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String card(@Me IMessageIO channel, String title, String body, @TextArea List<String> commands) {
        try {
            String emoji = "\ufe0f\u20e3";

            if (commands.size() > 10) {
                return "Too many commands (max: 10, provided: " + commands.size() + ")\n" +
                        "Note: Commands must be inside \"double quotes\", and each subsequent command separated by a space.";
            }
            body = body.replace("\\n", "\n");

            System.out.println("Commands: " + commands);
            IMessageBuilder msg = channel.create().embed(title, body);
            for (int i = 0; i < commands.size(); i++) {
                String cmd = commands.get(i);
                String codePoint = i + emoji;

                msg = msg.commandButton(cmd, codePoint);
            }
            msg.send();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    @Command(desc = "Create a channel with name in a specified category and ping the specified roles upon creation.")
    public String channel(NationPlaceholders placeholders, ValueStore store, @Me GuildDB db, @Me JSONObject command, @Me User author, @Me Guild guild, @Me IMessageIO output, @Me DBNation nation,
                          String channelName, Category category, @Default String copypasta,
                          @Switch("i") boolean addInternalAffairsRole,
                          @Switch("m") boolean addMilcom,
                          @Switch("f") boolean addForeignAffairs,
                          @Switch("e") boolean addEcon,
                          @Switch("p") boolean pingRoles,
                          @Switch("a") boolean pingAuthor

    ) throws ExecutionException, InterruptedException {
        channelName = placeholders.format(store, channelName);

        Member member = guild.getMember(author);

        List<IPermissionHolder> holders = new ArrayList<>();
        holders.add(member);
        assert member != null;
        holders.addAll(member.getRoles());
        holders.add(guild.getRolesByName("@everyone", false).get(0));

        IMessageBuilder msg = output.getMessage();
        boolean hasOverride = msg != null && msg.getAuthor().getIdLong() == Settings.INSTANCE.APPLICATION_ID;
        for (IPermissionHolder holder : holders) {
            PermissionOverride overrides = category.getPermissionOverride(holder);
            if (overrides == null) continue;
            if (overrides.getAllowed().contains(Permission.MANAGE_CHANNEL)) {
                hasOverride = true;
                break;
            }
        }

        if (!hasOverride) {
            return "No permission to create channel in: " + category.getName();
        }

        Set<Roles> roles = new HashSet<>();
        if (addInternalAffairsRole) roles.add(Roles.INTERNAL_AFFAIRS);
        if (addMilcom) roles.add(Roles.MILCOM);
        if (addForeignAffairs) roles.add(Roles.FOREIGN_AFFAIRS);
        if (addEcon) roles.add(Roles.ECON);
        if (roles.isEmpty()) roles.add(Roles.INTERNAL_AFFAIRS);

        GuildMessageChannel createdChannel = null;
        List<TextChannel> channels = category.getTextChannels();
        for (TextChannel channel : channels) {
            if (channel.getName().equalsIgnoreCase(channelName)) {
                createdChannel = updateChannel(channel, member, roles);
                break;
            }
        }
        if (createdChannel == null) {
            createdChannel = updateChannel(RateLimitUtil.complete(category.createTextChannel(channelName)), member, roles);
            DiscordChannelIO io = new DiscordChannelIO(createdChannel);
            IMessageBuilder toSend = null;
            if (copypasta != null && !copypasta.isEmpty()) {
                String copyPasta = db.getCopyPasta(copypasta, true);
                if (copyPasta != null) {
                    if (toSend == null) toSend = io.create();
                    toSend.append(copyPasta);
                }
            }
            if (pingRoles) {
                for (Roles dept : roles) {
                    Role role = dept.toRole(guild);
                    if (role != null) {
                        if (toSend == null) toSend = io.create();
                        toSend.append("\n" + role.getAsMention());
                    }
                }
            }
            if (pingAuthor) {
                if (toSend == null) toSend = io.create();
                toSend.append("\n" + author.getAsMention());
            }
            if (toSend != null) toSend.send();
        }

        return "Channel: " + createdChannel.getAsMention();
    }

    private TextChannel updateChannel(TextChannel channel, IPermissionHolder holder, Set<Roles> depts) {
        RateLimitUtil.complete(channel.upsertPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL));
        RateLimitUtil.complete(channel.upsertPermissionOverride(holder).grant(Permission.VIEW_CHANNEL));

        for (Roles dept : depts) {
            Role role = dept.toRole(channel.getGuild());
            if (role != null) {
                RateLimitUtil.complete(channel.upsertPermissionOverride(role).grant(Permission.VIEW_CHANNEL));
            }
        }
        return channel;
    }

    @Command(desc = "Get info about a bot embed")
    @RolePermission(value = Roles.ADMIN)
    public String embedInfo(Message message) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embed found.";

        MessageEmbed embed = embeds.get(0);
        String title = embed.getTitle();
        String desc = embed.getDescription();
        Map<String, String> reactions = DiscordUtil.getReactions(embed);
        Map<String, String> commands = new HashMap<>();

        if (reactions == null || reactions.isEmpty()) {
            return "No embed commands found.";
        }

        List<Button> buttons = message.getButtons();
        for (Button button : buttons) {
            String id = button.getId();
            if (id == null) continue;
            System.out.println("ID " + id);
            if (id.isBlank()) {
                commands.put(button.getLabel(), "");
            } else if (MathMan.isInteger(id)) {
                String cmd = reactions.get(id);
                if (cmd != null) {
                    commands.put(button.getLabel(), cmd);
                } else {
                    commands.put(button.getLabel(), id);
                }
            } else {
                commands.put(button.getLabel(), id);
            }
        }
        if (buttons.isEmpty()) {
            commands.putAll(reactions);
        }

        String cmd = CM.embed.commands.cmd.create(title, desc, StringMan.join(commands.values(), "\" \"")).toSlashCommand();
        return "```" + cmd + "```";
    }

    @Command(desc = "Update a bot embed")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String updateEmbed(@Me Guild guild, @Me User user, @Me IMessageIO io, @Switch("r") @RegisteredRole Roles requiredRole, @Switch("c") Color color, @Switch("t") String title, @Switch("d") String desc) {
        IMessageBuilder message = io.getMessage();

        if (message == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID)
            return "This command can only be run when bound to a Locutus embed.";
        if (requiredRole != null) {
            if (!requiredRole.has(user, guild)) {
                return null;
            }
        }

        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        MessageEmbed embed = embeds.get(0);

        EmbedBuilder builder = new EmbedBuilder(embed);

        if (color != null) {
            builder.setColor(color);
        }

        if (title != null) {
            builder.setTitle(parse(title.replace(("{title}"), Objects.requireNonNull(embed.getTitle())), embed, message));
        }

        if (desc != null) {
            builder.setDescription(parse(desc.replace(("{description}"), Objects.requireNonNull(embed.getDescription())), embed, message));
        }

        message.clearEmbeds();
        message.embed(builder.build());
        message.send();

        return null;
    }

    private String parse(String arg, MessageEmbed embed, IMessageBuilder message) {
        long timestamp = message.getTimeCreated();
        long diff = System.currentTimeMillis() - timestamp;
        arg = arg.replace("{timediff}", TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
        return arg;
    }

    @Command(desc = "Return the discord invite link for the bot")
    public String invite() {
        return "<https://docs.google.com/document/d/1Qq6Qe7KtCy-Dlqktz8bhNfrUpcbf7oM8F6gRVNR28Dw/edit?usp=sharing>";
    }

    @Command(desc = "Unregister a nation to a discord user")
    public String unregister(@Me IMessageIO io, @Me JSONObject command, @Me User user, @Default("%user%") DBNation nation, @Switch("f") boolean force) {
        User nationUser = nation.getUser();
//        if (nationUser == null) return "That nation is not registered.";
        if (force && !Roles.ADMIN.hasOnRoot(user)) return "You do not have permission to force un-register.";
        if (!user.equals(nationUser) && !force) {
            String title = "Unregister another user.";
            String body = nation.getNationUrlMarkup(true) + " | " + nationUser.getAsMention() + " | " + nationUser.getName();
            io.create().confirmation(title, body, command).send();
            return null;
        }
        Locutus.imp().getDiscordDB().unregister(nation.getNation_id(), null);
        return "Unregistered user from " + nation.getNationUrl();
    }

    @Command(desc = "Register your discord user with your Politics And War nation.")
    public String register(@Me GuildDB db, @Me User user, /* @Default("%user%")  */ DBNation nation) throws IOException {
        boolean notRegistered = DiscordUtil.getUserByNationId(nation.getNation_id()) == null;
        String fullDiscriminator = user.getName() + "#" + user.getDiscriminator();

        String errorMsg = "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your discord username `" + fullDiscriminator + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command " + CM.register.cmd.create(nation.getNation_id() + "").toSlashCommand() + " again";

        long id = user.getIdLong();
        boolean checkId = false;

        PNWUser existingUser = Locutus.imp().getDiscordDB().getUser(null, user.getName(), fullDiscriminator);

        /*
        Using register
         - If the discord user/discriminator is registered to another nation, require using the discord id
         - If the nation is registered to another user, require using the discord id
         (have message that they can change the discord setting afterwards to their username)
         */

        String discordIdErrorMsg = "That nation is already registered to another user!" +
                "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/nation/edit/>\n" +
                "2. Scroll down to where it says Discord Username:\n" +
                "3. Put your **DISCORD ID** `" + user.getIdLong() + "` in the field\n" +
                "4. Click save\n" +
                "5. Run the command " + CM.register.cmd.create(nation.getNation_id() + "").toSlashCommand() + " again";

        if (existingUser != null && existingUser.getNationId() != nation.getNation_id()) {
            if (existingUser.getDiscordId() != id) {
                errorMsg = discordIdErrorMsg;
                checkId = true;
            }
        }
        Long existingUserId = nation.getUserId();
        if (existingUserId != null && existingUserId != id) {
            errorMsg = discordIdErrorMsg;
            checkId = true;
        }
        try {
            String pnwDiscordName = nation.fetchUsername();
            if (pnwDiscordName == null || pnwDiscordName.isEmpty()) {
                return errorMsg;
            }
            String userName = user.getName() + "#" + user.getDiscriminator();
            if (checkId) {
                userName = "" + user.getIdLong();
            }
            if (!userName.equalsIgnoreCase(pnwDiscordName) && !pnwDiscordName.contains("" + user.getIdLong())) {
                return "Your user doesn't match: `" + pnwDiscordName + "` != `" + userName + "`\n\n" + errorMsg;
            }

            if (existingUser != null) {
                Locutus.imp().getDiscordDB().unregister(existingUser.getNationId(), existingUser.getDiscordId());
            }
            if (existingUserId != null) {
                Locutus.imp().getDiscordDB().unregister(nation.getNation_id(), existingUserId);
            }

            PNWUser pnwUser = new PNWUser(nation.getNation_id(), id, userName);
            Locutus.imp().getDiscordDB().addUser(pnwUser);
            return nation.register(user, db, notRegistered);
        } catch (InsufficientPermissionException e) {
            return e.getMessage();
        } catch (Throwable e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    @Command(desc = "Lists the shared servers where a user has a role.")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String hasRole(User user, Roles role) {
        StringBuilder response = new StringBuilder();
        for (Guild other : user.getMutualGuilds()) {
            Role discRole = role.toRole(other);
            if (Objects.requireNonNull(other.getMember(user)).getRoles().contains(discRole)) {
                response.append(user.getName()).append(" has ").append(role.name()).append(" | @").append(discRole.getName()).append(" on ").append(other).append("\n");
            }
        }
        return response.toString();
    }

    @Command(desc = "Move a discord channel up 1 position")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelUp(@Me TextChannel channel) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() - 1));
        return null;
    }

    @Command(desc = "Delete a discord channel")
    @RolePermission(value = Roles.ADMIN)
    public String deleteChannel(@Me Guild guild, @Me User user, @Me Member member, MessageChannel channel) {
        GuildMessageChannel text = (GuildMessageChannel) channel;
        String[] split = text.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(user, guild)) && text.canTalk(member)) {
            RateLimitUtil.queue(text.delete());
            return null;
        } else {
            return "You do not have permission to close that channel.";
        }

    }

    @Command(desc = "Move a discord channel down 1 position")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelDown(@Me TextChannel channel) {
        RateLimitUtil.queue(channel.getManager().setPosition(channel.getPositionRaw() + 1));
        return null;
    }

    @Command(desc = "Send a message to the interview channels of the nations specified")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String interviewMessage(@Me GuildDB db, Set<DBNation> nations, String message, @Switch("p") boolean pingMentee) {
        Map<DBNation, IAChannel> map = db.getIACategory().getChannelMap();
        int num = 0;
        for (DBNation nation : nations) {
            IAChannel iaChan = map.get(nation);
            if (iaChan == null) continue;
            GuildMessageChannel channel = iaChan.getChannel();
            if (channel != null) {
                try {
                    String localMessage = message;
                    User user = nation.getUser();
                    if (pingMentee && user != null) {
                        localMessage += "\n" + user.getAsMention();
                    }
                    RateLimitUtil.queue(channel.sendMessage(localMessage));
                    num++;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        return "Done. Sent " + num + " messaged!";
    }

    @Command(desc = "Set the category for a discord channel")
    @RolePermission(value = Roles.INTERNAL_AFFAIRS)
    public String channelCategory(@Me Guild guild, @Me Member member, @Me TextChannel channel, Category category) {
        if (channel.getParentCategory() != null && channel.getParentCategory().getIdLong() == category.getIdLong()) {
            return "Channel is already in category: " + category;
        }
        String[] split = channel.getName().split("-");
        if (((split.length >= 2 && MathMan.isInteger(split[split.length - 1])) || Roles.ADMIN.has(member)) && channel.canTalk(member)) {
            RateLimitUtil.queue(channel.getManager().setParent(category));
            return null;
        } else {
            return "You do not have permission to move that channel.";
        }
    }
}
