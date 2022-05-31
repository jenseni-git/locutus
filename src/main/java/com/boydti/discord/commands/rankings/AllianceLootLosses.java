package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.builder.RankBuilder;
import com.boydti.discord.commands.rankings.builder.SummedMapRankBuilder;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.google.common.collect.BiMap;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
import com.boydti.discord.apiv1.enums.AttackType;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AllianceLootLosses extends Command {
    public AllianceLootLosses() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <time> [alliances]";
    }

    @Override
    public String desc() {
        return "Calculate the total losses from alliance loot over a specified timeframe";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 2) return usage(event);
        if (args.size() == 1) args.add("*");

        long millis = TimeUtil.timeToSec(args.get(0)) * 1000L;
        long cutOff = System.currentTimeMillis() - millis;

        Map<Integer, Double> allianceScores = new HashMap<>();
        List<DBNation> allNations = new ArrayList<>(DiscordUtil.parseNations(guild, args.get(1)));
        allNations.removeIf(n -> n.getVm_turns() > 0 || n.getPosition() <= 1);
        for (DBNation nation : allNations) {
            allianceScores.put(nation.getAlliance_id(), nation.getScore() + allianceScores.getOrDefault(nation.getAlliance_id(), 0d));
        }

        Map<Integer, Double> totals = new HashMap<>();
        List<DBAttack> aaLoot = Locutus.imp().getWarDb().getAttacks(cutOff, AttackType.A_LOOT);
        for (DBAttack attack : aaLoot) {
            Map<ResourceType, Double> loot = attack.getLoot();
            Integer allianceId = attack.getLooted();
            if (allianceId == null || allianceId == 0) continue;

            Double existing = totals.getOrDefault(allianceId, 0d);
            totals.put(allianceId, existing + PnwUtil.convertedTotal(loot));
        }

        totals.entrySet().removeIf(e -> !allianceScores.containsKey(e.getKey()) || e.getValue() <= 0);

        BiMap<Integer, String> aas = Locutus.imp().getNationDB().getAlliances();

        RankBuilder<String> ranks = new SummedMapRankBuilder<>(totals).sort().nameKeys(i -> aas.getOrDefault(i, Integer.toString(i)));


        String title = "Alliance bank loot losses (" + args.get(0) + ")";

        ranks.build(event, title);

        if (ranks.get().size() > 25) {
            DiscordUtil.upload(event.getGuildChannel(), title, ranks.toString());
        }

        return super.onCommand(event, guild, author, me, args, flags);
    }
}
