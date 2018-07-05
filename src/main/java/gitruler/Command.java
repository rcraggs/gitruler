package gitruler;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@CommandLine.Command(name = "testrepo", mixinStandardHelpOptions = true, version = "Gitruler 1.0")
public class Command implements Runnable {

    private static final String DEFAULT_CONFIG_FILENAME = "gitrules.json";

    @Option(names = { "-c", "--config" }, paramLabel = "Config File Path", description = "The file containing rules")
    private String configFilePath;

    @Option(names = { "-r", "--repo" }, paramLabel = "Repository Path", description = "The repository to test")
    private String repositoryPath;


    private GitRulerConfig config;

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
        }

        // Check the there is a repository at the given path



    }

    public static void main(String[] args) {
        CommandLine.run(new Command(), System.out, args);
    }
}