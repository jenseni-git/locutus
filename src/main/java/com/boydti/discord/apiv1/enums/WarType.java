package com.boydti.discord.apiv1.enums;

public enum WarType {
    RAID("raid", "raid"),
    ORD("ordinary", "ord"),
    ATT("attrition", "att"),
    NUCLEAR("nuclear", "nuke")

    ;

    public static WarType[] values = values();
    private final String name;
    private final String bountyName;

    WarType(String name, String bountyName) {
        this.name = name;
        this.bountyName = bountyName;
    }

    public String getBountyName() {
        return bountyName;
    }

    @Override
    public String toString() {
        return name;
    }

    public static WarType parse(String input) {
        try {
            return valueOf(input.toUpperCase());
        } catch (IllegalArgumentException e) {
            for (WarType type : values) {
                if (type.name.equalsIgnoreCase(input)) {
                    return type;
                }
            }
            throw e;
        }
    }
}
