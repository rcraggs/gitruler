package gitruler;

import org.json.JSONException;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.DecimalFormat;

@CommandLine.Command(name = "java -jar gitruler.jar", mixinStandardHelpOptions = true, version = "Gitruler 1.0")
public class Command implements Runnable {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_PURPLE = "\u001B[35m";

    private static final String NEW_LINE = System.getProperty("line.separator");


    private static final String CORRECT_TICK = ANSI_GREEN + "[\u2713]" + ANSI_RESET;
    private static final String WRONG_CROSS = ANSI_RED + "[\u2718]" + ANSI_RESET;
    private static final String SKIP = ANSI_YELLOW + "[-]" + ANSI_RESET;

    private static DecimalFormat formatter = new DecimalFormat("0.#");

    private static final String DEFAULT_CONFIG_FILENAME = "gitrules.json";

    @Option(names = { "-v", "--verbose" }, description = "Verbose mode. Print system error messages.")
    private boolean verbose;

    @Option(names = { "-c", "--config" }, paramLabel = "Config File Path", description = "The file containing rules")
    private String configFilePath;

    @Option(names = { "-r", "--repo" }, paramLabel = "Repository Path", description = "The repository to test")
    private String repositoryPath;

    @Option(names = { "-a", "--advice" }, description = "Advice mode. How hints and errors")
    private boolean showAdvice;

    private GitRulerConfig config;
    private GitInteractor git;

    public void run() {

        // Read the config
        if (repositoryPath == null){
            repositoryPath = System.getProperty("user.dir");
        }

        // Read the config

        if (configFilePath == null){
            String repoRoot = repositoryPath;
            configFilePath = repoRoot + File.separator +  Command.DEFAULT_CONFIG_FILENAME;
        }

        try {
            File configFile = new File(configFilePath);
            config = new GitRulerConfig(configFile);
        } catch (IOException e) {
            System.out.println("Could not read configuration from " + configFilePath);
            System.exit(1);
        } catch (JSONException e) {
            System.out.println("JSON formatting error in " + configFilePath);
            System.exit(1);
        }

        // Check the there is a repository at the given path
        try {
            git = new GitInteractor(repositoryPath);
        } catch (IOException e) {
            System.out.println(repositoryPath + File.separator + ".git is not a valid git repository");
            System.exit(1);
        }

        try {
            runFileSetup();
        } catch (IOException e) {
            System.out.println("Couldn't create setup files");
            if (verbose){
                e.printStackTrace();
            }
            System.exit(1);
        }

        double totalScore = 0d;

        // Process each of the rules
        boolean skipRemainingRules = false;
        for (Rule r: config.getRules()){

            RuleResult result = git.checkRule(r);
            System.out.println(createOutputFromRuleAndResult(result, r, skipRemainingRules));

            if (result.hasPassed()){
                totalScore += r.getScoreIfCorrect();
            }

            if (!result.hasPassed() && r.stopOnFail()){
                skipRemainingRules = true;
            }
        }

        if (skipRemainingRules){
            System.out.println(ANSI_CYAN + "Skipped rules because a critical rule didn't pass" + ANSI_RESET);
        }

        // Print the total score
        if (config.getTotalAvailableScore() > 0) {
            System.out.println();
            String congratulationsString = "";
            if (totalScore == config.getTotalAvailableScore()) {
                congratulationsString = " Perfect!";
            }
            System.out.println(ANSI_CYAN + "Score: " + formatter.format(totalScore) + " out of " + formatter.format(config.getTotalAvailableScore()) + congratulationsString + ANSI_RESET);
        }
    }

    private void runFileSetup() throws IOException {

        boolean setupRequiredAndSuccessful = false;

        // If this is the first time, then create the setup files
        if (!Files.exists(Paths.get(repositoryPath + File.separator + ".gitruler"),
                LinkOption.NOFOLLOW_LINKS)) {

            for (String path : config.getSetupFiles().keySet()) {

                String content = config.getSetupFiles().get(path);
                Path newFilePath = Paths.get(repositoryPath + File.separator + path);

                try {
                    Files.createDirectories(newFilePath.getParent());

                    if (!Files.exists(newFilePath)) {
                        Files.write(newFilePath, content.getBytes(), StandardOpenOption.CREATE_NEW);
                    }else{
                        Files.write(newFilePath, content.getBytes(), StandardOpenOption.APPEND);
                    }

                    setupRequiredAndSuccessful = true;

                }catch(FileAlreadyExistsException e){
                    if (verbose) {
                        System.out.println("Warning: I was asked to create a file that already exists: " + newFilePath.toString() );
                    }
                }
            }

            Files.write(Paths.get(repositoryPath + File.separator + ".gitruler"), "setup done".getBytes());

            // If the setup ran, print a message
            if (setupRequiredAndSuccessful) {
                System.out.println(ANSI_CYAN + NEW_LINE + "[Info] I ran for the first time and performed the file setup. Now running the rules." + NEW_LINE + ANSI_RESET);
            }
        }
    }

    private String createOutputFromRuleAndResult(RuleResult result, Rule rule, boolean skipRemainingRules) {

        if (rule.getRuleName().equals(Rule.TEXT_RULE_NAME)){
            return rule.getTitle();
        }

        StringBuilder resultString = new StringBuilder();

        if (skipRemainingRules){
            return SKIP + " " + rule.getTitle();
        }

        resultString.append(result.hasPassed() ? CORRECT_TICK : WRONG_CROSS);

        if (rule.hasPreText())
            resultString.append(" ").append(rule.getPreText());

        if (showAdvice || verbose) {
            resultString.append(" ").append(rule.getTitle()).append(" ");
            resultString.append(ANSI_PURPLE).append(result.getMessage()).append(ANSI_RESET);
        }
        else
            resultString.append(" ").append(rule.getTitle());

        if (rule.hasPostText())
            resultString.append(rule.getPostText());

        if (rule.hasFailureMessage() && !result.hasPassed() && showAdvice){
            resultString.append(": ").append(rule.getFailureMessage());
        }

        if (result.exceptionOccurred && verbose){
            resultString.append("Exception:");
            resultString.append(result.getExceptionMessage());
            resultString.append(result.getExceptionTrace());
        }

        if (rule.getScoreIfCorrect() > 0){
            double score = result.hasPassed() ? rule.getScoreIfCorrect() : 0d;
            resultString
                    .append(ANSI_CYAN)
                    .append(" ")
                    .append(formatter.format(score))
                    .append(" / ")
                    .append(formatter.format(rule.getScoreIfCorrect()))
                    .append(ANSI_RESET);
        }

        return resultString.toString();
    }

    public static void main(String[] args) {
        CommandLine.run(new Command(), System.out, args);
    }
}