package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class WarRoom extends Command {
    public WarRoom() {
        super(CommandCategory.MILCOM, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return "" + super.help() + " [target] [att1] [att2] [att3] ...\n" +
                "OR\n" +
                super.help() + " <sheet> <message>";
    }

    @Override
    public String desc() {
        return """
                Create a war room
                Add `-p` to ping users that are added
                Add `-a` to skip adding users
                Add `-f` to force create channels (if checks fail)
                Add `-m` to send standard counter messages
                Add `-h:1` to change the header row (0 index)
                Add `filter:<filter>` to filter nations.""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();

        if ((flags.contains('p') || flags.contains('m')) && !Roles.MILCOM.has(guild.getMember(author))) {
            return "You need to have milcom role to use `-p` or `-m`.";
        }
        GuildDB db = Locutus.imp().getGuildDB(guild);
        WarCategory warCat = db.getWarChannel(true);
        if (warCat == null) {
            return "War categories are not enabled. See " + GuildKey.ENABLE_WAR_ROOMS.getCommandObj(db, true) + "";
        }
        String filterArg = DiscordUtil.parseArg(args, "filter");

        boolean ping = flags.contains('p');
        boolean addMember = !flags.contains('a');
        boolean addMessage = flags.contains('m');
        String headerStr = DiscordUtil.parseArg(args, "-h");
        int headerRow = headerStr == null ? 0 : Integer.parseInt(headerStr);

        String arg = args.get(0);
        if (arg.equalsIgnoreCase("close") || arg.equalsIgnoreCase("delete")) {
            MessageChannel textChannel = channel instanceof DiscordChannelIO ? ((DiscordChannelIO) channel).getChannel() : null;
            WarCategory.WarRoom room = warCat.getWarRoom((GuildMessageChannel) textChannel);
            if (room != null) {
                room.delete("Closed by " + DiscordUtil.getFullUsername(author));
                return "Goodbye.";
            } else {
                return "You are not in a war room!";
            }
        }
        if (args.size() < 2) return usage(args.size(), 2, channel);

        if (arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:")) {
            SpreadSheet sheet = SpreadSheet.create(arg);
            StringBuilder response = new StringBuilder();
            Map<DBNation, Set<DBNation>> targets = BlitzGenerator.getTargets(sheet, headerRow, f -> 3, 0.75, 1.75, true, true, false, f -> true, (dbNationDBNationEntry, s) -> response.append(s).append("\n"));
            if (response.length() != 0) {
                channel.send(response.toString());
                if (!flags.contains('f')) {
                    return "Add `-f` to force create the channels anyway.";
                }
            }

            channel.sendMessage("Generating channels...");

            if (filterArg != null) {
                Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, filterArg, false, false);
                for (Map.Entry<DBNation, Set<DBNation>> entry : targets.entrySet()) {
                    entry.getValue().removeIf(f -> !nations.contains(f));
                }
            }
            targets.entrySet().removeIf(f -> f.getValue().isEmpty());

            Set<GuildMessageChannel> channels = new LinkedHashSet<>();
            for (Map.Entry<DBNation, Set<DBNation>> entry : targets.entrySet()) {
                DBNation target = entry.getKey();
                Set<DBNation> attackers = entry.getValue();

                WarCategory.WarRoom warChan = createChannel(warCat, author, guild, s -> response.append(s).append("\n"), ping, addMember, addMessage, target, attackers);

                try {
                    if (args.get(1).length() > 1 && !args.get(1).equalsIgnoreCase("null")) {
                        RateLimitUtil.queue(warChan.getChannel().sendMessage(args.get(1)));
                    }

                    channels.add(warChan.getChannel());
                } catch (Throwable e) {
                    e.printStackTrace();
                    response.append(e.getMessage());
                }
            }

            return "Created " + channels.size() + " for " + targets.size() + " targets";
        }
        DBNation target = DiscordUtil.parseNation(arg);
        if (target == null) return "Invalid target: `" + args.get(0) + "`";
        Set<DBNation> attackers = new LinkedHashSet<>();
        for (int i = 1; i < args.size(); i++) {
            DBNation attacker = DiscordUtil.parseNation(args.get(i));
            if (attacker == null) {
                return "Invalid attacker: `" + args.get(i) + "`. Maybe try using the nation id or the link.";
            }
            attackers.add(attacker);
        }

        StringBuilder response = new StringBuilder();
        WarCategory.WarRoom warChan = createChannel(warCat, author, guild, s -> response.append(s).append("\n"), ping, addMember, addMessage, target, attackers);

        response.append(warChan.getChannel().getAsMention());

        me.setMeta(NationMeta.INTERVIEW_WAR_ROOM, (byte) 1);

        if (!flags.contains('m') && db.getOrNull(GuildKey.API_KEY) != null)
            response.append("\n- add `-m` to send standard counter instructions");
        if (!flags.contains('p') && db.getOrNull(GuildKey.API_KEY) != null)
            response.append("\n- add `-p` to ping users in the war channel");

        return response.toString();
    }

    public static WarCategory.WarRoom createChannel(WarCategory warCat, User author, Guild guild, Consumer<String> errorOutput, boolean ping, boolean addMember, boolean addMessage, DBNation target, Collection<DBNation> attackers) {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        WarCategory.WarRoom room = warCat.get(target, true, true, true, true);
        TextChannel channel = room.getChannel(true, true);

        String declareUrl = target.getDeclareUrl();
        String channelUrl = "https://discord.com/channels/" + guild.getIdLong() + "/" + channel.getIdLong();
        String info = "> A counter is when an alliance declares a war on a nation for attacking one of its members/applicants. We usually only order counters for unprovoked attacks on members.\n" +
                "About Counters: https://docs.google.com/document/d/1eJfgNRk6L72G6N3MT01xjfn0CzQtYibwnTg9ARFknRg";

        if (addMessage) {
            RateLimitUtil.queue(channel.sendMessage(info));
        }

        for (DBNation attacker : attackers) {
            User user = attacker.getUser();
            if (user == null) {
                errorOutput.accept("No user for: " + attacker.getNation() + " | " + attacker.getAllianceName() + ". Have they used " + CM.register.cmd.toSlashMention() + " ?");
                continue;
            }

            guild = channel.getGuild();
            Member member = guild.getMemberById(user.getIdLong());
            if (member == null) {
                errorOutput.accept("No member for: " + attacker.getNation() + " | " + attacker.getAllianceName() + ". Are they on this discord?");
                continue;
            }

            if (addMember) {
                List<PermissionOverride> overrideds = channel.getMemberPermissionOverrides();
                boolean contains = false;
                for (PermissionOverride overrided : overrideds) {
                    if (member.equals(overrided.getMember())) {
                        contains = true;
                        break;
                    }
                }

                if (!contains) {
                    RateLimitUtil.complete(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));
                    if (ping) {
                        String msg = author.getName() + " added " + user.getAsMention();

                        if (addMessage) {
                            String warType = target.getAvg_infra() > 2000 && target.getAvg_infra() > attacker.getAvg_infra() ? "attrition" : "raid";
                            msg += ". Please declare a war of type `" + warType + "` with reason `counter`.";

                            Role econRole = Roles.ECON.toRole(guild);
                            String econRoleName = econRole != null ? "`@" + econRole.getName() + "`" : "ECON";

                            MessageChannel rssChannel = db.getResourceChannel(attacker.getAlliance_id());
                            MessageChannel grantChannel = db.getOrNull(GuildKey.GRANT_REQUEST_CHANNEL);

                            if (rssChannel != null) {
                                if (Boolean.TRUE.equals(db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW))) {
                                    msg += " Withdraw funds from: " + rssChannel.getAsMention() + "  **BEFORE** you declare.";
                                } else {
                                    msg += " Ping " + econRoleName + " in " + rssChannel.getAsMention() + " to withdraw funds **BEFORE** you declare.";
                                }
                            }
                            if (grantChannel != null)
                                msg += " Request funds from: " + grantChannel.getAsMention() + " **BEFORE** you declare.";

                            if (target.getGroundStrength(true, true) > attacker.getGroundStrength(true, false)) {
                                msg += "\nThe enemy has more ground. You must ensure you have funds to switch to e.g. mmr=5550 and buy tanks after declaring.";
                            }

                            String title = "Counter Attack/" + channel.getIdLong();
                            String body = info +
                                    "\n\n" + msg +
                                    "\n- target: " + declareUrl +
                                    "\n\nCheck the war room for further details: " + channelUrl;
                            String mailBody = MarkupUtil.transformURLIntoLinks(MarkupUtil.markdownToHTML(body));

                            try {
                                attacker.sendMail(ApiKeyPool.create(Locutus.imp().getRootAuth().getApiKey()), title, mailBody, false);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        RateLimitUtil.queue(channel.sendMessage(msg + "\n- <" + declareUrl + (">")));
                    }
                }
            }
        }

        return room;
    }
}
