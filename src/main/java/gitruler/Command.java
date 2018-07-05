package gitruler;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;

@CommandLine.Command(name = "testrepo", mixinStandardHelpOptions = true, version = "Gitruler 1.0")
public class Command implements Runnable {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_RED_BACKGROUND = "\u001B[41m";
    public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
    public static final String ANSI_YELLOW_BACKGROUND = "\u001B[43m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_PURPLE_BACKGROUND = "\u001B[45m";
    public static final String ANSI_CYAN_BACKGROUND = "\u001B[46m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";

    static final String CORRECT_TICK = ANSI_GREEN + "[\u2713]" + ANSI_RESET;
    static final String WRONG_CROSS = ANSI_RED + "[\u2718]" + ANSI_RESET;


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
            configFilePath = System.getProperty("user.dir") + File.separator +  Command.DEFAULT_CONFIG_FILENAME;
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
        }
    }




    public static void main(String[] args) {
        CommandLine.run(new Command(), System.out, args);
    }
}