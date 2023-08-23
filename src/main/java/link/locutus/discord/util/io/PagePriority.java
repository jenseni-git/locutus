package link.locutus.discord.util.io;
public enum PagePriority {
    ACTIVE_PAGE(0, 0),
    TOKEN(0, 0),
    LOGOUT(0, 0),
    LOGIN(0, 0),
    FETCH_KEY(0, 0),
    API_BANK_SEND(0, 0),
    BANK_SEND(0, 0),
    API_BANK_DEPOSIT(0, 0),
    BANK_DEPOSIT(0, 0),
    BANK_TRADE(0, 0),
    API_BOT_KEY(0, 5000),
    API_BANK_RECS_MANUAL(1000, 5000),
    ALLIANCE_ID_AUTH_CODE(0, 5000),
    RANK_SET(0, 5000),
    API_KEY_STATS(0, 5000),
    API_TREATY_SEND(0, 5000),
    API_TREATY_APPROVE(0, 5000),
    API_TREATY_CANCEL(0, 5000),
    API_TAX_ASSIGN(0, 5000),
    API_TAX_CREATE(0, 5000),
    API_TAX_EDIT(0, 5000),
    API_TAX_DELETE(0, 5000),
    API_OCR(0, 5000),
    EMBARGO(0, 5000),
    PUSHER(4000, 5000),
    ALLIANCE_EDIT(0, 5000),
    NATION_EDIT(0, 5000),
    API_CITIES_MANUAL(1000, 5000),
    API_NATIONS_MANUAL(1000, 5000),
    API_ALLIANCES_MANUAL(1000, 5000),
    API_BANK_RECS_AUTO(5000, 60000),
    ESPIONAGE_ODDS_SINGLE(1000, 60000),
    API_GAME_TIME(250, 5000),
    API_COLOR_GET(250, 5000),
    MAIL_REPLY(1000, 5000),
    MAIL_SEND_SINGLE(1000, 5000),
    MAIL_READ(1000, 5000),
    MAIL_SEARCH(1000, 5000),
    WORLD_GRAPHS(250, 5000),
    API_NATIONS_AUTO(5000, 60000),
    API_ALLIANCES_AUTO(5000, 60000),
    API_CITIES_AUTO(5000, 60000),
    API_ATTACKS(5000, 60000),
    API_WARS(5000, 60000),
    ESPIONAGE_ODDS_BULK(1000, 60000),
    MAIL_SEND_BULK(10000, 15000),
    API_TRADE_GET(5000, 60000),
    API_TRADE_PRICE(5000, 60000),
    TRADE_HISTORY(5000, 60000),
    API_TREATIES(15000, 60000),
    API_TREASURES(10000, 60000),
    API_BOUNTIES(10000, 60000),
    API_BANS(5000, 60000),
    ESPIONAGE_ODDS_AUTO(10000, 60000),
    API_BASEBALL(10000, 60000),
    API_EMBARGO_GET(10000, 60000),
    // unimportant
    IMPORT_BUILD(5000, 5000),
    PLACE_BOUNTY(10000, 10000),
    COMMEND(10000, 10000),
    CITY_NAME(10000, 10000),
    // background
    DISCORD_IDS_ENDPOINT(5000, 5000),
    DISCORD_EMOJI_URL(5000, 5000),
    TAXES_GET_LEGACY(5000, 10000),
    NATION_UID_MANUAL(5000, 10000),
    NATION_UID_AUTO(3600000, 3600000),
    DATA_DUMP(60000, 60000),
    FORUM_PAGE(60000, 60000),
    KENO(60000, 60000),
    ESPIONAGE_FULL_UNUSED(60000, 60000),
    GET_BRACKETS_UNUSED(60000, 60000),
    GET_TREATIES_UNUSED(60000, 60000),
    NATION_STATS_UNUSED(60000, 60000),
    ACTIVE_WARS_UNUSED(60000, 60000),
    BRACKET_SET_UNUSED(60000, 60000),
    MODIFY_TREATY_UNUSED(60000, 60000),

    ;

    public static final PagePriority[] values = values();
    private final int allowedBufferingMs;
    private final int allowableDelayMs;

    PagePriority(int allowedBufferingMs, int allowableDelayMs) {
        this.allowedBufferingMs = allowedBufferingMs;
        this.allowableDelayMs = allowableDelayMs;
    }

    public int getAllowedBufferingMs() {
        return allowedBufferingMs;
    }

    public int getAllowableDelayMs() {
        return allowableDelayMs;
    }
}
