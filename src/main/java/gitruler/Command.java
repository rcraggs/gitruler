package gitruler;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;

@CommandLine.Command(name = "testrepo", mixinStandardHelpOptions = true, version = "Gitruler 1.0")
public class Command implements Runnable {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";

    private static final String CORRECT_TICK = ANSI_GREEN + "[\u2713]" + ANSI_RESET;
    private static final String WRONG_CROSS = ANSI_RED + "[\u2718]" + ANSI_RESET;


    private static final String DEFAULT_CONFIG_FILENAME = "gitrules.json";

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
            if (repoRoot.endsWith(".git")){
                repoRoot = repoRoot.substring(0, repoRoot.length() - 4);
            }
            configFilePath = repoRoot + File.separator +  Command.DEFAULT_CONFIG_FILENAME;
        }

        try {
            config = new GitRulerConfig(configFilePath);
        } catch (IOException e) {
            System.out.println("Could not read configuration from " + configFilePath);
            System.exit(1);
        }

        // Check the there is a repository at the given path
        try {
            git = new GitInteractor(repositoryPath);
        } catch (IOException e) {
            System.out.println(repositoryPath + File.separator + ".git is not a valid git repository");
            System.exit(1);
        }

        // Process each of the rules
        for (Rule r: config.getRules()){
            RuleResult result = git.checkRule(r);
            String resultString = result.hasPassed() ? CORRECT_TICK : WRONG_CROSS;
            resultString += " " + r.getTitle() + " " + result.getMessage();

            System.out.println(resultString);

            if (!result.hasPassed() && r.stopOnFail()){
                System.out.println(ANSI_CYAN + "Exiting because a critical rule didn't pass" + ANSI_RESET);
                System.exit(0);
            }
        }
    }

    public static void main(String[] args) {
        System.setProperty("picocli.trace", "OFF");
        CommandLine.run(new Command(), System.out, args);
    }
}