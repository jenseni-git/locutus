package com.boydti.discord.commands.war;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.NationMeta;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.SpyCount;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import com.boydti.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpyCommand extends Command {
    public SpyCommand() {
        super("spy", "spies", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return "!spy <nation> [spies-used]";
    }

    @Override
    public String desc() {
        return "Calculate spies for a nation.\n" +
                "Nation argument can be nation name, id, link, or discord tag\n" +
                "If `spies-used` is provided, it will cap the odds at using that number of spies\n" +
                "`[safety]` defaults to what has the best net. Options: quick, normal, covert";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return super.checkPermission(server, user) || Roles.MILCOM.has(user, server) || (Roles.MEMBER.has(user, server) && db.isValidAlliance());
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use `!verify`";
        if (args.size() < 1 || args.size() > 3) {
            return "Usage: `!spy <nation-link> [num-used] [safety]`";
        }

        Integer nationId = DiscordUtil.parseNationId(args.get(0));
        if (nationId == null) {
            return "invalid user/nation: " + nationId;
        }
        Integer cap = 60;
        if (args.size() >= 2) cap = MathMan.parseInt(args.get(1));
        if (cap == null) return "Invalid number of spies used: `" + args.get(1) + "`";

        Integer requiredSafety = null;
        if (args.size() >= 3) {
            switch (args.get(2).toLowerCase()) {
                case "1":
                case "quick":
                    requiredSafety = 1;
                    break;
                case "2":
                case "normal":
                    requiredSafety = 2;
                    break;
                case "3":
                case "covert":
                    requiredSafety = 3;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid safety: " + args.get(2));
            }
        }

        me.setMeta(NationMeta.INTERVIEW_SPIES, (byte) 1);

        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
        int result = SpyCount.guessSpyCount(nation);
        if (nation.getSpies() == null || nation.getSpies() != result) {
            Locutus.imp().getNationDB().setSpies(nation.getNation_id(), result);
            nation.setSpies(result);
            Locutus.imp().getNationDB().addNation(nation);
        }


        StringBuilder response = new StringBuilder(nation.getNation() + " has " + result + " spies.");
        response.append("\nRecommended:");

        int minSafety = requiredSafety == null ? 1 : requiredSafety;
        int maxSafety = requiredSafety == null ? 3 : requiredSafety;

        for (SpyCount.Operation op : SpyCount.Operation.values()) {
            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(true, op != SpyCount.Operation.SOLDIER, 60, nation, minSafety, maxSafety, op);
            if (best == null) continue;

            Map.Entry<Integer, Double> bestVal = best.getValue();
            Integer safetyOrd = bestVal.getKey();
            int recommended = SpyCount.getRecommendedSpies(60, result, safetyOrd, op, nation);
            recommended = Math.min(cap, recommended);

            double odds = SpyCount.getOdds(recommended, result, safetyOrd, op, nation);

            if (op == SpyCount.Operation.SOLDIER && nation.getSoldiers() == 0) op = SpyCount.Operation.INTEL;
            response.append("\n - ").append(op.name()).append(": ");

            String safety = safetyOrd == 3 ? "covert" : safetyOrd == 2 ? "normal" : "quick";

            response.append(recommended + " spies on " + safety + " = " + MathMan.format(Math.min(95, odds)) + "%");
        }
        if (nation.getMissiles() > 0 || nation.getNukes() > 0) {
            long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

            int maxMissile = nation.hasProject(Projects.SPACE_PROGRAM) ? 2 : 1;
            if (nation.getMissiles() == maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought missile today`");
                }
            }

            if (nation.getNukes() == 1) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought nuke today`");
                }
            }
        }

        return response.toString();
    }
}