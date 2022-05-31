package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.builder.GroupedRankBuilder;
import com.boydti.discord.commands.rankings.builder.RankBuilder;
import com.boydti.discord.commands.rankings.builder.SummedMapRankBuilder;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.google.common.collect.BiMap;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class NetProfitPerWar extends Command {
    public NetProfitPerWar() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        int days = 3;
        boolean profit = true;

        Set<Integer> AAs = null;
        String id = "AA";

        for (String arg : args) {
            if (MathMan.isInteger(arg)) {
                days = Integer.parseInt(arg);
            } else if (arg.equalsIgnoreCase("false")) {
                profit = false;
            } else if (arg.equalsIgnoreCase("*")) {
                id = "Nation";
                AAs = new HashSet<>();
            } else {
                id = arg;
                AAs = DiscordUtil.parseAlliances(DiscordUtil.getDefaultGuild(event), arg);
            }
        }
        int sign = profit ? -1 : 1;
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        String title = id + " Net " + (profit ? "profit" : "losses") + " per war (%s days)";
        title = String.format(title, days);

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        BiMap<Integer, String> allianceNames = Locutus.imp().getNationDB().getAlliances();

        Set<Integer> finalAAs = AAs;
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(cutoffMs);

        SummedMapRankBuilder<Integer, Number> byNation = new RankBuilder<>(attacks)
                .group((BiConsumer<DBAttack, GroupedRankBuilder<Integer, DBAttack>>) (attack, map) -> {
                    // Group attacks into attacker and defender
                    map.put(attack.attacker_nation_id, attack);
                    map.put(attack.defender_nation_id, attack);
                }).map((i, a) -> a.war_id,
                        // Convert attack to profit value
                        (nationdId, attack) -> {
                            DBNation nation = nations.get(nationdId);
                            return nation != null ? sign * attack.getLossesConverted(attack.attacker_nation_id == nationdId) : 0;
                        })
                // Average it per war
                .average();

        RankBuilder<String> ranks;
        if (AAs == null) {
            // Group it by alliance
            ranks = byNation.<Integer > group((entry, builder) -> {
            DBNation nation = nations.get(entry.getKey());
            if (nation != null) {
                builder.put(nation.getAlliance_id(), entry.getValue());
            }
            })
            // Average it per alliance
            .average()
            // Sort descending
            .sort()
            // Change key to alliance name
            .nameKeys(allianceId -> allianceNames.getOrDefault(allianceId, Integer.toString(allianceId)))
            .limit(25);
        } else {
            // Sort descending
            ranks = byNation
                    .removeIfKey(nationId -> !nations.containsKey(nationId) || (!finalAAs.isEmpty() && !finalAAs.contains(nations.get(nationId).getAlliance_id())))
                    .sort()
            // Change key to alliance name
            .nameKeys(nationId -> nations.get(nationId).getNation())
            .limit(25);
        }

        // Embed the rank list
        ranks.build(event, title);


        return null;
    }
}