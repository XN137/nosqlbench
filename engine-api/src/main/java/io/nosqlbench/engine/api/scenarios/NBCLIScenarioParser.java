package io.nosqlbench.engine.api.scenarios;

import io.nosqlbench.engine.api.activityconfig.StatementsLoader;
import io.nosqlbench.engine.api.activityconfig.rawyaml.RawStmtsLoader;
import io.nosqlbench.engine.api.activityconfig.yaml.Scenarios;
import io.nosqlbench.engine.api.activityconfig.yaml.StmtsDocList;
import io.nosqlbench.engine.api.templating.StrInterpolator;
import io.nosqlbench.nb.api.config.params.Synonyms;
import io.nosqlbench.nb.api.content.Content;
import io.nosqlbench.nb.api.content.NBIO;
import io.nosqlbench.nb.api.content.NBPathsAPI;
import io.nosqlbench.nb.api.errors.BasicError;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NBCLIScenarioParser {

    public final static String SILENT_LOCKED = "==";
    public final static String VERBOSE_LOCKED = "===";
    public final static String UNLOCKED = "=";

    private final static Logger logger = LogManager.getLogger("SCENARIOS");
    private static final String SEARCH_IN = "activities";
    public static final String WORKLOAD_SCENARIO_STEP = "WORKLOAD_SCENARIO_STEP";

    public static boolean isFoundWorkload(String workload, String... includes) {
        Optional<Content<?>> found = NBIO.all()
            .prefix("activities")
            .prefix(includes)
            .name(workload)
            .extension(RawStmtsLoader.YAML_EXTENSIONS)
            .first();
        return found.isPresent();
    }

    public static void parseScenarioCommand(LinkedList<String> arglist,
                                            Set<String> RESERVED_WORDS,
                                            String... includes) {

        String workloadName = arglist.removeFirst();
        Optional<Content<?>> found = NBIO.all()
            .prefix("activities")
            .prefix(includes)
            .name(workloadName)
            .extension(RawStmtsLoader.YAML_EXTENSIONS)
            .first();
//
        Content<?> workloadContent = found.orElseThrow();

//        Optional<Path> workloadPathSearch = NBPaths.findOptionalPath(workloadName, "yaml", false, "activities");
//        Path workloadPath = workloadPathSearch.orElseThrow();

        // Buffer in CLI word from user, but only until the next command
        List<String> scenarioNames = new ArrayList<>();
        while (arglist.size() > 0
            && !arglist.peekFirst().contains("=")
            && !arglist.peekFirst().startsWith("-")
            && !RESERVED_WORDS.contains(arglist.peekFirst())) {
            scenarioNames.add(arglist.removeFirst());
        }
        if (scenarioNames.size() == 0) {
            scenarioNames.add("default");
        }

        // Parse CLI command into keyed parameters, in order
        LinkedHashMap<String, String> userParams = new LinkedHashMap<>();
        while (arglist.size() > 0
            && arglist.peekFirst().contains("=")
            && !arglist.peekFirst().startsWith("-")) {
            String[] arg = arglist.removeFirst().split("=", 2);
            arg[0] = Synonyms.canonicalize(arg[0], logger);
            if (userParams.containsKey(arg[0])) {
                throw new BasicError("duplicate occurrence of option on command line: " + arg[0]);
            }
            userParams.put(arg[0], arg[1]);
        }

        // This will buffer the new command before adding it to the main arg list
        LinkedList<String> buildCmdBuffer = new LinkedList<>();
        StrInterpolator userParamsInterp = new StrInterpolator(userParams);


        for (String scenarioName : scenarioNames) {

            // Load in named scenario
            Content<?> yamlWithNamedScenarios = NBIO.all()
                .prefix(SEARCH_IN)
                .prefix(includes)
                .name(workloadName)
                .extension(RawStmtsLoader.YAML_EXTENSIONS)
                .first().orElseThrow();

            StmtsDocList stmts = StatementsLoader.loadContent(logger, yamlWithNamedScenarios, userParams);

            Scenarios scenarios = stmts.getDocScenarios();

            Map<String, String> namedSteps = scenarios.getNamedScenario(scenarioName);

            if (namedSteps == null) {
                throw new BasicError("Unable to find named scenario '" + scenarioName + "' in workload '" + workloadName
                    + "', but you can pick from one of: " +
                    scenarios.getScenarioNames().stream().collect(Collectors.joining(", ")));
            }

            // each named command line step of the named scenario
            for (Map.Entry<String, String> cmdEntry : namedSteps.entrySet()) {

                String stepName = cmdEntry.getKey();
                String cmd = cmdEntry.getValue();
                cmd = userParamsInterp.apply(cmd);
                LinkedHashMap<String, CmdArg> parsedStep = parseStep(cmd);
                LinkedHashMap<String, String> usersCopy = new LinkedHashMap<>(userParams);
                LinkedHashMap<String, String> buildingCmd = new LinkedHashMap<>();

                // consume each of the parameters from the steps to produce a composited command
                // order is primarily based on the step template, then on user-provided parameters
                for (CmdArg cmdarg : parsedStep.values()) {

                    // allow user provided parameter values to override those in the template,
                    // if the assignment operator used in the template allows for it
                    if (usersCopy.containsKey(cmdarg.getName())) {
                        cmdarg = cmdarg.override(usersCopy.remove(cmdarg.getName()));
                    }

                    buildingCmd.put(cmdarg.getName(), cmdarg.toString());
                }
                usersCopy.forEach((k, v) -> buildingCmd.put(k, k + "=" + v));

                // Undefine any keys with a value of 'undef'
                List<String> undefKeys = buildingCmd.entrySet()
                    .stream()
                    .filter(e -> e.getValue().toLowerCase().endsWith("=undef"))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                undefKeys.forEach(buildingCmd::remove);

                if (!buildingCmd.containsKey("workload")) {
// The logic to remove the leading slash was likely used to fix a nuisance bug before,
// although it is clearly not correct as-is. Leaving temporarily for context.
//                    String relativeWorkloadPathFromRoot = yamlWithNamedScenarios.asPath().toString();
//                    relativeWorkloadPathFromRoot = relativeWorkloadPathFromRoot.startsWith("/") ?
//                        relativeWorkloadPathFromRoot.substring(1) : relativeWorkloadPathFromRoot;
                    buildingCmd.put("workload", "workload=" + workloadName);
                }

                if (!buildingCmd.containsKey("alias")) {
                    buildingCmd.put("alias", "alias=" + WORKLOAD_SCENARIO_STEP);
                }

                String alias = buildingCmd.get("alias");
                for (String token : new String[]{"WORKLOAD", "SCENARIO", "STEP"}) {
                    if (!alias.contains(token)) {
                        logger.warn("Your alias template '" + alias + "' does not contain " + token + ", which will " +
                            "cause your metrics to be combined under the same name. It is strongly advised that you " +
                            "include them in a template like " + WORKLOAD_SCENARIO_STEP + ".");
                    }
                }

                String workloadToken = workloadContent.asPath().getFileName().toString();

                alias = alias.replaceAll("WORKLOAD", sanitize(workloadToken));
                alias = alias.replaceAll("SCENARIO", sanitize(scenarioName));
                alias = alias.replaceAll("STEP", sanitize(stepName));
                alias = (alias.startsWith("alias=") ? alias : "alias=" + alias);
                buildingCmd.put("alias", alias);

                logger.debug("rebuilt command: " + String.join(" ", buildingCmd.values()));
                buildCmdBuffer.addAll(buildingCmd.values());
            }

        }
        buildCmdBuffer.descendingIterator().forEachRemaining(arglist::addFirst);

    }

    public static String sanitize(String word) {
        String sanitized = word;
        sanitized = sanitized.replaceAll("\\..+$", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9]+", "");
        return sanitized;
    }

    private static final Pattern WordAndMaybeAssignment = Pattern.compile("(?<name>\\w[-_\\d\\w.]+)((?<oper>=+)(?<val>.+))?");

    private static LinkedHashMap<String, CmdArg> parseStep(String cmd) {
        LinkedHashMap<String, CmdArg> parsedStep = new LinkedHashMap<>();

        String[] namedStepPieces = cmd.split(" ");
        for (String commandFragment : namedStepPieces) {
            Matcher matcher = WordAndMaybeAssignment.matcher(commandFragment);
            if (!matcher.matches()) {
                throw new BasicError("Unable to recognize scenario cmd spec in '" + commandFragment + "'");
            }
            String commandName = matcher.group("name");
            commandName = Synonyms.canonicalize(commandName, logger);
            String assignmentOp = matcher.group("oper");
            String assignedValue = matcher.group("val");
            parsedStep.put(commandName, new CmdArg(commandName, assignmentOp, assignedValue));
        }
        return parsedStep;
    }

    private final static class CmdArg {
        private final String name;
        private final String operator;
        private final String value;
        private String scenarioName;

        public CmdArg(String name, String operator, String value) {
            this.name = name;
            this.operator = operator;
            this.value = value;
        }

        public boolean isReassignable() {
            return UNLOCKED.equals(operator);
        }

        public boolean isFinalSilent() {
            return SILENT_LOCKED.equals(operator);
        }

        public boolean isFinalVerbose() {
            return VERBOSE_LOCKED.equals(operator);
        }


        public CmdArg override(String value) {
            if (isReassignable()) {
                return new CmdArg(this.name, this.operator, value);
            } else if (isFinalSilent()) {
                return this;
            } else if (isFinalVerbose()) {
                throw new BasicError("Unable to reassign value for locked param '" + name + operator + value + "'");
            } else {
                throw new RuntimeException("impossible!");
            }
        }

        @Override
        public String toString() {
            return name + (operator != null ? "=" : "") + (value != null ? value : "");
        }

        public String getName() {
            return name;
        }
    }

    private static final Pattern templatePattern = Pattern.compile("TEMPLATE\\((.+?)\\)");
    private static final Pattern innerTemplatePattern = Pattern.compile("TEMPLATE\\((.+?)$");
    private static final Pattern templatePattern2 = Pattern.compile("<<(.+?)>>");

    public static List<WorkloadDesc> filterForScenarios(List<Content<?>> candidates) {

        List<Path> yamlPathList = candidates.stream().map(Content::asPath).collect(Collectors.toList());

        List<WorkloadDesc> workloadDescriptions = new ArrayList<>();

        for (Path yamlPath : yamlPathList) {

            try {

                String referenced = yamlPath.toString();

                if (referenced.startsWith("/")) {
                    if (yamlPath.getFileSystem() == FileSystems.getDefault()) {
                        Path relative = Paths.get(System.getProperty("user.dir")).toAbsolutePath().relativize(yamlPath);
                        if (!relative.toString().contains("..")) {
                            referenced = relative.toString();
                        }
                    }
                }

                Content<?> content = NBIO.all().prefix(SEARCH_IN)
                    .name(referenced).extension(RawStmtsLoader.YAML_EXTENSIONS)
                    .one();

                StmtsDocList stmts = StatementsLoader.loadContent(logger, content);
                if (stmts.getStmtDocs().size() == 0) {
                    logger.warn("Encountered yaml with no docs in '" + referenced + "'");
                    continue;
                }

                Map<String, String> templates = new LinkedHashMap<>();
                try {
                    List<String> lines = Files.readAllLines(yamlPath);
                    for (String line : lines) {
                        templates = matchTemplates(line, templates);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


                Scenarios scenarios = stmts.getDocScenarios();

                List<String> scenarioNames = scenarios.getScenarioNames();

                if (scenarioNames != null && scenarioNames.size() > 0) {
//                String path = yamlPath.toString();
//                path = path.startsWith(FileSystems.getDefault().getSeparator()) ? path.substring(1) : path;
                    LinkedHashMap<String, String> sortedTemplates = new LinkedHashMap<>();
                    ArrayList<String> keyNames = new ArrayList<>(templates.keySet());
                    Collections.sort(keyNames);
                    for (String keyName : keyNames) {
                        sortedTemplates.put(keyName, templates.get(keyName));
                    }

                    String description = stmts.getDescription();
                    workloadDescriptions.add(new WorkloadDesc(referenced, scenarioNames, sortedTemplates, description, ""));
                }
            } catch (Exception e) {
                throw new RuntimeException("Error while scanning path '" + yamlPath.toString() + "':" + e.getMessage(), e);
            }

        }
        Collections.sort(workloadDescriptions);

        return workloadDescriptions;

    }

    public static List<WorkloadDesc> getWorkloadsWithScenarioScripts(boolean defaultIncludes, Set<String> includes) {
        return getWorkloadsWithScenarioScripts(defaultIncludes, includes.toArray(new String[0]));
    }

    public static List<WorkloadDesc> getWorkloadsWithScenarioScripts(boolean defaultIncludes, String... includes) {

        NBPathsAPI.GetPrefix searchin = NBIO.all();
        if (defaultIncludes) {
            searchin = searchin.prefix(SEARCH_IN);
        }

        List<Content<?>> activities = searchin
            .prefix(includes)
            .extension(RawStmtsLoader.YAML_EXTENSIONS)
            .list();

        return filterForScenarios(activities);

    }

    public static List<String> getScripts(boolean defaultIncludes, String... includes) {

        NBPathsAPI.GetPrefix searchin = NBIO.all();
        if (defaultIncludes) {
            searchin = searchin.prefix(SEARCH_IN);
        }

        List<Path> scriptPaths = searchin
            .prefix("scripts/auto")
            .prefix(includes)
            .extension("js")
            .list().stream().map(Content::asPath).collect(Collectors.toList());

        List<String> scriptNames = new ArrayList();

        for (Path scriptPath : scriptPaths) {
            String name = scriptPath.getFileName().toString();
            name = name.substring(0, name.lastIndexOf('.'));

            scriptNames.add(name);
        }


        return scriptNames;

    }

    public static Map<String, String> matchTemplates(String line, Map<String, String> templates) {
        Matcher matcher = templatePattern.matcher(line);

        while (matcher.find()) {
            String match = matcher.group(1);

            Matcher innerMatcher = innerTemplatePattern.matcher(match);
            String[] matchArray = match.split(",");
//            if (matchArray.length!=2) {
//                throw new BasicError("TEMPLATE form must have two arguments separated by a comma, like 'TEMPLATE(a,b), not '" + match +"'");
//            }
            //TODO: support recursive matches
            if (innerMatcher.find()) {
                String[] innerMatch = innerMatcher.group(1).split("[,:]");

                //We want the outer name with the inner default value
                templates.put(matchArray[0], innerMatch[1]);
            } else {
                templates.put(matchArray[0], matchArray[1]);
            }
        }
        matcher = templatePattern2.matcher(line);

        while (matcher.find()) {
            String match = matcher.group(1);
            String[] matchArray = match.split(":");
            if (matchArray.length == 1) {
                templates.put(matchArray[0], "-none-");
            } else {
                templates.put(matchArray[0], matchArray[1]);
            }
        }
        return templates;
    }


}
