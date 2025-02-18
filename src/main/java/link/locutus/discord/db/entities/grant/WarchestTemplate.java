package link.locutus.discord.db.entities.grant;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Grant;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class WarchestTemplate extends AGrantTemplate<Map<ResourceType, Double>> {
    private final double[] allowancePerCity;
    private final long trackDays;
    private final boolean subtractExpenditure;
    private final long overdrawPercentCents;
    public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), ArrayUtil.toDoubleArray(rs.getBytes("allowance_per_city")), rs.getLong("track_days"), rs.getBoolean("subtract_expenditure"), rs.getLong("overdraw_percent_cents"),
                rs.getLong("expire"),
                rs.getBoolean("allow_ignore"),
                rs.getBoolean("repeatable"));
    }

    // create new constructor  with typed parameters instead of resultset
    public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, double[] allowancePerCity, long trackDays, boolean subtractExpenditure, long overdrawPercentCents, long expiryOrZero, boolean allowIgnore, boolean repeatable) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, allowIgnore, repeatable);
        this.allowancePerCity = allowancePerCity;
        this.trackDays = trackDays;
        this.subtractExpenditure = subtractExpenditure;
        this.overdrawPercentCents = overdrawPercentCents;
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowIgnore, String repeatable) {
        return CM.grant_template.create.warchest.cmd.create(name,
                allowedRecipients,
                allowancePerCity == null ? null : PnwUtil.resourcesToString(allowancePerCity),
                trackDays <= 0 ? null : trackDays + "",
                subtractExpenditure ? "true" : null,
                overdrawPercentCents <= 0 ? null : overdrawPercentCents + "",
                econRole,
                selfRole,
                bracket,
                useReceiverBracket,
                maxTotal,
                maxDay,
                maxGranterDay,
                maxGranterTotal, allowExpire, allowIgnore,
                isRepeatable() ? null : "true",
                null).toSlashCommand();
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver, Map<ResourceType, Double> parsed) {
        StringBuilder result = new StringBuilder();
        // add the fields as "key: value"
        if (allowancePerCity != null) {
            result.append("allowance per city: " + PnwUtil.resourcesToString(allowancePerCity));
        }
        if (trackDays > 0) {
            result.append("track days: " + trackDays);
        }
        if (subtractExpenditure) {
            result.append("subtract expenditure: true");
        }
        if (overdrawPercentCents > 0) {
            result.append("overdraw percent: " + (overdrawPercentCents / 100d) + "%");
        }
        return result.toString();
    }

    public Map<ResourceType, Double> getWarchestPerCity(DBNation nation) {
        Map<ResourceType, Double> result = this.getWarchestPerCity(nation);
        if (result == null || result.isEmpty()) {
            result = getDb().getPerCityWarchest(nation);
        }
        return result;
    }

    @Override
    public Map<ResourceType, Double> parse(DBNation receiver, String value) {
        Map<ResourceType, Double> perCityMax = getWarchestPerCity(receiver);
        if (value == null) {
            return new LinkedHashMap<>(perCityMax);
        }
        Map<ResourceType, Double> result = super.parse(receiver, value);
        if (result == null) {
            result = new LinkedHashMap<>(perCityMax);
        } else {
            for (Map.Entry<ResourceType, Double> entry : result.entrySet()) {
                ResourceType resource = entry.getKey();
                Double amount = entry.getValue();
                // if negative
                if (amount < 0) {
                    throw new IllegalArgumentException("Negative amount for " + resource + " in " + value);
                }
                // if greater than perCityMax
                if (amount > perCityMax.get(resource)) {
                    throw new IllegalArgumentException("Amount for " + resource + " in " + value + " is greater than per city max of " + perCityMax.get(resource));
                }
                if (resource == ResourceType.CREDITS) {
                    throw new IllegalArgumentException("Credits are not allowed in warchest grants");
                }
            }
        }
        return result;
    }

    @Override
    public String toListString() {
        StringBuilder result = new StringBuilder(super.toListString());
        if (subtractExpenditure) {
            result.append(" | expenditure");
        }
        if (allowancePerCity != null) {
            result.append(" | city=");
            result.append(PnwUtil.resourcesToString(allowancePerCity));
        }
        return result.toString();
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.WARCHEST;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("allowance_per_city");
        list.add("track_days");
        list.add("subtract_expenditure");
        list.add("overdraw_percent_cents");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setBytes(16, ArrayUtil.toByteArray(allowancePerCity));
        stmt.setLong(17, trackDays);
        stmt.setBoolean(18, subtractExpenditure);
        stmt.setLong(19, overdrawPercentCents);
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(@Nullable DBNation sender, @Nullable DBNation receiver, Map<ResourceType, Double> parsed) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender, receiver, parsed);
        list.addAll(getRequirements(sender, receiver, this, parsed));
        return list;
    }

    public static List<Grant.Requirement> getRequirements(DBNation sender, DBNation receiver, WarchestTemplate template, Map<ResourceType, Double> parsed) {
        List<Grant.Requirement> list = new ArrayList<>();

        if (template == null || parsed != null) {
            list.add(new Grant.Requirement("Amount must NOT be negative: `" + (template == null ? "{amount}" : PnwUtil.resourcesToString(parsed)) + "`", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    for (Map.Entry<ResourceType, Double> entry : parsed.entrySet()) {
                        if (entry.getValue() < 0) {
                            return false;
                        }
                    }
                    return true;
                }
            }));

            double[] allowance;
            StringBuilder allowanceStr = new StringBuilder();
            if (receiver != null) {
                allowance = template.getCost(null, sender, receiver, parsed, allowanceStr);
            } else {
                allowance = null;
            }
            list.add(new Grant.Requirement("Amount must NOT exceed remaining allowance: `" + (template == null ? "{amount}" : PnwUtil.resourcesToString(parsed)) + "` > `" + (allowance == null ? "{allowance_remaining}" : allowance) + "`\n" +
                    allowanceStr, false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    for (Map.Entry<ResourceType, Double> entry : parsed.entrySet()) {
                        if (entry.getValue() < 0) {
                            return false;
                        }
                    }
                    return true;
                }
            }));
        }

        return list;
    }

    @Override
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Map<ResourceType, Double> parsed) {
        return getCost(db, sender, receiver, parsed, null);
    }

    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Map<ResourceType, Double> parsed, StringBuilder debugOutput) {
        double[] allowance;
        if (allowancePerCity != null) {
            allowance = PnwUtil.multiply(allowancePerCity.clone(), receiver.getCities());
        } else {
            allowance = PnwUtil.resourcesToArray(getDb().getPerCityWarchest(receiver));
        }

        long cutoff = trackDays <= 0 ? 0 : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(trackDays);
        double[] spent = ResourceType.getBuffer();
        if (this.subtractExpenditure) {
            // get war cost
            AttackCost cost = Locutus.imp().getWarDb().queryAttacks().withWarSet(f -> f.getWarsByNation(receiver.getId())).afterDate(cutoff).toCost(
                    (war, attack) -> attack.getAttacker_id() == receiver.getId(),
                    "",
                    "",
                    false,
                    false,
                    false,
                    false,
                    false
            );
            spent = ResourceType.add(spent, PnwUtil.resourcesToArray(cost.getNetCost(true)));
            PnwUtil.max(spent, ResourceType.getBuffer());
        }

        // received #warchest
        double[] received = ResourceType.getBuffer();

        for (Transaction2 record : receiver.getTransactions(true)) {
            if(record.tx_datetime > cutoff && record.note != null && record.sender_id == receiver.getId()) {
                Map<String, String> notes = PnwUtil.parseTransferHashNotes(record.note);
                if (notes.containsKey("#warchest")) {
                    received = ResourceType.add(received, record.resources);
                }
            }
        }
        if (overdrawPercentCents > 0) {
            double factor = overdrawPercentCents * 0.0001;
            if (!ResourceType.isZero(received)) {
                received = PnwUtil.multiply(received, 1 - factor);
            }
            if (!ResourceType.isZero(spent)) {
                spent = PnwUtil.multiply(spent, 1 + factor);
            }
        }

        if (debugOutput != null) {
            debugOutput.append("[Allowance: `" + PnwUtil.resourcesToString(allowance) + "`, ");
            debugOutput.append("Received: `" + PnwUtil.resourcesToString(received) + "`, ");
            debugOutput.append("Spent: `" + PnwUtil.resourcesToString(spent) + "`]");
        }

        double[] canSend = ResourceType.builder(allowance).subtract(received).add(spent).min(allowance).build();
        if (parsed != null) {
            canSend = PnwUtil.min(canSend, PnwUtil.resourcesToArray(parsed));
        }
        return canSend;
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Map<ResourceType, Double> parsed) {
        return DepositType.WARCHEST.withValue();
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Map<ResourceType, Double> parsed) {
        return DepositType.WARCHEST.getDescription();
    }
    @Override
    public Class<Map<ResourceType, Double>> getParsedType() {
        return (Class<Map<ResourceType, Double>>) TypeToken.getParameterized(Map.class, ResourceType.class, Double.class).getRawType();
    }
}
