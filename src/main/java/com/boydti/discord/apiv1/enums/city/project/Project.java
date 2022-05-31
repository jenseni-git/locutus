package com.boydti.discord.apiv1.enums.city.project;

import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.apiv1.enums.ResourceType;

import java.util.Map;

public interface Project {
    Map<ResourceType, Double> cost();

    default int get(DBNation nation) {
        return nation.hasProject(this) ? 1 : 0;
    }

    String name();

    String getApiName();

    int ordinal();

    ResourceType getOutput();

    default boolean has(long bitMask) {
        return (bitMask & (1 << ordinal() + 1)) != 0;
    }

    String getImageName();

    default String getImageUrl() {
        String name = getImageName();
        return "" + Settings.INSTANCE.PNW_URL() + "/img/projects/" + name + (name.contains(".") ? "" : ".jpg");
    }
}