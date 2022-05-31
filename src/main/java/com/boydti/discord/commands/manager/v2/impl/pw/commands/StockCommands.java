package com.boydti.discord.commands.manager.v2.impl.pw.commands;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.bank.BankWith;
import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.binding.annotation.Range;
import com.boydti.discord.commands.manager.v2.binding.annotation.Step;
import com.boydti.discord.commands.manager.v2.binding.annotation.Switch;
import com.boydti.discord.commands.manager.v2.binding.annotation.Timediff;
import com.boydti.discord.commands.manager.v2.binding.annotation.Timestamp;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.commands.rankings.builder.SummedMapRankBuilder;
import com.boydti.discord.commands.stock.Exchange;
import com.boydti.discord.commands.stock.StockDB;
import com.boydti.discord.commands.stock.StockTrade;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.NationOrExchange;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StockCommands {
    @Command(desc = "Create a share buy offer")
    public String buy(StockDB db, @Me DBNation me, Exchange exchange, double quantity, @Range(min=0.01) @Step(0.01) double maxprice) {
        if (!exchange.canView(me)) return exchange.name + " requires you to be " + exchange.requiredRank + " to buy/sell";
        if (exchange.id == ResourceType.MONEY.ordinal() || exchange.id == ResourceType.CREDITS.ordinal()) throw new IllegalArgumentException("Cannot buy/sell " + exchange.symbol);
        Map.Entry<Long, Long> currentPrice = db.getCurrentPrice(exchange.id);
        long priceLong = (long) (maxprice * 100);
        long quantityLong = (long) (quantity * 100);

        List<String> output = new ArrayList<>();

        if (currentPrice.getValue() != null && priceLong >= currentPrice.getValue()) {
            List<StockTrade> open = db.getOpenTradesByCorp(exchange.id);
            open.removeIf(f -> f.buyer != 0);
            open.removeIf(f -> f.price > priceLong);
            if (!open.isEmpty()) {
                Collections.sort(open, (o1, o2) -> Long.compare(o1.price, o2.price));
                for (StockTrade trade : open) {
                    // TODO dont buy from yourself
                    if (quantityLong <= 0) break;
                    long amtPurchase = Math.min(trade.amount, quantityLong);
                    try {
                        long totalPrice = amtPurchase * trade.price;
                        db.buyTrade(trade, amtPurchase, me.getNation_id());

                        quantityLong -= amtPurchase;

                        String info = (trade.buyer == me.getNation_id() ? "Bought" : "Sold") + " " +
                                MathMan.format(amtPurchase / 100d) +
                                "x" +
                                db.getName(trade.company) +
                                " for " +
                                (MathMan.format(totalPrice / 100d));
                        output.add(info);
                    } catch (IllegalArgumentException e) {
                        String info = "Could not accept trade: " + trade + " (" + e.getMessage() + ")";
                        output.add(info);
                    }
                }
            }
        }
        if (quantityLong > 0) {
            StockTrade trade = new StockTrade(exchange.id, me.getNation_id(), true, ResourceType.MONEY.ordinal(), quantityLong, priceLong);
            db.addTrade(trade);
            output.add("Created buy offer: " + trade);
        }
        return StringMan.join(output, "\n");
    }

    @Command(desc = "Create a sell offer. If buy offers are available for that price, it will be filled (with confirmation)")
    public String sell(StockDB db, @Me DBNation me, Exchange exchange, double quantity, @Range(min=0.01) @Step(0.01) double minprice) {
        if (!exchange.canView(me)) return exchange.name + " requires you to be " + exchange.requiredRank + " to buy/sell";
        if (exchange.id == ResourceType.MONEY.ordinal() || exchange.id == ResourceType.CREDITS.ordinal()) throw new IllegalArgumentException("Cannot buy/sell " + exchange.symbol);
        Map.Entry<Long, Long> currentPrice = db.getCurrentPrice(exchange.id);
        long priceLong = (long) (minprice * 100);
        long quantityLong = (long) (quantity * 100);

        List<String> output = new ArrayList<>();

        if (currentPrice.getKey() != null && priceLong <= currentPrice.getKey()) {
            List<StockTrade> open = db.getOpenTradesByCorp(exchange.id);
            open.removeIf(f -> f.seller != 0);
            open.removeIf(f -> f.price < priceLong);
            if (!open.isEmpty()) {
                Collections.sort(open, (o1, o2) -> Long.compare(o2.price, o1.price));
                for (StockTrade trade : open) {
                    // TODO dont buy from yourself
                    if (quantityLong <= 0) break;
                    long amtPurchase = Math.min(trade.amount, quantityLong);
                    try {
                        long totalPrice = amtPurchase * trade.price;
                        db.sellTrade(trade, amtPurchase, me.getNation_id());

                        quantityLong -= amtPurchase;

                        String info = (trade.buyer == me.getNation_id() ? "Bought" : "Sold") + " " +
                                MathMan.format(amtPurchase / 100d) +
                                "x" +
                                db.getName(trade.company) +
                                " for " +
                                (MathMan.format(totalPrice / 100d));
                        output.add(info);
                    } catch (IllegalArgumentException e) {
                        String info = "Could not accept trade: " + trade + " (" + e.getMessage() + ")";
                        output.add(info);
                    }
                }
            }
        }
        if (quantityLong > 0) {
            StockTrade trade = new StockTrade(exchange.id, me.getNation_id(), false, ResourceType.MONEY.ordinal(), (long) (quantity * 100), (long) (minprice * 100));
            db.addTrade(trade);
            output.add("Created sell offer: " + trade);
        }
        return StringMan.join(output, "\n");
    }

    @Command(desc = "Cancel all your share trades")
    public String cancelall(StockDB db, @Me DBNation me) {
        Collection<StockTrade> myTrades = db.getOpenTrades(me.getNation_id()).values();
        if (myTrades.isEmpty()) {
            return "You have no open trades";
        }
        StringBuilder response = new StringBuilder();
        for (StockTrade trade : myTrades) {
            if (db.deleteTrade(trade.tradeId)) {
                response.append("Deleted trade:\n" + trade.toString());
            } else {
                response.append("Failed to delete trade:\n" + trade.toString());
            }
        }
        return response.toString();
    }

    @Command(desc = "Cancel your share trades for an exchange")
    public String cancel(StockDB db, @Me DBNation me, Exchange exchange) {
        Collection<StockTrade> myTrades = db.getOpenTrades(me.getNation_id()).values();
        myTrades.removeIf(f -> f.company != exchange.id);
        if (myTrades.isEmpty()) {
            return "You have no trade matching exchange: " + exchange.symbol;
        }
        StringBuilder response = new StringBuilder();
        for (StockTrade trade : myTrades) {
            if (db.deleteTrade(trade.tradeId)) {
                response.append("Deleted trade:\n" + trade.toString());
            } else {
                response.append("Failed to delete trade:\n" + trade.toString());
            }
        }
        return response.toString();
    }

    @Command(desc = "Cancel a share trade by id")
    public String cancelid(StockDB db, @Me DBNation me, int tradeId) {
        Map<Integer, StockTrade> myTrades = db.getOpenTrades(me.getNation_id());
        StockTrade trade = myTrades.get(tradeId);
        if (trade == null) {
            return "You have no trade matching id: " + tradeId;
        }
        if (trade.buyer != 0 && trade.seller != 0) {
            return "Your trade " + tradeId + " has already been finalized";
        }
        if (db.deleteTrade(tradeId)) {
            return "Deleted trade:\n" + trade.toString();
        } else {
            return "Failed to delete trade:\n" + trade.toString();
        }
    }

    @Command(aliases = {"balance", "bal", "deposits", "safekeep"}, desc = "Show a nations balance info")
    public String nation(@Me MessageChannel channel, StockDB db, @Me DBNation me, DBNation nation) {
        return info(channel, db, me, new NationOrExchange(nation));
    }

    @Command(aliases = {"mybalance", "me"}, desc = "Show your own balance info")
    public String me(@Me MessageChannel channel, StockDB db, @Me DBNation me) {
        return info(channel, db, me, new NationOrExchange(me));
    }

    private String info(@Me MessageChannel channel, StockDB db, @Me DBNation me, NationOrExchange nationOrExchange) {
        if (nationOrExchange.isExchange()) {
            Exchange exchange = nationOrExchange.getExchange();
            if (!exchange.canView(me)) return "No permission for this exchange";
        }
        Map<Exchange, Long> myShares = db.getSharesByNation(nationOrExchange.getId());
        if (myShares.isEmpty()) {
            return "You have no shares or resources in your account";
        }

        Map<ResourceType, Double> resourceShares = new LinkedHashMap<>();
        Iterator<Map.Entry<Exchange, Long>> iter = myShares.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Exchange, Long> entry = iter.next();
            long shares = entry.getValue();
            Exchange corp = entry.getKey();
            if (corp.isResource()) {
                resourceShares.put(corp.getResource(), shares / 100d);
                iter.remove();
            }
        }

        double total = 0;

        StringBuilder response = new StringBuilder();
        if (!resourceShares.isEmpty()) {
            double rssValue = PnwUtil.convertedTotal(resourceShares);
            total += rssValue;
            response.append("**Resources**: worth: ~$" + MathMan.format(rssValue)).append("\n");
            response.append(PnwUtil.resourcesToString(resourceShares)).append("\n");
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        for (Map.Entry<Exchange, Long> entry : myShares.entrySet()) {
            Exchange exchange = entry.getKey();
            double valuePerShare = db.getCombinedAveragePrice(exchange, cutoff);
            long shares = entry.getValue();

            total += valuePerShare * (shares / 100d);

            response.append("**" + exchange.symbol + ":** " + MathMan.format(shares / 100d) + "\n");
        }

        String title = nationOrExchange.getName() + " shares";
        String footer = "Total Equity: $" + MathMan.format(total);
        DiscordUtil.createEmbedCommandWithFooter(channel, title, response.toString(), footer);

        return null;
    }

    @Command(aliases = {"info", "exchangeinfo"}, desc="Show general info about an exchange")
    public String info(@Me MessageChannel channel, StockDB db, Exchange exchange) {
        String body = exchange.toString();
        DiscordUtil.createEmbedCommand(channel, exchange.symbol, body.toString());
        return null;
    }

    @Command(aliases = {"exchanges", "search", "find", "companies"}, desc="List exchanges matching input")
    public String exchanges(StockDB db, @Me DBNation me, String filter) {
        filter = filter.toLowerCase();
        List<String> equals = new ArrayList<>();
        List<Map.Entry<String, Integer>> matches = new ArrayList<>();
        List<String> desc = new ArrayList<>();
        for (Map.Entry<String, Exchange> entry : db.getExchanges().entrySet()) {
            Exchange exchange = entry.getValue();
            if (!exchange.canView(me)) continue;

            String name = entry.getKey();
            String nameBold = name.replaceAll("(?i)(" + filter + ")", "\\*\\*$1\\*\\*");
            if (entry.getKey().equalsIgnoreCase(filter)) {
                equals.add("**" + name + "**");
            }
            else if (entry.getKey().toLowerCase().contains(filter)) {
                int distance = StringMan.getLevenshteinDistance(filter, name.toLowerCase());
                matches.add(new AbstractMap.SimpleEntry<>(nameBold, distance));
            } else if (exchange.description.toLowerCase().matches(".*\b" + filter + "\b.*")) {
                desc.add(exchange.symbol);
            }
        }
        Collections.sort(matches, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return Integer.compare(o1.getValue(), o2.getValue());
            }
        });
        List<String> all = new ArrayList<>();
        all.addAll(equals);
        all.addAll(matches.stream().map(f -> f.getKey()).collect(Collectors.toList()));
        all.addAll(desc);
        if (!all.isEmpty()) {
            return "Matching exchanges:\n - " + StringMan.join(all, "\n - ");
        }
        return "No exchanges found for: `" + filter + "`";
    }

    @Command(desc = "List the buy/sell prices for exchanges")
    public String price(@Me MessageChannel channel, @Me Message message, StockDB db, List<Exchange> exchanges) {
        List<String> exchangeNames = exchanges.stream().map(f -> f.symbol).collect(Collectors.toList());

        Map<Exchange, Double> low = new HashMap<>();
        Map<Exchange, Double> high = new HashMap<>();
        for (Exchange exchange : exchanges) {
            Map.Entry<Long, Long> price = db.getCurrentPrice(exchange.id);
            if (price.getKey() != null) low.put(exchange, price.getKey() / 100d);
            if (price.getValue() != null) high.put(exchange, price.getValue() / 100d);
        }

        String refreshEmoji = "\uD83D\uDD04";
        DiscordUtil.createEmbedCommand(channel, b -> {
            ArrayList<String> lowList = new ArrayList<>();
            ArrayList<String> highList = new ArrayList<>();

            for (Exchange type : exchanges) {
                Double o1 = low.get(type);
                Double o2 = high.get(type);

                lowList.add(o1 == null ? "" : MathMan.format(o1));
                highList.add(o2 == null ? "" : MathMan.format(o2));
            }

            b.addField("Exchange", StringMan.join(exchangeNames, "\n"), true);
            b.addField("Buying", StringMan.join(lowList, "\n"), true);
            b.addField("Selling", StringMan.join(highList, "\n"), true);
        }, refreshEmoji, DiscordUtil.trimContent(message.getContentRaw()));
        return null;
    }

    @Command(desc = "List any resources (and their margin) which are out of sync with ingame prices")
    public String rssmargin(@Me MessageChannel channel, @Me Message message, StockDB db, @Switch('p') boolean usePercent) {
        StringBuilder response = new StringBuilder();
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS || type == ResourceType.MONEY) continue;
            int marketSell = Locutus.imp().getTradeManager().getPrice(type, false); // low
            int marketBuy = Locutus.imp().getTradeManager().getPrice(type, true); // high

            if (marketBuy <= marketSell) continue;

            Map.Entry<Long, Long> price = db.getCurrentPrice(type.ordinal());
            if (price.getKey() != null && (price.getKey() / 100d) > marketBuy) {
                response.append("\nBuy " + type + " ingame @ $" + MathMan.format(marketBuy) + " and sell on exchange for $" + MathMan.format(price.getKey() / 100d));
                // buy ingame and sell stock
            }
            if (price.getValue() != null && (price.getValue() / 100d) < marketSell) {
                response.append("\nBuy " + type + " on exchange @ $" + MathMan.format(price.getValue() / 100d) + " and sell on exchange for $" + MathMan.format(marketSell));
                // buy stock and sell ingame
            }
        }
        if (response.length() == 0) return "Exchange prices all conform to ingame margins";
        return response.toString().trim();
    }

    @Command(desc = "List the buy/sell margin for exchanges")
    public String margin(@Me MessageChannel channel, @Me Message message, StockDB db, List<Exchange> exchanges, @Switch('p') boolean usePercent) {
        List<String> exchangeNames = exchanges.stream().map(f -> f.symbol).collect(Collectors.toList());

        List<String> margin = new ArrayList<>();

        for (Exchange exchange : exchanges) {
            Map.Entry<Long, Long> price = db.getCurrentPrice(exchange.id);
            if (price.getKey() == null || price.getValue() == null) {
                margin.add("");
                continue;
            }

            double diff = (price.getValue() - price.getKey()) / 100d;

            if (usePercent) {
                diff = diff / price.getValue();
            }
            margin.add((MathMan.format(diff) + (usePercent ? "%" : "")));
        }
        String refreshEmoji = "\uD83D\uDD04";

        DiscordUtil.createEmbedCommand(channel, b -> {
            b.addField("Exchange", StringMan.join(exchangeNames, "\n"), true);
            b.addField("Margin", StringMan.join(margin, "\n"), true);
        }, refreshEmoji, DiscordUtil.trimContent(message.getContentRaw()));

        return null;
    }

    @Command(desc = "List a nation's trade profit")
    public String profit(StockDB db, DBNation nation, @Timestamp long time) {
        List<StockTrade> trades = db.getClosedTradesByNation(nation.getNation_id(), time);

        Map<Integer, Double> netOutflows = new LinkedHashMap<>();
        Map<Integer, Double> inflows = new LinkedHashMap<>();
        Map<Integer, Double> outflow = new LinkedHashMap<>();
        Map<Integer, Double> purchases = new LinkedHashMap<>();
        Map<Integer, Double> purchasesPrice = new LinkedHashMap<>();
        Map<Integer, Double> sales = new LinkedHashMap<>();
        Map<Integer, Double> salesPrice = new LinkedHashMap<>();

        for (StockTrade trade : trades) {
            double per = trade.price / 100d;
            int type = trade.resource;

            int sign = trade.buyer == nation.getNation_id() ? 1 : -1;
            long total = trade.amount * (long) per;

            if (sign > 0) {
                inflows.put(type, trade.amount + inflows.getOrDefault(type, 0d));
                sales.put(type, trade.amount + sales.getOrDefault(type, 0d));
                salesPrice.put(type, total + salesPrice.getOrDefault(type, 0d));
            } else {
                outflow.put(type, trade.amount + outflow.getOrDefault(type, 0d));
                purchases.put(type, trade.amount + purchases.getOrDefault(type, 0d));
                purchasesPrice.put(type, total + purchasesPrice.getOrDefault(type, 0d));
            }

            netOutflows.put(type, ((-1) * sign * trade.amount) + netOutflows.getOrDefault(type, 0d));
            netOutflows.put(ResourceType.MONEY.ordinal(), (sign * total) + netOutflows.getOrDefault(ResourceType.MONEY.ordinal(), 0d));
        }

        Map<Integer, Double> ppuBuy = new LinkedHashMap<>();
        Map<Integer, Double> ppuSell = new LinkedHashMap<>();

        for (Map.Entry<Integer, Double> entry : purchases.entrySet()) {
            int type = entry.getKey();
            ppuBuy.put(type, (double) purchasesPrice.get(type) / entry.getValue());
        }

        for (Map.Entry<Integer, Double> entry : sales.entrySet()) {
            int type = entry.getKey();
            ppuSell.put(type, (double) salesPrice.get(type) / entry.getValue());
        }

        double profitTotal = db.convertedTotal(netOutflows);
        double profitMin = 0;
        profitTotal = Math.min(profitTotal, profitMin);

        HashMap<ResourceType, Double> totalVolume = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values()) {
            double in  = inflows.getOrDefault(type, 0d);
            double out  = outflow.getOrDefault(type, 0d);
            double total = Math.abs(in) + Math.abs(out);
            if (total != 0) totalVolume.put(type, total);
        }

        StringBuilder response = new StringBuilder();
        response
                .append('\n').append("Buy (PPU):```")
                .append(String.format("%16s", StringMan.getString(ppuBuy)))
                .append("```")
                .append(' ').append("Sell (PPU):```")
                .append(String.format("%16s", StringMan.getString(ppuSell)))
                .append("```")
                .append(' ').append("Net inflows:```")
                .append(String.format("%16s", StringMan.getString(netOutflows)))
                .append("```")
                .append(' ').append("Total Volume:```")
                .append(String.format("%16s", StringMan.getString(totalVolume)))
                .append("```");
        response.append("Profit total: $").append(MathMan.format(profitTotal));
        return response.toString().trim();
    }

    @Command(desc = "List average buy/sell price of an exchange over X days")
    public String average(@Me MessageChannel channel, @Me Message message, @Me DBNation me, StockDB db, List<Exchange> exchanges, @Timediff long time) {
        List<String> exchangeNames = exchanges.stream().map(f -> f.symbol).collect(Collectors.toList());

        Map<Exchange, Double> low = new HashMap<>();
        Map<Exchange, Double> high = new HashMap<>();
        for (Exchange exchange : exchanges) {
            if (!exchange.canView(me)) return exchange.name + " requires you to be " + exchange.requiredRank + " to view";
            Map.Entry<Double, Double> price = db.getAveragePrice(exchange, time);
            if (price.getKey() != null) low.put(exchange, price.getKey());
            if (price.getValue() != null) high.put(exchange, price.getValue());
        }

        String refreshEmoji = "\uD83D\uDD04";
        DiscordUtil.createEmbedCommand(channel, b -> {
            ArrayList<String> lowList = new ArrayList<>();
            ArrayList<String> highList = new ArrayList<>();

            for (Exchange type : exchanges) {
                Double o1 = low.get(type);
                Double o2 = high.get(type);

                lowList.add(o1 == null ? "" : MathMan.format(o1));
                highList.add(o2 == null ? "" : MathMan.format(o2));
            }

            b.addField("Exchange", StringMan.join(exchangeNames, "\n"), true);
            b.addField("Buying", StringMan.join(lowList, "\n"), true);
            b.addField("Selling", StringMan.join(highList, "\n"), true);
        }, refreshEmoji, DiscordUtil.trimContent(message.getContentRaw()));
        return null;
    }

    @Command(desc = "List open offers for an exchange")
    public String market(@Me MessageChannel channel, @Me Message message, @Me DBNation me, StockDB db, Exchange exchange, @Switch('b') boolean onlyBuyOffers, @Switch('s') boolean onlySellOffers, @Switch('p') int page) {
        if (!exchange.canView(me)) return exchange.name + " requires you to be " + exchange.requiredRank + " to view";
        Map.Entry<List<StockTrade>, List<StockTrade>> buySell = db.getBuySellOffersByCorp(exchange.id);
        List<StockTrade> buy = buySell.getKey();
        List<StockTrade> sell = buySell.getValue();

        StringBuilder result = new StringBuilder();

        String cmdBase = DiscordUtil.trimContent(message.getContentRaw());
        int perPage = 15;
        if (!onlySellOffers) {
            if (buy.isEmpty()) result.append("No buy offers");
            else {
                Collections.sort(buy, (o1, o2) -> Long.compare(o2.price, o1.price));
                String title = "Top Buy Offers";
                List<String> results = new ArrayList<>();

                for (StockTrade trade : sell) {
                    results.add(trade.amount + "x " + exchange.symbol + " @ $" + MathMan.format(trade.price / 100d));
                }
                String cmd = cmdBase + (onlyBuyOffers ? "" : " -b");
                DiscordUtil.paginate(channel, title, cmd, perPage, page, results);
            }
        }
        if (!onlyBuyOffers) {
            if (sell.isEmpty()) result.append("\nNo sell offers");
            else {
                Collections.sort(sell, Comparator.comparingLong(o -> o.price));
                String title = "Top Sell Offers";
                List<String> results = new ArrayList<>();

                for (StockTrade trade : sell) {
                    results.add(trade.amount + "x " + exchange.symbol + " @ $" + MathMan.format(trade.price / 100d));
                }

                String cmd = cmdBase + (onlySellOffers ? "" : " -s");
                DiscordUtil.paginate(channel, title, cmd, perPage, page, results);
            }
        }
        return result.length() > 0 ? result.toString() : null;
    }

    @Command(desc = "List your open offers")
    public String mytrades(@Me MessageChannel channel, @Me Message message, StockDB db, @Me DBNation nation, @Switch('b') boolean onlyBuyOffers, @Switch('s') boolean onlySellOffers, @Switch('p') int page) {
        ArrayList<StockTrade> trades = new ArrayList<>(db.getOpenTrades(nation.getNation_id()).values());
        if (trades.isEmpty()) return "No open trades";
        Collections.sort(trades, (o1, o2) -> Double.compare(o2.date_offered, o1.date_offered));

        List<StockTrade> buy = new ArrayList<>();
        List<StockTrade> sell = new ArrayList<>();
        for (StockTrade trade : trades) {
            if (trade.buyer == 0) sell.add(trade);
            else if (trade.seller == 0) buy.add(trade);
        }

        boolean hasResult = false;
        StringBuilder result = new StringBuilder();

        String cmdBase = DiscordUtil.trimContent(message.getContentRaw());
        int perPage = 15;
        if (!onlySellOffers) {
            if (buy.isEmpty()) result.append("No buy offers");
            else {
                Collections.sort(buy, (o1, o2) -> Long.compare(o2.price, o1.price));
                String title = "Top Buy Offers";
                List<String> results = new ArrayList<>();

                for (StockTrade trade : sell) {
                    results.add("#" + trade.tradeId + ": " + trade.amount + "x " + db.getName(trade.company) + " @ $" + MathMan.format(trade.price / 100d));
                }
                String cmd = cmdBase + (onlyBuyOffers ? "" : " -b");
                DiscordUtil.paginate(channel, title, cmd, perPage, page, results);
            }
        }
        if (!onlyBuyOffers) {
            if (sell.isEmpty()) result.append("\nNo sell offers");
            else {
                Collections.sort(sell, Comparator.comparingLong(o -> o.price));
                String title = "Top Sell Offers";
                List<String> results = new ArrayList<>();

                for (StockTrade trade : sell) {
                    results.add("#" + trade.tradeId + ": " + trade.amount + "x " + db.getName(trade.company) + " @ $" + MathMan.format(trade.price / 100d));
                }

                String cmd = cmdBase + (onlySellOffers ? "" : " -s");
                DiscordUtil.paginate(channel, title, cmd, perPage, page, results);
            }
        }
        return result.length() > 0 ? result.toString() : null;
    }

    @Command(desc = "List a nations share transactions")
    public String transactions(@Me MessageChannel channel, @Me Message message, @Me DBNation me, StockDB db, DBNation nation, @Switch('p') int page) {
        int id = nation.getNation_id();
        List<StockTrade> trades = db.getClosedTradesByNation(id, 0);
        trades.removeIf(f -> (f.is_buying ? f.buyer : f.seller) != id);
        if (trades.isEmpty()) return "No trade history";

        Collections.sort(trades, (o1, o2) -> Double.compare(o2.date_bought, o1.date_bought));
        List<String> results = new ArrayList<>();

        Map<Integer, Exchange> exchangeMap = new HashMap<>();
        for (StockTrade trade : trades) {
            Exchange exchange = exchangeMap.computeIfAbsent(trade.company, f -> db.getExchange(f));
            if (exchange != null && !exchange.canView(me)) continue;
            results.add(trade.toString());
        }

        int perPage = 15;
        int pages = (results.size() + perPage - 1) / perPage;
        String title = "Transactions (" + (page + 1) + "/" + pages + ")";

        String cmd = DiscordUtil.trimContent(message.getContentRaw());
        DiscordUtil.paginate(channel, title, cmd, perPage, page, results);

        return null;
    }

    @Command(desc = "List exchange shareholders")
    public String shareholders(@Me MessageChannel channel, @Me Message message, StockDB db, @Me DBNation me, Exchange exchange, @Switch('p') int page) {
        if (!exchange.canView(me)) return exchange.name + " requires you to be " + exchange.requiredRank + " to view";

        Map<Integer, Long> shareholders = db.getShareholdersByCorp(exchange.id);
        if (shareholders.isEmpty()) return "No shareholders";

        List<String> results = new SummedMapRankBuilder<>(shareholders).sort().name(nationId -> PnwUtil.getName(nationId, false), f -> MathMan.format(f / 100d)).get();

        int perPage = 15;
        int pages = (results.size() + perPage - 1) / perPage;
        String title = "Shareholders (" + (page + 1) + "/" + pages + ")";

        String cmd = DiscordUtil.trimContent(message.getContentRaw());
        DiscordUtil.paginate(channel, title, cmd, perPage, page, results);

        return null;
    }

    @Command(desc = "List a nations shares")
    public String shares(@Me MessageChannel channel, @Me Message message, StockDB db, NationOrExchange nation, @Switch('p') int page) {
        Map<Exchange, Long> shares = db.getSharesByNation(nation.getId());
        if (shares.isEmpty()) return "No shareholders";

        List<String> results = new SummedMapRankBuilder<>(shares).sort().name(exchange -> exchange.symbol, f -> MathMan.format(f / 100d)).get();

        int perPage = 15;
        int pages = (results.size() + perPage - 1) / perPage;
        String title = "Shares (" + (page + 1) + "/" + pages + ")";

        String cmd = DiscordUtil.trimContent(message.getContentRaw());
        DiscordUtil.paginate(channel, title, cmd, perPage, page, results);

        return null;
    }

    // TODO give from company to nation
    // TODO withdraw from company to nation

    @Command(desc = "Give some of your shares to another nation")
    @RolePermission(value={Roles.ECON}, root=true)
    public String give(@Me MessageChannel channel, StockDB db, @Me DBNation me, NationOrExchange receiver, Exchange exchange, @Range(min=0.01) double amount, @Switch('f') boolean confirm, @Switch('a') boolean anonymous) {
        Map.Entry<Boolean, String> result = new NationOrExchange(me).give(channel, me, receiver, exchange, amount, anonymous);
        return result.getValue();
    }

    @Command(desc = "Withdraw your cash/resources from the exchange")
    public String withdraw(@Me MessageChannel channel, @Me Message message, StockDB db, @Me DBNation me, DBNation receiver, Map<ResourceType, Double> resources, @Switch('f') boolean force) throws IOException {
        if (receiver.isBlockaded()) throw new IllegalArgumentException("Receiver is blockaded");
        if (receiver.getVm_turns() != 0) throw new IllegalArgumentException("Receiver is on vacation mode");

        if (!force) {
            String title = "Confirm transfer worth: $" + MathMan.format(PnwUtil.convertedTotal(resources));
            String body = "Amount: " + PnwUtil.resourcesToString(resources) + "\nTo:" + receiver.getNation() + " | " + receiver.getAlliance();
            DiscordUtil.pending(channel, message, title, body, 'f');
            return null;
        }

        return withdraw(db, me, receiver.getNationUrl(), resources);
    }

    private String withdraw(StockDB db, DBNation sender, String receiver, Map<ResourceType, Double> resources) {
        Map<Exchange, Long> shares = db.getSharesByNation(sender.getId());
        StringBuilder response = new StringBuilder();

        synchronized (db) {
            for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                long current = shares.getOrDefault(entry.getKey().ordinal(), 0L);
                long requiredLong = (long) (entry.getValue() * 100d);
                if (requiredLong <= 0)
                    throw new IllegalArgumentException("You must specify positive amounts to withdraw");
                if (requiredLong < current)
                    throw new IllegalArgumentException("You do not have " + MathMan.format(entry.getValue()) + " " + entry.getKey().name());
            }

            GuildDB guildDb = Locutus.imp().getGuildDB(StockDB.ROOT_GUILD);
            MessageChannel channel = guildDb.getOrThrow(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);

            Map<ResourceType, Double> transfer = new HashMap<>();
            for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                ResourceType type = entry.getKey();
                long amtLong = (long) (entry.getValue() * 100d);
                if (db.transferShare(type.ordinal(), sender.getId(), 0, amtLong)) {
                    transfer.put(type, entry.getValue());
                } else {
                    response.append("Your withdrawal of " + MathMan.format(entry.getValue()) + "x" + type + " could not be processed. Please try again\n");
                }
            }

            String title = "Withdraw ~$" + MathMan.format(PnwUtil.convertedTotal(transfer));

            StringBuilder body = new StringBuilder();
            body.append(sender.getUserDiscriminator()).append("\n");
            body.append("From: " + sender.getNationUrlMarkup(true) + " | " + sender.getAllianceUrlMarkup(true)).append("\n");
            body.append("To: " + receiver).append("\n");
            body.append("Amount: `" + PnwUtil.resourcesToString(transfer) + "`").append("\n");

            String emoji = "\u2705";

            UUID token = UUID.randomUUID();
            BankWith.authorized.add(token);
            String transferStr = StringMan.getString(transfer);
            String transferCmd = "!transfer " + receiver + " " + transferStr + " #ignore -f -g:" + token;
            String dmCmd = "!dm " + sender.getNationUrl() + " 'Your withdrawal of `" + transferStr + "` has been processed'";
            String command = transferCmd + "\n" + dmCmd;
            DiscordUtil.createEmbedCommand(channel, title, body.toString(), emoji, command);

            response.append("Requested withdrawal of: `" + transferStr + "`. Please wait");
        }
        return response.toString();
    }

    @Command(desc = "Withdraw your cash/resources from the exchange")
    public String withdrawAA(@Me MessageChannel channel, @Me Message message, StockDB db, @Me DBNation me, Alliance alliance, Map<ResourceType, Double> resources, @Switch('f') boolean force) {
        if (!force) {
            String title = "Confirm transfer worth: $" + MathMan.format(PnwUtil.convertedTotal(resources));
            String body = "Amount: " + PnwUtil.resourcesToString(resources) + "\nTo AA:" + alliance.getName() + "(" + alliance.getNations(true, 0, true).size() + " members)";
            DiscordUtil.pending(channel, message, title, body, 'f');
            return null;
        }
        return withdraw(db, me, alliance.getUrl(), resources);
    }

    @Command(desc = "Graph of price history for exchanges")
    public String history(StockDB db, List<Exchange> exchanges, @Timestamp long time) {
        for (Exchange exchange : exchanges) {
            List<StockTrade> trades = db.getTradesBoughtByCorp(exchange.id, time);
        }
        // TODO graph of price history
        return "TODO graph of price history";
    }
}