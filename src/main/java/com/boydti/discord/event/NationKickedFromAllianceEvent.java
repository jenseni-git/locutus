package com.boydti.discord.event;

import com.boydti.discord.pnw.DBNation;

public class NationKickedFromAllianceEvent extends Event{
    private final DBNation previous;
    private final DBNation current;

    public NationKickedFromAllianceEvent(DBNation previous, DBNation current) {
        this.previous = previous;
        this.current = current;
    }

    public DBNation getPrevious() {
        return previous;
    }

    public DBNation getCurrent() {
        return current;
    }
}
