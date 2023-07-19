package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FACommands {
    @Command(desc = "Generate a named coalition by the treaty web of an alliance")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String generateSphere(@Me GuildDB db, String coalition, DBAlliance rootAlliance, @Arg("Include only the top alliances") @Default("80") int topX) {
        SphereGenerator sphereGen = new SphereGenerator(topX);
        Map.Entry<Integer, List<DBAlliance>> sphere = sphereGen.getSphere(rootAlliance);
        if (sphere == null) {
            return "No sphere found for " + rootAlliance.getName();
        }
        if (Coalition.getOrNull(coalition) != null) {
            return coalition + " is a reserved keyword";
        }
        Set<Long> existing = db.getCoalitionRaw(coalition);
        if (existing != null) {
            for (long id : new HashSet<>(existing)) {
                db.removeCoalition(id, coalition);
            }
        }
        for (DBAlliance alliance : sphere.getValue()) {
            db.addCoalition(alliance.getIdLong(), coalition);
        }
        return "Set Coalition: `" + coalition + "` to `" + StringMan.getString( sphere.getValue()) + "`";
    }

    @Command(desc = "Send a treaty to an alliance")
    @IsAlliance
    @RolePermission(value = Roles.FOREIGN_AFFAIRS, alliance = true)
    public static String sendTreaty(@Me User user, @Me GuildDB db, @Arg("The alliance to send a treaty from\ni.e. Your alliance") @RolePermission(Roles.FOREIGN_AFFAIRS) AllianceList sender, @Arg("Alliance to send treaty to") DBAlliance receiver, TreatyType type, int days, @Default String message) {
        if (message != null && !message.isEmpty() && !Roles.ADMIN.has(user, db.getGuild())) {
            return "Admin is required to send a treaty with a message";
        }
        if (message == null) message = "";
        Set<Treaty> result = sender.sendTreaty(receiver.getAlliance_id(), type, message, days);
        return "Sent:\n- " + StringMan.join(result, "\n- ");
    }

    @Command(desc = "Approve a pending treaty from an alliance")
    @IsAlliance
    @RolePermission(value = Roles.FOREIGN_AFFAIRS, alliance = true)
    public static String approveTreaty(@Arg("The alliance to approve a treaty of\ni.e. Your alliance") @RolePermission(Roles.FOREIGN_AFFAIRS) AllianceList receiver, @Arg("Alliance that sent the treaty") Set<DBAlliance> senders) {
        List<Treaty> changed = receiver.approveTreaty(senders);

        if (changed.isEmpty()) {
            return "No treaties to approve";
        }

        return "Approved:\n- " + StringMan.join(changed, "\n- ");
    }

    @Command(desc = "Cancel a treaty in-game")
    @IsAlliance
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public static String cancelTreaty(@Arg("The alliance to cancel a treaty of\ni.e. Your alliance") @RolePermission(Roles.FOREIGN_AFFAIRS) AllianceList receiver, @Arg("The other alliance the treaty is with") Set<DBAlliance> senders) {
        List<Treaty> changed = receiver.cancelTreaty(senders);

        if (changed.isEmpty()) {
            return "No treaties to cancel";
        }

        return "Cancelled:\n- " + StringMan.join(changed, "\n- ");
    }

    @Command(desc = "List the bot coalitions")
    @RolePermission(Roles.MEMBER)
    public String listCoalition(@Me User user, @Me GuildDB db, @Arg("Only list alliances or guilds containing this filter") @Default String filter, @Arg("List the alliance and guild ids instead of names") @Switch("i") boolean listIds, @Arg("Ignore deleted alliances") @Switch("d") boolean ignoreDeleted) {
        Map<String, Set<Long>> coalitions = db.getCoalitionsRaw();
        List<String> coalitionNames = new ArrayList<>(coalitions.keySet());
        Collections.sort(coalitionNames);
        if (filter != null) filter = filter.toLowerCase(Locale.ROOT);

        StringBuilder response = new StringBuilder();
        for (String coalition : coalitionNames) {
            if (coalition.equalsIgnoreCase("offshore") && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild())) {
                continue;
            }
            Set<Long> alliances = coalitions.get(coalition);
            List<String> names = new ArrayList<>();
            for (long allianceOrGuildId : alliances) {
                String name;
                if (allianceOrGuildId > Integer.MAX_VALUE) {
                    GuildDB guildDb = Locutus.imp().getGuildDB(allianceOrGuildId);
                    if (guildDb == null) {
                        if (ignoreDeleted) continue;
                        name = "guild:" + allianceOrGuildId;
                    } else {
                        name = guildDb.getGuild().toString();
                    }
                } else {
                    name = Locutus.imp().getNationDB().getAllianceName((int) allianceOrGuildId);
                    if (name == null) {
                        if (ignoreDeleted) continue;
                        name = "AA:" + allianceOrGuildId;
                    }
                }
                if (listIds) {
                    names.add(allianceOrGuildId + "");
                } else {
                    names.add(name);
                }
            }
            if (filter != null) {
                if (!coalition.toLowerCase(Locale.ROOT).contains(filter)) {
                    String finalFilter = filter;
                    names.removeIf(f -> !f.toLowerCase().contains(finalFilter));
                    if (names.isEmpty()) continue;
                }
            }

            String listBold = StringMan.join(names, ",");
            if (filter != null) {
                // replace ignore case regex
                listBold = listBold.replaceAll("(?i)" + filter, "__" + filter + "__");
            }
            response.append('\n').append("**" + coalition + "**: " + listBold);
        }

        if (response.length() == 0) return "No coalitions found";
        return response.toString().trim();
    }

    @Command(desc = "Create a new coalition with the provided alliances")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String createCoalition(@Me User user, @Me GuildDB db, Set<NationOrAllianceOrGuild> alliances, String coalitionName) {
        Coalition coalition = Coalition.getOrNull(coalitionName);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        StringBuilder response = new StringBuilder();
        for (NationOrAllianceOrGuild aaOrGuild : alliances) {
            if (aaOrGuild.isNation()) {
                DBAlliance alliance = Locutus.imp().getNationDB().getAllianceByName(aaOrGuild.getName());
                if (alliance == null) {
                    alliance = Locutus.imp().getNationDB().getAllianceByName(aaOrGuild.asNation().getLeader());
                }
                if (alliance == null) {
                    throw new IllegalArgumentException("Invalid alliance: " + aaOrGuild.getName());
                }
                aaOrGuild = alliance;
            }
            db.addCoalition(aaOrGuild.getIdLong(), coalitionName);
            response.append("Added " + aaOrGuild.getName() + " to " + coalitionName).append("\n");
        }
        return response.toString();
    }

    @Command(desc = "Add alliances to an existing coalition\n" +
            "Note: Use `{prefix}coalition create` to use a nonstandard coalition")
    @RolePermission(Roles.MEMBER)
    public String addCoalition(@Me User user, @Me GuildDB db, Set<NationOrAllianceOrGuild> alliances, @GuildCoalition String coalitionName) {
        return createCoalition(user, db, alliances, coalitionName);
    }

    @Command(desc = "Delete an entire coalition")
    @RolePermission(Roles.FOREIGN_AFFAIRS)
    public String deleteCoalition(@Me User user, @Me GuildDB db, @GuildCoalition String coalitionName) {
        Coalition coalition = Coalition.getOrNull(coalitionName);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        db.removeCoalition(coalitionName);
        return "Deleted coalition " + coalitionName;
    }

    @Command(desc = "Remove alliances to a coalition\n" +
            "Note: Use `{prefix}coalition delete` to delete an entire coalition")
    @RolePermission(Roles.MEMBER)
    public String removeCoalition(@Me User user, @Me GuildDB db, Set<NationOrAllianceOrGuild> alliances, @GuildCoalition String coalitionName) {
        Coalition coalition = Coalition.getOrNull(coalitionName);
        if ((coalition != null && !coalition.hasPermission(db.getGuild(), user)) ||
                (coalition == null && !Roles.FOREIGN_AFFAIRS.has(user, db.getGuild()))
        ) {
            return "You do not have permission to set this coalition";
        }
        StringBuilder response = new StringBuilder();
        for (NationOrAllianceOrGuild aaOrGuild : alliances) {
            if (aaOrGuild.isNation()) {
                DBAlliance alliance = Locutus.imp().getNationDB().getAllianceByName(aaOrGuild.getName());
                if (alliance == null) throw new IllegalArgumentException("Invalid alliance: " + aaOrGuild.getName());
                aaOrGuild = alliance;
            }
            db.removeCoalition(aaOrGuild.getIdLong(), coalitionName);
            response.append("Removed " + aaOrGuild.getName() + " from " + coalitionName).append("\n");
        }
        return response.toString();
    }

    // Write a long description
    @Command(desc = "List the treaties of the provided alliances\n" +
            "Note: If you have the FORIEGN_AFFAIRS role you can view the pending treaties of your own alliance from its guild")
    public String treaties(@Me IMessageIO channel, @Me User user, Set<DBAlliance> alliances) {
        StringBuilder response = new StringBuilder();
        Set<Treaty> allTreaties = new LinkedHashSet<>();

        if (alliances.size() == 1) {
            DBAlliance alliance = alliances.iterator().next();
            boolean update = false;
            GuildDB aaDb = alliance.getGuildDB();
            if (aaDb != null) {
                update = (Roles.FOREIGN_AFFAIRS.getAllowedAccounts(user, aaDb).contains(alliance.getIdLong()));
            }
            allTreaties.addAll(alliance.getTreaties(update).values());
        } else {
            for (DBAlliance alliance : alliances) {
                Map<Integer, Treaty> treaties = alliance.getTreaties();
                allTreaties.addAll(treaties.values());
            }
        }

        if (allTreaties.isEmpty()) return "No treaties";

        long turn = TimeUtil.getTurn();
        for (Treaty treaty : allTreaties) {
            String from = PnwUtil.getMarkdownUrl(treaty.getFromId(), true);
            String to = PnwUtil.getMarkdownUrl(treaty.getToId(), true);
            TreatyType type = treaty.getType();

            response.append(from + " | " + type + " -> " + to);
            if (treaty.getTurnEnds() > turn) {
                String expires = TimeUtil.secToTime(TimeUnit.MILLISECONDS, TimeUtil.getTimeFromTurn(treaty.getTurnEnds() - turn));
                response.append(" (" + expires + ")");
            }
            response.append("\n");
        }

        String title = allTreaties.size() + " treaties";
        channel.create().embed(title, response.toString()).send();
        return null;
    }

    @Command(desc = "Create an embassy channel in the embassy category")
    public String embassy(@Me GuildDB db, @Me Guild guild, @Me User user, @Me DBNation me, @Arg("The nation to create an embassy for") @Default("%user%") DBNation nation) {
        if (nation == null) nation = me;
        if (!me.equals(nation) && !Roles.FOREIGN_AFFAIRS.has(user, guild)) return "You do not have FOREIGN_AFFAIRS";
        Category category = db.getOrThrow(GuildKey.EMBASSY_CATEGORY);
        if (category == null) {
            return "Embassies are disabled. To set it up, use " + GuildKey.EMBASSY_CATEGORY.getCommandMention() + "";
        }
        if (nation.getPosition() < Rank.OFFICER.id && !Roles.FOREIGN_AFFAIRS.has(user, guild)) return "You are not an officer";
        User nationUser = nation.getUser();
        if (nationUser == null) return "Nation " + nation.getNationUrl() + " is not registered";
        Member member = guild.getMember(nationUser);
        if (member == null) return "User " + user.getName() + " is not in this guild";

        Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
        Role role = aaRoles.get(nation.getAlliance_id());
        if (role == null) {
            db.addCoalition(nation.getAlliance_id(), Coalition.MASKEDALLIANCES);
            GuildDB.AutoRoleOption autoRoleValue = db.getOrNull(GuildKey.AUTOROLE_ALLIANCES);
            if (autoRoleValue == null || autoRoleValue == GuildDB.AutoRoleOption.FALSE) {
                return "AutoRole is disabled. See " + GuildKey.AUTOROLE_ALLIANCES.getCommandMention() + "";
            }
            db.getAutoRoleTask().syncDB();
            AutoRoleInfo task = db.getAutoRoleTask().autoRole(member, nation);
            task.execute();
            aaRoles = DiscordUtil.getAARoles(guild.getRoles());
            role = aaRoles.get(nation.getAlliance_id());
            if (role == null) {
                return "No alliance role found. Please try " + CM.role.autoassign.cmd.create().toSlashCommand() + "";
            }
        }

        for (TextChannel channel : category.getTextChannels()) {
            String[] split = channel.getName().split("-");
            if (MathMan.isInteger(split[split.length - 1]) && Integer.parseInt(split[split.length - 1]) == nation.getAlliance_id()) {
                updateEmbassyPerms(channel, role, user, true);
                return "Embassy: <#" + channel.getId() + ">";
            }
        }
        if (me.getPosition() <= 2) {
            return "You must be an officer to create an embassy";
        }

        String embassyName = nation.getAllianceName() + "-" + nation.getAlliance_id();

        TextChannel channel = RateLimitUtil.complete(category.createTextChannel(embassyName).setParent(category));
        updateEmbassyPerms(channel, role, nationUser, true);

        return "Embassy: <#" + channel.getId() + ">";
    }

    public static void updateEmbassyPerms(TextChannel channel, Role role, User user, boolean mention) {
        RateLimitUtil.complete(channel.upsertPermissionOverride(channel.getGuild().getRolesByName("@everyone", false).get(0))
                .deny(Permission.VIEW_CHANNEL));
        RateLimitUtil.complete(channel.upsertPermissionOverride(role).grant(Permission.VIEW_CHANNEL));

        Role ambassador = Roles.FOREIGN_AFFAIRS.toRole(channel.getGuild());
        if (ambassador != null) {
            RateLimitUtil.complete(channel.upsertPermissionOverride(ambassador).grant(Permission.VIEW_CHANNEL));
        }

        if (mention) {
            RateLimitUtil.queue(channel.sendMessage(user.getAsMention()));
        }
    }
}