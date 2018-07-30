package gitruler;

import gitruler.exceptions.BranchNotFoundException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
    private static Repository repo;

    @BeforeAll
    static void setup() {

        // Find the path to the test repo from the properties file
        Properties props = new Properties();
        InputStream is = ClassLoader.getSystemResourceAsStream("unittest.properties");
        try {
            props.load(is);
            String repoPath = props.getProperty("repo-path");

            FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();
            repo = repositoryBuilder.setGitDir(new File(repoPath))
                    .setMustExist(true)
                    .build();

            gf = new GitFunctions(repo);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getBranchRefExistsTest() {

        try {
            RevCommit branchCommit = gf.getBranchCommit("branch-1");
            assertTrue(branchCommit != ObjectId.zeroId() && branchCommit != null);
        } catch (BranchNotFoundException e) {
            fail("Failed to find branch");
        }
    }


    @Test
    void getBranchRefMasterTest() {

        try {
            RevCommit branchCommit = gf.getBranchCommit("master");
            assertTrue(branchCommit != ObjectId.zeroId() && branchCommit != null);
        } catch (BranchNotFoundException e) {
            fail("Failed to find branch");
        }
    }


    @Test
    void getBranchRefNotExistsTest() {

        try {
            gf.getBranchCommit("unknown");
            fail("Branch was incorrectly found");
        } catch (BranchNotFoundException e) {
            assert(e.getMessage().contains("unknown"));
        }
    }

    @Test
    void getTreeIdFromPathTest() {

        RevWalk walk = new RevWalk(repo);
        try {
            RevCommit commit = walk.parseCommit(ObjectId.fromString("069eeb05253827a6d7d281f3e51665781e5c3f37"));
            ObjectId treeId = gf.getTreeIdFromPath("file1.txt", commit);
            assertTrue(treeId != ObjectId.zeroId() && treeId != null);
        } catch (IOException e) {
            fail("Failed to get commit");
        }
    }

    @Test
    void getTreeIdFromPathIncorrectPathTest() {

        RevWalk walk = new RevWalk(repo);
        try {
            RevCommit commit = walk.parseCommit(ObjectId.fromString("069eeb05253827a6d7d281f3e51665781e5c3f37"));
            ObjectId treeId = gf.getTreeIdFromPath("file2.txt", commit);
            assertFalse(treeId != ObjectId.zeroId() && treeId != null);
        } catch (IOException e) {
            fail("Commit was found when it should not have been");
        }
    }

    @Test
    void getTreeIdFromPathIncorrectCommitIdTest() {

        RevWalk walk = new RevWalk(repo);
        try {
            RevCommit commit = walk.parseCommit(ObjectId.fromString("0c895c4ef98ee8184ea7bc619e56c5ce31948628"));
            ObjectId treeId = gf.getTreeIdFromPath("file2.txt", commit);
            assertFalse(treeId != ObjectId.zeroId() && treeId != null);
        } catch (IOException e) {
            fail("Commit was found when it should not have been");
        }
    }

    @Test
    void getFileContentsValidTreeTest() throws IOException {

        String contents = gf.getFileContents(ObjectId.fromString("67ba9998ffaa6edad2d84287800d9efd1941409c"));
        assertTrue(contents.contains("updated"));
    }

    @Test
    void getCommitWithMessageContainingTest() {

        assertNotNull(gf.getCommitWithMessageContaining("FILE1", true));
        assertNull(gf.getCommitWithMessageContaining("FILE1", false));
        assertNotNull(gf.getCommitWithMessageContaining("add file in branch 1", false), "Check commit in branch");
    }

    @Test
    void isCommitOrphanTest() throws IOException {

        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(ObjectId.fromString("0c895c4ef98ee8184ea7bc619e56c5ce31948628"));
        assertTrue(gf.isCommitOrphan(commit));
        commit = walk.parseCommit(ObjectId.fromString("069eeb05253827a6d7d281f3e51665781e5c3f37"));
        assertFalse(gf.isCommitOrphan(commit));

    }

    @Test
    void wasPathUpdatedInCommitSimpleTrueTest() throws IOException {

        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(ObjectId.fromString("892d8e86ce4d04618e46309914fc6d8666dbed49"));
        assertTrue(gf.isPathUpdatedInCommit("file1.txt", commit));
    }

    @Test
    void wasPathUpdatedInFirstCommitTest() throws IOException {

        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(ObjectId.fromString("0c895c4ef98ee8184ea7bc619e56c5ce31948628"));
        assertTrue(gf.isPathUpdatedInCommit("README.MD", commit));
    }

    @Test
    void wasPathUpdatedInFirstCommitFailTest() throws IOException {

        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(ObjectId.fromString("0c895c4ef98ee8184ea7bc619e56c5ce31948628"));
        assertFalse(gf.isPathUpdatedInCommit("file1.txt", commit));
    }

    @Test
    void wasPathUpdatedInCommitThereButNotUpdatedTest() throws IOException {

        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(ObjectId.fromString("069eeb05253827a6d7d281f3e51665781e5c3f37"));
        assertFalse(gf.isPathUpdatedInCommit("README.MD", commit));
    }

    @Test
    void pathExistsInCommitTest() throws IOException {

        RevCommit commit =  gf.getCommitFromRefString(Constants.HEAD);
        assertTrue(gf.pathExistsInCommit(commit, "file1.txt"));

        commit =  gf.getCommitFromRefString("0c895c4ef98ee8184ea7bc619e56c5ce31948628");
        assertFalse((gf.pathExistsInCommit(commit, "file1.txt")));
    }

    @Test
    void pathExistsInCommitWithIdTest() throws IOException {

        RevCommit commit =  gf.getCommitFromRefString(Constants.HEAD);
        assertTrue(gf.pathExistsInCommit(commit, "file1.txt", "67ba9998ffaa6edad2d84287800d9efd1941409c"));

        assertFalse((gf.pathExistsInCommit(commit, "file1.txt", "incorrectID")));
    }

    @Test
    void getContentsOfFileInCommitTest() throws IOException {

        RevCommit commit =  gf.getCommitFromRefString("892d8e86ce4d04618e46309914fc6d8666dbed49");
        assertEquals(gf.getContentsOfFileInCommit(commit, "file2.txt"),"file2"+System.lineSeparator());

        commit =  gf.getCommitFromRefString("069eeb05253827a6d7d281f3e51665781e5c3f37");
        String contents = gf.getContentsOfFileInCommit(commit, "file1.txt");
        assertTrue(!contents.contains("updated"), "Check folder commits are correctly checked");
    }

    @Test
    void getCommitMessageForFileContainsString() throws IOException, GitAPIException {
        assertTrue(gf.anyCommitMessagesForFileContainsString("file1.txt", "MULTIPLE", true));
        assertFalse(gf.anyCommitMessagesForFileContainsString("file1.txt", "MULTIPLE", false));
        assertTrue(gf.anyCommitMessagesForFileContainsString("file1.txt", "add file1", true));
        assertFalse(gf.anyCommitMessagesForFileContainsString("file1.txt", "ADD README", true));
    }
}