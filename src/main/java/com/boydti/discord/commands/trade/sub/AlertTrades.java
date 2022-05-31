package com.boydti.discord.commands.trade.sub;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.TradeDB;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class AlertTrades extends Command {
    public AlertTrades() {
        super("Alert-Trades", "AlertTrades", "SubscribeTrades", "Subscribe-Trades", "Alert-Trade", "AlertTrade", "SubscribeTrade", "Subscribe-Trade", "Subscribe-Trade", "SubscribeTrade", "Sub-Trade", "SubTrade",
                CommandCategory.ECON, CommandCategory.MEMBER, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return "!alert-trades <resource> <buy|sell> >/< <ppu> <duration>";
    }

    @Override
    public String desc() {
        return "Subscribe (for a duration) to get alerts about trades e.g. `!alert-trades food buy < 75 3days`\n" +
                " - Where `BUY` = BUYER WANTED page, and `SELL` = SELLER WANTED page";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 5) {
            return usage(event);
        }
        ResourceType resource;
        try {
            resource = ResourceType.parse(args.get(0));
        } catch (IllegalArgumentException e) {
            return "Invalid resource type: `" + args.get(0) + "`" + ". Valid values are: " + StringMan.getString(ResourceType.values);
        }

        boolean isBuy = args.get(1).equalsIgnoreCase("buy");
        if (!isBuy && !args.get(1).equalsIgnoreCase("sell")) {
            return "Invalid category `" + args.get(1) + "`" + ". Must be either `buy` or `sell`";
        }

        boolean above;
        int offset = 0;

        switch (args.get(2)) {
            case ">":
                above = true;
                break;
            case ">=":
                offset = -1;
                above = true;
                break;
            case "<":
                above = false;
                break;
            case "<=":
                above = false;
                offset = 1;
                break;
            default:
                return "Invalid conditional: `" + args.get(2) + "`" + ". Must be either `>` or `<`";
        }

        Integer ppu = MathMan.parseInt(args.get(3));
        if (ppu == null) {
            return "Invalid ppu (number): `" + args.get(3) + "`";
        }
        ppu += offset;

        long now = System.currentTimeMillis();
        long msOffset = TimeUtil.timeToSec(args.get(4)) * 1000;
        long date = now + msOffset;

        TradeDB db = Locutus.imp().getTradeManager().getTradeDb();
        User user = event.getAuthor();

        db.subscribe(user, resource, date, isBuy, above, ppu, TradeDB.TradeAlertType.ABSOLUTE);

        return "Subscribed to `" + DiscordUtil.trimContent(event.getMessage().getContentRaw()).toUpperCase() + "`" +
                "\nCheck your subscriptions with: `!trade-subs`";
    }
}
