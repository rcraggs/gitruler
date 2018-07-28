package gitruler;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

class GitInteractor {

    private static final String THE_FILE_WAS_NOT_CHANGED_IN_THE_COMMIT = "The file was not changed in the commit";
    private static final String DUMMY_CONTENT = "DUMMY CONTENT";
    private static final String GIT_DIR_NAME = ".git";
    private Repository repo;
    private GitFunctions gitFunctions;
    private String repositoryPath;

    GitInteractor(String path) throws IOException {

        FileRepositoryBuilder repositoryBuilder = new FileRepositoryBuilder();

        // if the path doesn't already have .git on it, add it
        if (!path.endsWith(GIT_DIR_NAME)){
            repositoryPath = path;
            path = path + File.separator + GIT_DIR_NAME;
        }else{
            repositoryPath = path.substring(0,path.indexOf(GIT_DIR_NAME));
        }

        repo = repositoryBuilder.setGitDir(new File(path))
                .setMustExist(true)
                .build();

        gitFunctions = new GitFunctions(repo);
    }

    RuleResult checkRule(Rule r) {

        switch (r.getRuleName()){
            case "head-exists":
                return checkHeadExists();
            case "file-tracked-in-head":
                return checkFileExistsInHead(r);
            case "file-untracked-in-head":
                return checkFileNotExistsInHead(r);
            case "file-has-hash-in-head":
                return checkBlobInLocationInHead(r);
            case "file-contains-in-head":
                return checkFileContainsContents(r);
            case "last-commit-message-for-file-contains":
                return checkCommitForPathContains(r, true);
            case "any-commit-message-for-file-contains":
                return checkCommitForPathContains(r, false);
            case "any-commit-message-contains":
                return checkCommitForPathContains(r, false);
            case "commit-with-message-updated-file":
                return checkCommitWithContentsUpdatedPath(r);
            case "commit-with-message-doesnt-update-file":
                return checkCommitWithContentsDoesntUpdatePath(r);
            case "ignored":
                return gitWouldIgnore(r);
            case "file-tracked-in-branch":
                return checkFileTrackedInBranch(r);
            case "file-contains-in-branch":
                return checkFileContainsInBranch(r);
            default:
                RuleResult result = new RuleResult();
                result.setPassed(false);
                result.setMessage("Could not run this rule.");
                return result;
        }
    }

    /**
     * Check whether there is a file with given contents at a path
     * @param r the rule containing parameters
     * @return a RuleResult
     */
    private RuleResult checkFileContainsInBranch(Rule r) {

        String path = r.getStringParameter("path");
        String branch = r.getStringParameter("branch");
        String contents = r.getStringParameter("contents");

        RuleResult result = new RuleResult();
        result.setPassed(false);

        try {
            // Get the tree and check its contents
            RevCommit branchCommit = gitFunctions.getBranchCommit(branch);
            ObjectId treeId = gitFunctions.getTreeIdFromPath(path, branchCommit);

            if (gitFunctions.isValidObjectId(treeId)) {
                String fileContents = gitFunctions.getFileContents(treeId);
                result.setPassed(fileContents.toLowerCase().contains(contents.toLowerCase()));
            }

        } catch (Exception e) {
            if (e.getMessage().startsWith("Could not find branch")) {
                result.setMessage(e.getMessage());
            }else{
                result = createResultFromException(e);
            }
        }

        return result;
    }

    private RuleResult checkFileTrackedInBranch(Rule r) {

        String branchName = r.getStringParameter("branch");
        String path = r.getStringParameter("path");
        RuleResult result = new RuleResult(false);

        try {
            RevCommit branchCommit = gitFunctions.getBranchCommit(branchName);
            ObjectId pathId = gitFunctions.getTreeIdFromPath(path, branchCommit);

            if (gitFunctions.isValidObjectId(pathId)){
                result.setPassed(true);
            }
        } catch (Exception e) {
            result = createResultFromException(e);
        }

        return result;
    }

