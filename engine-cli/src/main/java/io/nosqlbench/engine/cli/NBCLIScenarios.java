package io.nosqlbench.engine.cli;

import io.nosqlbench.engine.api.scenarios.NBCLIScenarioParser;
import io.nosqlbench.engine.api.scenarios.WorkloadDesc;

import java.util.List;

public class NBCLIScenarios {

    public static void printWorkloads(
        boolean includeScenarios,
        String... includes
    ) {
        List<WorkloadDesc> workloads = List.of();
        try {
            workloads = NBCLIScenarioParser.getWorkloadsWithScenarioScripts(true, includes);
        } catch (Exception e) {
            throw new RuntimeException("Error while getting workloads:" + e.getMessage(), e);

        }
        for (WorkloadDesc workload : workloads) {
            System.out.println(workload.toMarkdown(includeScenarios));
        }

        if (!includeScenarios) {
            System.out.println("## To see scenarios scenarios, use --list-scenarios");
        }

        System.out.println(
            "## To include examples, add --include=examples\n" +
                "## To copy any of these to your local directory, use\n" +
                "## --include=examples --copy=examplename\n"
        );

    }
}
