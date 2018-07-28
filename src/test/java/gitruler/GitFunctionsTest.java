package gitruler;

import gitruler.exceptions.BranchNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class GitFunctionsTest {

    private static GitFunctions gf;

    @BeforeAll
    static void setup() {

        // Find the path to the test repo from the properties file
        Properties props = new Properties();
        InputStream is = ClassLoader.getSystemResourceAsStream("unittest.properties");
        try {
            props.load(is);
            String repoPath = props.getProperty("repo-path");

            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            Repository repo = repositoryBuilder.setGitDir(new File(repoPath))
                    .setMustExist(true)
                    .build();

            gf = new GitFunctions(repo);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getBranchRefExists() {

        try {
            Ref branchRef = gf.getBranchRef("branch-1");
            assertTrue(branchRef != ObjectId.zeroId() && branchRef != null);
        } catch (BranchNotFoundException e) {
            fail("Failed to find branch");
        }
    }


    @Test
    void getBranchRefMaster() {

        try {
            Ref branchRef = gf.getBranchRef("master");
            assertTrue(branchRef != ObjectId.zeroId() && branchRef != null);
        } catch (BranchNotFoundException e) {
            fail("Failed to find branch");
        }
    }


    @Test
    void getBranchRefNotExists() {

        try {
            gf.getBranchRef("unknown");
            fail("Branch was incorrectly found");
        } catch (BranchNotFoundException e) {
            assert(e.getMessage().contains("unknown"));
        }
    }
}