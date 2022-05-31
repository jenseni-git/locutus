package com.boydti.discord.apiv1.queries;

import com.boydti.discord.apiv1.core.UrlBuilder;
import com.boydti.discord.apiv1.domains.Nation;
import com.boydti.discord.apiv1.enums.QueryURL;

public class NationQuery extends Query {

  public NationQuery(int nid, String apiKey) {
    super(Integer.toString(nid), apiKey);
  }

  @Override
  public ApiQuery build() {
    return new ApiQuery<>(UrlBuilder.build(QueryURL.NATION_URL, args), new Nation());
  }
}
