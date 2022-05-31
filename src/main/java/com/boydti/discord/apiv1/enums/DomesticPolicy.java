package com.boydti.discord.apiv1.enums;

public enum DomesticPolicy {
    MANIFEST_DESTINY,
    OPEN_MARKETS,
    TECHNOLOGICAL_ADVANCEMENT,
    IMPERIALISM,
    URBANIZATION,
    RAPID_EXPANSION,

    ;

    public static DomesticPolicy parse(String policy) {
        return DomesticPolicy.valueOf(policy.toUpperCase().replace(" ", "_"));
    }
}
