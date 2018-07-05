package gitruler;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;

class GitInteractor {

    private Repository repo;

    GitInteractor(String path) throws IOException {

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();

        // if the path doesn't already have .git on it, add it
        if (!path.endsWith(".git")){
            path = path + File.separator + ".git";
        }

        repo = repositoryBuilder.setGitDir(new File(path))
                .setMustExist(true)
                .build();
    }

    RuleResult checkRule(Rule r) {

        RuleResult result = new RuleResult();
        result.setPassed(false);
        result.setMessage("This is a test message.");

        return result;
    }
}