    private RuleResult gitWouldIgnore(Rule r) {

        RuleResult result = new RuleResult();
        Path backupPath = null;

        Path path = Paths.get(repositoryPath + File.separator + r.getStringParameter("path"));
        String pathStringValue = r.getStringParameter("path");

        // If the path exists, back it up and delete it
        if (Files.exists(path)){

            try {
                File tempFile = File.createTempFile("ignore-test-backup", "bak");
                tempFile.deleteOnExit();
                backupPath = tempFile.toPath();
                Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(path);

            } catch (IOException e) {
                result.setPassed(false);
                result.setMessage("Tried to test ignoring a file that could not be backed up for the test");
                return result;
            }
        }

        // Create a file
        try {
            Files.write(path, DUMMY_CONTENT.getBytes(), StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            result.setPassed(false);
            result.setMessage("Tried to test ignoring a file that could not be created");
            return result;
        }

        // Check if this appears in the diff
        Git git = new Git(repo);
        try {
            Status status = git.status().call();

            // The path should not be in added or modified lists
            result.setPassed(!status.getAdded().contains(pathStringValue)
                    && !status.getModified().contains(pathStringValue)
                    && !status.getChanged().contains(pathStringValue)
                    && !status.getUntracked().contains(pathStringValue));

        } catch (GitAPIException e) {
            result.setPassed(false);
            result.setMessage("Failed to check if a file is ignored because I couldn't do a status call");
            return result;
        }

        // Delete the file
        if (backupPath == null){
            try {
                Files.delete(path);
            } catch (IOException e) {
                result.setPassed(false);
                result.setMessage("Failed to check if a file is ignored because I couldn't remove the temporary file");
                return result;
            }
        }else{
            try {
                Files.copy(backupPath, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                result.setPassed(false);
                result.setMessage("Failed to check if a file is ignored because I couldn't restore the original file");
                return result;
            }
        }

        return result;
    }

    private RuleResult checkCommitWithContentsDoesntUpdatePath(Rule r) {

        RuleResult result = new RuleResult();

        String contents = r.getStringParameter("contents");
        String path = r.getStringParameter("path");
        boolean caseInsensitive = r.getBooleanParameter("ignore-case");

        RevCommit commit = gitFunctions.getCommitWithMessageContaining(contents, caseInsensitive);

        if (commit == null){
            result.setMessage("No commit with that message was found.");
        }else{
            if (gitFunctions.wasPathUpdatedInCommit(path, commit)) {
                result.setMessage("That file was not updated in that commit");
            }else{
                result.setPassed(true);
            }
        }

        return result;
    }


//
//    private String getReasonIfCheckForFileInCommitFails(Rule r) {
//
//        String contents = r.getStringParameter("contents");
//        String path = r.getStringParameter("path");
//        boolean caseInsensitive = r.getBooleanParameter("ignore-case");
//
//        try {
//            RevCommit commit = gitFunctions.getCommitWithMessageContaining(contents, caseInsensitive);
//
//            if (commit != null) {
//
//                String x = gitFunctions.isPathUpdatedByCommit(path, commit);
//                if (x != null) return x;
//
//            }else{
//                return "There was no commit with that message";
//            }
//
//        } catch (Exception e) {
//            return "An error happened with the message:" + e.getMessage();
//        }
//
//        return "";
//    }

    private RuleResult checkCommitWithContentsUpdatedPath(Rule r) {

        RuleResult result = new RuleResult();

        String contents = r.getStringParameter("contents");
        String path = r.getStringParameter("path");
        boolean caseInsensitive = r.getBooleanParameter("ignore-case");

        RevCommit commit = gitFunctions.getCommitWithMessageContaining(contents, caseInsensitive);

        if (commit == null){
            result.setMessage("No commit with that message was found.");
        }else{
            if (gitFunctions.wasPathUpdatedInCommit(path, commit)) {
                result.setPassed(true);
            }else{
                result.setMessage("That file was not updated in that commit");
            }
        }

        return result;
    }

    private RuleResult checkCommitForPathContains(Rule r, boolean mustBeLastCommit) {

        String path = "";

        if (r.details.containsKey("path")) {
            path = r.getStringParameter("path");
        }
        String contents = r.getStringParameter("contents");
        boolean caseInsensitive = r.getBooleanParameter("ignore-case");
        RuleResult result = new RuleResult();
        result.setPassed(false);
        try {

            List<RevCommit> commits = new ArrayList<>();
            double latestTimeCommit = 0;
            Git git = new Git(repo);
            Iterable<RevCommit> log;
            if (path.isEmpty()) {
                log = git.log().all().call();
            }else{
                log = git.log().addPath(path).call();
            }

            // Collect the commits to check
            for (RevCommit commit: log) {

                if (mustBeLastCommit) {
                    if (commit.getCommitTime() > latestTimeCommit) {
                        // Only keep the latest commit
                        latestTimeCommit = commit.getCommitTime();
                        commits.clear();
                        commits.add(commit);
                    }
                } else {
                    commits.add(commit);
                }
            }

            // Check the commits for a matching message
            for (RevCommit commit : commits) {
                if (caseInsensitive) {
                    if (commit.getFullMessage().toLowerCase().contains(contents.toLowerCase())){
                        result.setPassed(true);
                        return result;
                    }
                }
                else{
                    if (commit.getFullMessage().contains(contents)){
                        result.setPassed(true);
                        return result;
                    }
                }
            }
        }catch(GitAPIException | IOException e){
            result = createResultFromException(e);
        }

        return result;
    }

    private RuleResult checkFileContainsContents(Rule r) {

        String path = r.getStringParameter("path");
        String contents = r.getStringParameter("contents");
        boolean caseInsensitive = r.getBooleanParameter("ignore-case");
        RuleResult result = new RuleResult();

        try {
            String fileContents = getContentsOfFileInCommit(Constants.HEAD, path);

            boolean foundMatch;
            if (fileContents == null){
                foundMatch = false;
            }else{

                if (caseInsensitive) {
                    foundMatch = fileContents.toLowerCase().contains(contents.toLowerCase());
                }
                else{
                    foundMatch = fileContents.contains(contents);
                }
            }

            result.setPassed(foundMatch);

        } catch (Exception e) {
            result = createResultFromException(e);
        }
        return result;
    }

    private RuleResult checkBlobInLocationInHead(Rule r) {

        String path = r.getStringParameter("path");
        String id = r.getStringParameter("hash");

        RuleResult result = new RuleResult();

        try {
            RevCommit head = gitFunctions.getCommitFromRefString(Constants.HEAD);
            boolean pathFound = gitFunctions.pathExistsInCommit(head, path, id);
            result.setPassed(pathFound);
        } catch (Exception e) {
            result = createResultFromException(e);
        }
        return result;
    }

    private RuleResult checkFileNotExistsInHead(Rule r) {

        // Get the necessary parameters
        String path = r.getStringParameter("path");
        RuleResult result = new RuleResult();
        try {
            RevCommit head = gitFunctions.getCommitFromRefString(Constants.HEAD);
            boolean pathFound = gitFunctions.pathExistsInCommit(head, path, "");
            result.setPassed(!pathFound);
        } catch (Exception e) {
            result.setPassed(false);
            result.setMessage("An error occurred when running this rule.");
        }

        return result;
    }

    private RuleResult checkHeadExists() {

        RuleResult result = new RuleResult();

        try {
            ObjectId head = repo.resolve(Constants.HEAD);
            result.setPassed(head != null);
        } catch (Exception e) {
            result.setPassed(false);
            result.setMessage("An error occurred when running this rule.");
        }

        return result;
    }

    private RuleResult checkFileExistsInHead(Rule r) {

        // Get the necessary parameters
        String path = r.getStringParameter("path");
        RuleResult result = new RuleResult(false);
        try {
            RevCommit head = gitFunctions.getCommitFromRefString(Constants.HEAD);
            boolean pathFound = gitFunctions.pathExistsInCommit(head, path, "");
            result.setPassed(pathFound);
        } catch (Exception e) {
            result.setMessage("An error occurred when running this rule.");
        }

        return result;
    }

    private String getContentsOfFileInCommit(String commitRefString, String path) throws IOException, NullPointerException {

        ObjectId commitId = repo.resolve(commitRefString);
        RevWalk revWalk = new RevWalk(repo);
        RevCommit commit = revWalk.parseCommit(commitId);
        RevTree tree = commit.getTree();

        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(tree);
        treeWalk.setRecursive(true);
        treeWalk.setFilter(PathFilter.create(path));
        while (treeWalk.next()) {
            if (treeWalk.getPathString() != null){
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repo.open(objectId);
                InputStream in = loader.openStream();
                Scanner s = new Scanner(in).useDelimiter("\\A");
                return s.hasNext() ? s.next() : "";
            }
        }
        return null;
    }

    private RuleResult createResultFromException(Exception e) {
        RuleResult result = new RuleResult();
        result.setPassed(false);
        result.setMessage("An error occurred when running this rule.");
        result.setExceptionMessage(e.getMessage());
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        result.setExceptionTrace(sw.toString());
        result.exceptionOccurred = true;
        return result;
    }
}
