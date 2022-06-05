package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.pnw.DBNation;

import java.lang.reflect.Type;
import java.util.function.Function;

public class NationMetric<T> implements Metric<DBNation, T> {
    private final Function<DBNation, T> parent;
    private final Type type;
    private final String name;
    private final String desc;

    public NationMetric(String name, String desc, Type type, Function<DBNation, T> parent) {
        this.type = type;
        this.parent = parent;
        this.name = name;
        this.desc = desc;
    }


    public String getName() {
        return this.name;
    }

    public Type getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public T apply(DBNation nation) {
        return parent.apply(nation);
    }
}