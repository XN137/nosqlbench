/*
 *
 *    Copyright 2016 jshook
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * /
 */

package io.nosqlbench.engine.core.script;

import io.nosqlbench.engine.core.lifecycle.ScenarioResult;
import io.nosqlbench.engine.core.lifecycle.ScenariosResults;
import org.apache.commons.compress.utils.IOUtils;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class AsyncScriptIntegrationTests {

    public static ScenarioResult runScenario(String scriptname, String... params) {
        if ((params.length % 2) != 0) {
            throw new RuntimeException("params must be pairwise key, value, ...");
        }
        Map<String, String> paramsMap = new HashMap<>();

        for (int i = 0; i < params.length; i += 2) {
            paramsMap.put(params[i], params[i + 1]);
        }
        String scenarioName = "scenario " + scriptname;
        System.out.println("=".repeat(29) + " Running ASYNC integration test for: " + scenarioName);
        ScenariosExecutor e = new ScenariosExecutor(AsyncScriptIntegrationTests.class.getSimpleName() + ":" + scriptname, 1);
        Scenario s = new Scenario(scenarioName, Scenario.Engine.Graalvm,"stdout:300");

        s.addScenarioScriptParams(paramsMap);

        ClassLoader cl = AsyncScriptIntegrationTests.class.getClassLoader();
        String script;
        try {
            InputStream sstream = cl.getResourceAsStream("scripts/async/" + scriptname + ".js");
            byte[] bytes = IOUtils.toByteArray(sstream);
            script = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        s.addScriptText(script);
//        s.addScriptText("load('classpath:scripts/async/" + scriptname + ".js');");
        e.execute(s);
        ScenariosResults scenariosResults = e.awaitAllResults();
        ScenarioResult scenarioResult = scenariosResults.getOne();
        return scenarioResult;
    }

    @BeforeAll
    public static void logit() {
        System.out.println("Running ASYNC version of Script Integration Tests.");
    }

    @Test
    public void testOptimo() {
        ScenarioResult scenarioResult = runScenario("optimo");
        String iolog = scenarioResult.getIOLog();
        System.out.println("iolog\n" + iolog);
//        Pattern p = Pattern.compile(".*mean cycle rate = (\\d[.\\d]+).*", Pattern.DOTALL);
//        Matcher m = p.matcher(iolog);
//        assertThat(m.matches()).isTrue();
//
//        String digits = m.group(1);
//        assertThat(digits).isNotEmpty();
//        double rate = Double.valueOf(digits);
//        assertThat(rate).isCloseTo(1000, Offset.offset(100.0));
//
    }

    @Test
    public void testCycleRate() {
        ScenarioResult scenarioResult = runScenario("cycle_rate");
        String iolog = scenarioResult.getIOLog();
        System.out.println("iolog\n" + iolog);
        Pattern p = Pattern.compile(".*mean cycle rate = (\\d[.\\d]+).*", Pattern.DOTALL);
        Matcher m = p.matcher(iolog);
        assertThat(m.matches()).isTrue();

        String digits = m.group(1);
        assertThat(digits).isNotEmpty();
        double rate = Double.parseDouble(digits);
        assertThat(rate).isCloseTo(1000, Offset.offset(100.0));
    }

// The coverage of this test is also covered by the rate limiter
// specific tests
//    @Test
//    public void testStrideRateOnly() {
//        ScenarioResult scenarioResult = runScenario("stride_rate");
//        String iolog = scenarioResult.getIOLog();
//        System.out.println("iolog\n" + iolog);
//        Pattern p = Pattern.compile(".*stride_rate.strides.servicetime.meanRate = (\\d[.\\d]+).*", Pattern.DOTALL);
//        Matcher m = p.matcher(iolog);
//        assertThat(m.matches()).isTrue();
//
//        String digits = m.group(1);
//        assertThat(digits).isNotEmpty();
//        double rate = Double.valueOf(digits);
//        assertThat(rate).isCloseTo(2500, Offset.offset(500D));
//    }

    @Test
    public void testExtensionPoint() {
        ScenarioResult scenarioResult = runScenario("extensions");
        assertThat(scenarioResult.getIOLog()).contains("sum is 46");
    }

    @Test
    public void testLinkedInput() {
        ScenarioResult scenarioResult = runScenario("linkedinput");
        Pattern p = Pattern.compile(".*started leader.*started follower.*stopped leader.*stopped follower.*",
                Pattern.DOTALL);
        assertThat(p.matcher(scenarioResult.getIOLog()).matches()).isTrue();
    }

    @Test
    public void testExtensionCsvLogger() {
        ScenarioResult scenarioResult = runScenario("extension_csvmetrics");
        assertThat(scenarioResult.getIOLog()).contains("started new " +
                "csvlogger: logs/csvmetricstestdir");
    }


    @Test
    public void testScriptParamsVariable() {
        ScenarioResult scenarioResult = runScenario("params_variable", "one", "two", "three", "four");
        assertThat(scenarioResult.getIOLog()).contains("params[\"one\"]='two'");
        assertThat(scenarioResult.getIOLog()).contains("params[\"three\"]='four'");
        assertThat(scenarioResult.getIOLog()).contains("overridden[\"three\"] [overridden-three-five]='five'");
        assertThat(scenarioResult.getIOLog()).contains("defaulted.get[\"four\"] [defaulted-four-niner]='niner'");
    }

    @Test
    public void testScriptParamsUndefVariableWithOverride() {
        ScenarioResult scenarioResult = runScenario("undef_param", "one", "two", "three", "four");
        assertThat(scenarioResult.getIOLog()).contains("before: params[\"three\"]:four");
        assertThat(scenarioResult.getIOLog()).contains("before: params.three:four");
        assertThat(scenarioResult.getIOLog()).contains("after: params[\"three\"]:undefined");
        assertThat(scenarioResult.getIOLog()).contains("after: params.three:undefined");
    }

    @Test
    public void testExtensionHistoStatsLogger() throws IOException {
        ScenarioResult scenarioResult = runScenario("extension_histostatslogger");
        assertThat(scenarioResult.getIOLog()).contains("stdout started " +
                "logging to logs/histostats.csv");
        List<String> strings = Files.readAllLines(Paths.get(
                "logs/histostats.csv"));
        String logdata = strings.stream().collect(Collectors.joining("\n"));
        assertThat(logdata).contains("min,p25,p50,p75,p90,p95,");
        assertThat(logdata.split("Tag=testhistostatslogger.cycles.servicetime,").length).isGreaterThanOrEqualTo(2);
    }

    @Test
    public void testExtensionCsvOutput() throws IOException {
        ScenarioResult scenarioResult = runScenario("extension_csvoutput");
        List<String> strings = Files.readAllLines(Paths.get(
            "logs/csvoutputtestfile.csv"));
        String logdata = strings.stream().collect(Collectors.joining("\n"));
        assertThat(logdata).contains("header1,header2");
        assertThat(logdata).contains("value1,value2");
    }

    @Test
    public void testExtensionHistogramLogger() throws IOException {
        ScenarioResult scenarioResult = runScenario("extension_histologger");
        assertThat(scenarioResult.getIOLog()).contains("stdout started logging to hdrhistodata.log");
        List<String> strings = Files.readAllLines(Paths.get("hdrhistodata.log"));
        String logdata = strings.stream().collect(Collectors.joining("\n"));
        assertThat(logdata).contains(",HIST");
        assertThat(logdata.split("Tag=testhistologger.cycles.servicetime,").length).isGreaterThanOrEqualTo(3);
    }

    @Test
    public void testBlockingRun() {
        ScenarioResult scenarioResult = runScenario("blockingrun");
        int a1end = scenarioResult.getIOLog().indexOf("blockingactivity1 finished");
        int a2start = scenarioResult.getIOLog().indexOf("running blockingactivity2");
        assertThat(a1end).isLessThan(a2start);
    }

    @Test
    public void testAwaitFinished() {
        ScenarioResult scenarioResult = runScenario("awaitfinished");
    }

    @Test
    public void testStartStop() {
        ScenarioResult scenarioResult = runScenario("startstopdiag");
        int startedAt = scenarioResult.getIOLog().indexOf("starting activity teststartstopdiag");
        int stoppedAt = scenarioResult.getIOLog().indexOf("stopped activity teststartstopdiag");
        assertThat(startedAt).isGreaterThan(0);
        assertThat(stoppedAt).isGreaterThan(startedAt);
    }

    // TODO: find out why this causes a long delay after stop is called.
    @Test
    public void testThreadChange() {
        ScenarioResult scenarioResult = runScenario("threadchange");
        int changedTo1At = scenarioResult.getIOLog().indexOf("threads now 1");
        int changedTo5At = scenarioResult.getIOLog().indexOf("threads now 5");
        System.out.println("IOLOG:\n"+scenarioResult.getIOLog());
        assertThat(changedTo1At).isGreaterThan(0);
        assertThat(changedTo5At).isGreaterThan(changedTo1At);
    }

    @Test
    public void testReadMetric() {
        ScenarioResult scenarioResult = runScenario("readmetrics");
        assertThat(scenarioResult.getIOLog()).contains("count: ");
    }

    @Test
    public void testShutdownHook() {
        ScenarioResult scenarioResult = runScenario("extension_shutdown_hook");
        assertThat(scenarioResult.getIOLog()).doesNotContain("shutdown hook running").describedAs(
            "shutdown hooks should not run in the same IO context as the main scenario"
        );
    }

    @Test
    public void testExceptionPropagationFromMotorThread() {
        ScenarioResult scenarioResult = runScenario("activityerror");
        assertThat(scenarioResult.getException()).isPresent();
        assertThat(scenarioResult.getException().get().getMessage()).contains("For input string: \"unparsable\"");
    }

    @Test
    public void testExceptionPropagationFromActivityInit() {
        ScenarioResult scenarioResult = runScenario("activityiniterror");
        assertThat(scenarioResult.getException()).isPresent();
        assertThat(scenarioResult.getException().get().getMessage()).contains("For input string: \"unparsable\"");
        assertThat(scenarioResult.getException()).isNotNull();
    }

    @Test
    public void testReportedCoDelayBursty() {
        ScenarioResult scenarioResult = runScenario("cocycledelay_bursty");
        assertThat(scenarioResult.getIOLog()).contains("step1 metrics.waittime=");
        assertThat(scenarioResult.getIOLog()).contains("step2 metrics.waittime=");
        String iolog = scenarioResult.getIOLog();
        System.out.println(iolog);
        assertThat(iolog).contains("waittime trended back down as expected");
    }

    @Test
    public void testReportedCoDelayStrict() {
        ScenarioResult scenarioResult = runScenario("cocycledelay_strict");
        assertThat(scenarioResult.getIOLog()).contains("step1 cycles.waittime=");
        assertThat(scenarioResult.getIOLog()).contains("step2 cycles.waittime=");
        String iolog = scenarioResult.getIOLog();
        System.out.println(iolog);
        // TODO: ensure that waittime is staying the same or increasing
        // after investigating minor decreasing effect
    }


//    @Test
//    @Disabled
//    public void testCycleRateChangeOldMetrics() {
//        ScenarioResult scenarioResult = runScenario("cycle_rate_change_deprecated");
//        String ioLog = scenarioResult.getIOLog();
//        assertThat(ioLog).contains("cycles adjusted, exiting on iteration");
//    }

    @Test
    public void testCycleRateChangeNewMetrics() {
        ScenarioResult scenarioResult = runScenario("cycle_rate_change");
        String ioLog = scenarioResult.getIOLog();
        assertThat(ioLog).contains("cycles adjusted, exiting on iteration");
    }

    @Test
    public void testExitLogic() {
        ScenarioResult scenarioResult = runScenario(
                "basicdiag",
                "type", "diag", "cyclerate", "5", "erroroncycle", "10", "cycles", "2000"
        );
    }

}
