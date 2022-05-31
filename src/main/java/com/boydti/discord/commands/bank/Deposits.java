package com.boydti.discord.commands.bank;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.NationMeta;
import com.boydti.discord.db.entities.Transaction2;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.offshore.OffshoreInstance;
import com.boydti.discord.apiv1.enums.DepositType;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.boydti.discord.util.PnwUtil.*;
import static com.boydti.discord.apiv1.enums.DepositType.*;

public class Deposits extends Command {
    public Deposits() {
        super("deposits", "depo", "holdings", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return "`!deposits <nation|alliance|*>` or `!deposits <nation|alliance|*> [offshores]` e.g. `!deposits @user`";
    }

    @Override
    public String desc() {
        return "Calculate a nations deposits/loans/taxes\n" +
                "Add `-b` to not subtract base taxes\n" +
                "Add `-o` to not include any manual offset\n" +
                "Add e.g. `\"date>05/01/2019 11:21 pm\"` to filter by date\n" +
                "Add `-l` to only include the largest positive value of each rss in the total\n" +
                "Add `-t` to show taxes separately\n\n" +
                "Note: Use `!synctaxes` to update tax records\n" +
                "Add `-d` to show results in dm"
                ;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    public void largest(Map<ResourceType, Double> total, Map<ResourceType, Double> local) {
        for (Map.Entry<ResourceType, Double> entry : local.entrySet()) {
            ResourceType type = entry.getKey();
            total.put(type, Math.max(entry.getValue(), total.getOrDefault(type, 0d)));
            if (total.get(type) <= 0) {
                total.remove(type);
            }
        }
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        DBNation banker = DiscordUtil.getNation(event);
        if (banker == null) {
            return "Please use !validate";
        }

        banker.setMeta(NationMeta.INTERVIEW_DEPOSITS, (byte) 1);

        long cutOff = 0;
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if (arg.startsWith("date>")) {
                String dateStr = arg.split(">")[1];
                boolean equal = dateStr.startsWith("=");
                if (equal) dateStr = dateStr.substring(1);

                cutOff = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, dateStr);
                if (equal) cutOff -= 1;
                iterator.remove();
            }
        }

        if (args.size() != 1 && args.size() != 2) {
            return usage(event);
        }

        Map<String, Map<DepositType, double[]>> accountDeposits = new HashMap<>();

        String arg0 = args.get(0);

        Set<String> split = new LinkedHashSet<>(Arrays.asList(arg0.split(",")));

        GuildDB guildDb = Locutus.imp().getGuildDB(event);

        StringBuilder response = new StringBuilder();
        response.append("**" + arg0 + "**:\n".replaceAll("@", ""));

        List<String> footers = new ArrayList<>();

        DBNation requiredUser = null;

        Set<Long> tracked = null;
        if (args.size() == 2) {
            tracked = new LinkedHashSet<>();
            Set<Integer> alliances = DiscordUtil.parseAlliances(guild, args.get(1));
            if (alliances == null || alliances.isEmpty()) {
                return "Invalid alliance: `" + args.get(1) + "`";
            }
            for (Integer alliance : alliances) tracked.add(alliance.longValue());
            tracked = PnwUtil.expandCoalition(tracked);
        }

        for (String arg : split) {
            DBNation nation = DiscordUtil.parseNation(arg);
            if (arg.equalsIgnoreCase("*")) {
                OffshoreInstance offshore = guildDb.getOffshore();
                if (offshore == null) return "No offshore found";
                GuildDB offshoreDb = guildDb.getOffshoreDB();
                if (!Roles.ECON.has(author, guild) && !Roles.ECON.has(author, offshoreDb.getGuild()))
                    return "You do not have permission to check this guild's deposits";

                double[] deposits = offshore.getDeposits(guildDb);
                accountDeposits.put("*", Collections.singletonMap(DEPOSITS, deposits));
            } else if (nation == null && MathMan.isInteger(arg) && Long.parseLong(arg) > Integer.MAX_VALUE) {
                GuildDB otherDb = Locutus.imp().getGuildDB(Long.parseLong(arg));
                if (otherDb == null) return "Unknown guild: " + arg;
                OffshoreInstance offshore = otherDb.getOffshore();
                if (offshore == null) return "No offshore is set. In this server, use `!setcoalition <alliance|guild> offshore` and from the offshore server use `!setcoalition <alliance|guild> offshoring`";
                if (!Roles.ECON.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON.has(author, otherDb.getGuild()))
                    return "You do not have permission to check another guild's deposits";

                double[] deposits = offshore.getDeposits(otherDb);
                String name = otherDb.getGuild().getName();
                accountDeposits.put(name, Collections.singletonMap(DEPOSITS, deposits));
            } else if (nation == null && parseAllianceId(arg) != null) {
                Integer allianceId = parseAllianceId(arg);
                GuildDB otherDb = Locutus.imp().getGuildDBByAA(allianceId);
                if (otherDb == null) return "No guild found for AA:" + allianceId;

                OffshoreInstance offshore = otherDb.getOffshore();
                if (offshore == null) {
                    if (flags.contains('f')) {
                        offshore = guildDb.getHandler().getBank();
                    }
                    if (offshore == null) {
                        return "No offshore set";
                    }
                }
                if (!Roles.ECON.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON.has(author, otherDb.getGuild()))
                    return "You do not have permisssion to check another alliance's deposits";

                String name = PnwUtil.getName(allianceId, true);
                double[] deposits = PnwUtil.resourcesToArray(offshore.getDeposits(allianceId, true));
                accountDeposits.put(name, Collections.singletonMap(DEPOSITS, deposits));
            } else {
                if (nation == null && arg.contains("/nation/") && DiscordUtil.parseNationId(arg) != null) {
                    nation = new DBNation();
                    nation.setNation_id(DiscordUtil.parseNationId(arg));
                }
                if (nation == null) return "Nation not found: `" + arg + "`";
                if (split.size() == 1) requiredUser = nation;
                if (nation.getNation_id() != me.getNation_id() && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.ECON.has(author, guild)) return "You do not have permission to check other nation's deposits";

                Map<DepositType, double[]> nationDepo = nation.getDeposits(guildDb, tracked, !flags.contains('b'), !flags.contains('o'), 0L, cutOff);
                accountDeposits.put(nation.getNation(), nationDepo);
            }
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);

        boolean largest = flags.contains('l');

        double[] total = new double[ResourceType.values.length];
        double[] totalNoGrants = new double[ResourceType.values.length];
        double[] taxAndDeposits = new double[ResourceType.values.length];
        Map<DepositType, double[]> categorized = new HashMap<>();

        for (Map<DepositType, double[]> accountDeposit : accountDeposits.values()) {
            for (Map.Entry<DepositType, double[]> entry : accountDeposit.entrySet()) {
                DepositType type = entry.getKey();
                double[] existing = categorized.computeIfAbsent(type, f -> new double[ResourceType.values.length]);
                double[] current = entry.getValue();

                for (int i = 0 ; i < existing.length; i++) {
                    if (largest) {
                        existing[i] = Math.max(existing[i], current[i]);
                        total[i] = Math.max(total[i], current[i]);
                        if (type != GRANT) {
                            totalNoGrants[i] = Math.max(totalNoGrants[i], current[i]);
                            if (type != LOAN) {
                                taxAndDeposits[i] = Math.max(taxAndDeposits[i], current[i]);
                            }
                        }
                    }
                    else {
                        existing[i] += current[i];
                        total[i] += current[i];
                        if (type != GRANT) {
                            totalNoGrants[i] += current[i];
                            if (type != LOAN) {
                                taxAndDeposits[i] += current[i];
                            }
                        }
                    }
                }
            }
        }

        footers.add("value is based on current market prices");

        if (flags.contains('t')) {
            if (categorized.containsKey(DEPOSITS)) {
                response.append("DEPOSITS: ~$" + MathMan.format(convertedTotal(categorized.get(DEPOSITS))));
                response.append("\n```").append(resourcesToString(categorized.get(DEPOSITS))).append("``` ");
            }
            if (categorized.containsKey(TAX)) {
                response.append("TAX: ~$" + MathMan.format(convertedTotal(categorized.get(TAX))));
                response.append("\n```").append(resourcesToString(categorized.get(TAX))).append("``` ");
            }
            if (categorized.containsKey(LOAN)) {
                response.append("LOANS/GRANTS: ~$" + MathMan.format(convertedTotal(categorized.get(LOAN))));
                response.append("\n```").append(resourcesToString(categorized.get(LOAN))).append("``` ");
            }
            if (categorized.containsKey(GRANT)) {
                response.append("TEMPORARY: ~$" + MathMan.format(convertedTotal(categorized.get(GRANT))));
                response.append("\n```").append(resourcesToString(categorized.get(GRANT))).append("``` ");
            }
            if (categorized.size() > 1) {
                response.append("Total Equity: ~$" + MathMan.format(convertedTotal(total)));
                response.append("\n```").append(resourcesToString(total)).append("``` ");
            }
        } else {
            if (categorized.containsKey(GRANT)) {
                response.append("Total Equity (safekeep + loans + grants) worth: ~$" + MathMan.format(convertedTotal(total)));
                response.append("\n```").append(resourcesToString(total)).append("``` ");
                footers.add("Unlike loans, debt from grants will expire if you stay (see the transaction for the timeframe)");
            }
            if (categorized.containsKey(LOAN)) {
                response.append("Safekeep + loans worth: ~$" + MathMan.format(convertedTotal(totalNoGrants)));
                response.append("\n```").append(resourcesToString(totalNoGrants)).append("``` ");
            }

            response.append("Safekeep (bank + tax deposits) worth: ~$" + MathMan.format(convertedTotal(taxAndDeposits)));
            response.append("\n```").append(resourcesToString(taxAndDeposits)).append("``` ");
        }
        if (requiredUser != null && me != null && requiredUser.getNation_id() == me.getNation_id()) {
            if (Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.RESOURCE_CONVERSION))) {
                footers.add("You can sell resources to the alliance by depositing with the note #cash");
            }
            if (PnwUtil.convertedTotal(total) > 0 && Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW))) {
                Role role = Roles.ECON_WITHDRAW_SELF.toRole(guild);
                if (guild.getMember(author).getRoles().contains(role)) {
                    footers.add("To withdraw, use: !tr");
                }
            }
        }

        if (!footers.isEmpty()) {
            for (int i = 0; i < footers.size(); i++) {
                String footer = footers.get(i);
                response.append("\n`note" + (i == 0 ? "" : i)).append(": " + footer + "`");
            }
        }

        MessageChannel output = flags.contains('d') ? author.openPrivateChannel().complete() : event.getChannel();
        Message message = com.boydti.discord.util.RateLimitUtil.complete(output.sendMessage(response.toString()));

        if (requiredUser != null && requiredUser.getPosition() > 1 && guildDb.isWhitelisted() && guildDb.getOrNull(GuildDB.Key.API_KEY) != null) {
            DBNation finalNation = requiredUser;
            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<String> tips2 = new ArrayList<>();

                        {
                            Map<ResourceType, Double> stockpile = finalNation.getStockpile();
                            if (stockpile != null && !stockpile.isEmpty() && stockpile.getOrDefault(ResourceType.CREDITS, 0d) != -1) {
                                Map<ResourceType, Double> excess = finalNation.checkExcessResources(guildDb, stockpile);
                                if (!excess.isEmpty()) {
                                    tips2.add("Excess can be deposited: " + PnwUtil.resourcesToString(excess));
                                    if (Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.DEPOSIT_INTEREST))) {
                                        List<Transaction2> transactions = finalNation.getTransactions(-1);
                                        long last = 0;
                                        for (Transaction2 transaction : transactions) last = Math.max(transaction.tx_datetime, last);
                                        if (System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(5)) {
                                            tips2.add("Deposit frequently to be eligable for interest on your deposits");
                                        }
                                    }
                                }
                                Map<ResourceType, Double> needed = finalNation.getResourcesNeeded(stockpile, 3, true);
                                if (!needed.isEmpty()) {
                                    tips2.add("Missing resources for the next 3 days: " + PnwUtil.resourcesToString(needed));
                                }
                            }
                        }

                        if (me != null && me.getNation_id() == finalNation.getNation_id() && Boolean.TRUE.equals(guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE)) && guildDb.isValidAlliance() && guildDb.hasAuth()) {
                            Alliance alliance = guildDb.getAlliance();
                            if (alliance != null && me.getAlliance_id() == alliance.getAlliance_id()) {
                                try {
                                    Map<ResourceType, Double> stockpile = alliance.getStockpile();
                                    if (PnwUtil.convertedTotal(stockpile) > 5000000) {
                                        tips2.add("You MUST offshore funds after depositing");
                                    }
                                } catch (Throwable ignore) {}
                            }
                        }

                        if (!tips2.isEmpty()) {
                            for (String tip : tips2) response.append("\n`tip: " + tip + "`");

                            RateLimitUtil.queue(output.editMessageById(message.getIdLong(), response.toString()));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        return null;
    }

    public void appendAlliance(Map<Integer, String> alliances, Integer bankTo, Map<ResourceType, Double> value, StringBuilder response) {
        String allianceName = alliances.getOrDefault(bankTo, "");
        response.append('\n')
                .append(allianceName).append(" | ")
                .append("<" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + bankTo + ">")
                .append("```")
                .append(resourcesToString(value))
                .append(" | Converted total: $")
                .append(String.format("%.2f", convertedTotal(value)))
                .append("```");
    }

    public void appendByName(String name, Map<ResourceType, Double> value, StringBuilder response) {
        response.append('\n')
                .append(name).append(" | ")
                .append("```")
                .append(resourcesToString(value))
                .append(" | Converted total: $")
                .append(String.format("%.2f", convertedTotal(value)))
                .append("```");
    }

    public void appendNation(Integer nationId, Map<ResourceType, Double> value, StringBuilder response) {
        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
        String name = nation != null ? nation.getNation() : "";
        String alliance = nation != null ? nation.getAlliance() : "";
        String link = nation != null ? "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nationId : "";
        response.append('\n')
                .append(name).append(" | ")
                .append(alliance).append(" | ")
                .append("<" + link + ">")
                .append("```")
                .append(resourcesToString(value))
                .append(" | Converted total: $")
                .append(String.format("%.2f", convertedTotal(value)))
                .append("```");
    }
}
