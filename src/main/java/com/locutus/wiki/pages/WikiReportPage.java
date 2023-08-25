package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.ReportManager;

import java.util.Arrays;
import java.util.stream.Collectors;

public class WikiReportPage extends WikiGen {
    public WikiReportPage(CommandManager2 manager) {
        super(manager, "reporting");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "# Reporting overview",
                """
                        The goal of the report system is for alliances and player corps to report and access accurate information about problematic behavior of a user/nation.
                        """,
                "# What can be reported",
                """
                If someone is breaking game rules, create a report on the P&W discord, or forums
                - <https://discord.gg/H9XnGxc>
                - <https://forum.politicsandwar.com/index.php?/forum/133-game-reports/>
                
                If there is a violation of discord terms of service, report to discord:
                - <https://discord.com/safety/360044103651-reporting-abusive-behavior-to-discord>
                
                You can additionally report the above to the bot, however reports are solely informational, and will not result in administrative action.
                Please refrain from reporting normal game politics and declarations of war, as these are not reportable offenses.
                
                The following are reportable offenses:""",
                // dot points `- `
                Arrays.stream(ReportManager.ReportType.values()).map(r -> "- `" + r.name() + "`: " + r.getDescription()).collect(Collectors.joining("\n")),
                "# Creating and editing a report",
                commandMarkdown(CM.report.add.cmd),
                commandMarkdown(CM.report.remove.cmd),
                commandMarkdown(CM.report.comment.add.cmd),
                commandMarkdown(CM.report.comment.delete.cmd),
                "",
                "# Viewing reports",
                commandMarkdown(CM.report.analyze.cmd),
                commandMarkdown(CM.report.search.cmd),
                commandMarkdown(CM.report.show.cmd),
                commandMarkdown(CM.nation.list.bans.cmd),
                commandMarkdown(CM.report.sheet.generate.cmd),
                commandMarkdown(CM.settings_orbis_alerts.REPORT_ALERT_CHANNEL.cmd),
                "# A report of me is false or no longer valid",
                "You can add a comment to your report, which may result in a review",
                commandMarkdown(CM.report.comment.add.cmd),
                "Alternatively, you can join the Locutus server and create a ticket",
                "- <https://discord.gg/cUuskPDrB7>",
                "# I've been banned from creating reports",
                """
                This is likely due to reporting false information or misusing the report system.
                Create a ticket in the Locutus server to appeal the ban
                - <https://discord.gg/cUuskPDrB7>""",
                "# I would like to help verify reports",
                """
                Join the Locutus server and create a ticket to request the IA role
                - <https://discord.gg/cUuskPDrB7>
                """,
                "# Administrating reports (Locutus server only)",
                commandMarkdown(CM.report.approve.cmd),
                commandMarkdown(CM.report.purge.cmd),
                commandMarkdown(CM.report.ban.cmd),
                commandMarkdown(CM.report.unban.cmd),
                commandMarkdown(CM.report.upload.legacy_reports.cmd),
                commandMarkdown(CM.report.sheet.generate.cmd)
        );
    }
}
