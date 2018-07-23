package gitruler;

import org.json.JSONException;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

@CommandLine.Command(name = "testrepo", mixinStandardHelpOptions = true, version = "Gitruler 1.0")
public class Command implements Runnable {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    private static final String CORRECT_TICK = ANSI_GREEN + "[\u2713]" + ANSI_RESET;
    private static final String WRONG_CROSS = ANSI_RED + "[\u2718]" + ANSI_RESET;
    private static final String SKIP = ANSI_YELLOW + "[-]" + ANSI_RESET;


    private static final String DEFAULT_CONFIG_FILENAME = "gitrules.json";

    @Option(names = { "-v", "--verbose" }, description = "Verbose mode. Helpful for troubleshooting. " +
            "Multiple -v options increase the verbosity.")
    private boolean verbose;

    @Option(names = { "-c", "--config" }, paramLabel = "Config File Path", description = "The file containing rules")
    private String configFilePath;

    @Option(names = { "-r", "--repo" }, paramLabel = "Repository Path", description = "The repository to test")
    private String repositoryPath;


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
            config = new GitRulerConfig(configFilePath);
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
            // If this is the first time, then create the setup files
            if (!Files.exists(Paths.get(repositoryPath + File.separator + ".gitruler"),
                    LinkOption.NOFOLLOW_LINKS)) {

                for (String path : config.getSetupFiles().keySet()) {
                    String content = config.getSetupFiles().get(path);
                    Path newFilePath = Paths.get(repositoryPath + File.separator + path);
                    try {
                        Files.createDirectories(newFilePath.getParent());
                        Files.createFile(newFilePath);
                        Files.write(Paths.get(repositoryPath + File.separator + path), content.getBytes());
                    }catch(FileAlreadyExistsException e){
                        if (verbose) {
                            System.out.println("Warning: I was asked to create a file that already exists: " + newFilePath.toString() );
                        }
                    }
                }

                Files.write(Paths.get(repositoryPath + File.separator + ".gitruler"), "setup done".getBytes());
            }
        } catch (IOException e) {
            System.out.println("Couldn't create setup files");
            if (verbose){
                e.printStackTrace();
            }
            System.exit(1);
        }

        // Process each of the rules
        boolean skipRemainingRules = false;
        for (Rule r: config.getRules()){

            RuleResult result = git.checkRule(r);
            System.out.println(createOutputFromRuleAndResult(result, r, skipRemainingRules));

            if (!result.hasPassed() && r.stopOnFail()){
                skipRemainingRules = true;
            }
        }

        if (skipRemainingRules){
            System.out.println(ANSI_CYAN + "Skipped rules because a critical rule didn't pass" + ANSI_RESET);
        }
    }

    private String createOutputFromRuleAndResult(RuleResult result, Rule rule, boolean skipRemainingRules) {

        StringBuilder resultString = new StringBuilder();

        if (skipRemainingRules){
            return SKIP + " " + rule.getTitle();
        }

        resultString.append(result.hasPassed() ? CORRECT_TICK : WRONG_CROSS);

        if (rule.hasPreText())
            resultString.append(" ").append(rule.getPreText());

        resultString.append(" ").append(rule.getTitle()).append(" ").append(result.getMessage());

        if (rule.hasPostText())
            resultString.append(rule.getPostText());

        if (rule.hasFailureMessage() && !result.hasPassed()){
            resultString.append(": ").append(rule.getFailureMessage());
        }

        if (result.exceptionOccurred && verbose){
            resultString.append("Exception:");
            resultString.append(result.getExceptionMessage());
            resultString.append(result.getExceptionTrace());
        }

        return resultString.toString();
    }

    public static void main(String[] args) {
        CommandLine.run(new Command(), System.out, args);
    }
}