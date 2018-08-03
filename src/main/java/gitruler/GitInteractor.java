package gitruler;

import gitruler.exceptions.BranchNotFoundException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.Optional;

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
            case "branch-exists":
                return checkBranchExists(r);
            case "commit-with-message-was-merged-into-branch":
                return checkBranchReceivedCommitWithMessage(r);
            case "commit-with-message-was-made-on-branch":
                return getCommitWithMessageWasMadeOnBranch(r);
            default:
                RuleResult result = new RuleResult();
                result.setPassed(false);
                result.setMessage("Could not run this rule.");
                return result;
        }
    }

    /**
     * Check whether a commit was made on a specific branch
     * @param r The rule details
     * @return The RuleResult
     */
    private RuleResult getCommitWithMessageWasMadeOnBranch(Rule r) {

        RuleResult result = new RuleResult();
        try {
            result.setPassed(gitFunctions.wasCommitWithMessageMadeOnBranch(r.getBranch(), r.getContents(), r.getIgnoreCase()));
        } catch (IOException e) {
            return createResultFromException(e);
        } catch (BranchNotFoundException e) {
            result.setPassed(false);
            result.setMessage("The branch with that name doesn't exist");
        }

        return result;
    }

    /**
     * Is there a commit on the given branch that has a parent commit that contains a given message?
     * This lets us check whether a merge occurred that merged a commit into another branch.
     * @param r Rule details.
     * @return The RuleResult
     */
    private RuleResult checkBranchReceivedCommitWithMessage(Rule r) {

        RuleResult result = new RuleResult();
        try {
            result.setPassed(gitFunctions.isChildOfCommitOnBranch(r.getBranch(), r.getContents(), r.getIgnoreCase()));
        } catch (IOException | GitAPIException e) {
            return createResultFromException(e);
        } catch (BranchNotFoundException e) {
            result.setPassed(false);
            result.setMessage("The branch with that name doesn't exist");
        }

        return result;
    }

    /**
     * Check whether a branch exists.
     * @param r the rule containing parameter
     * @return a RuleResult
     */
    private RuleResult checkBranchExists(Rule r) {

        RuleResult result = new RuleResult(false);
        try {
            result.setPassed(gitFunctions.doesBranchExist(r.getBranch()));
        } catch (GitAPIException e) {
            result = createResultFromException(e);
        }

        return result;
    }

    /**
     * Check whether there is a file with given contents at a path
     * @param r the rule containing parameters
     * @return a RuleResult
     */
    private RuleResult checkFileContainsInBranch(Rule r) {

        RuleResult result = new RuleResult();
        result.setPassed(false);

        try {
            // Get the tree and check its contents
            RevCommit branchCommit = gitFunctions.getBranchCommit(r.getBranch());
            ObjectId treeId = gitFunctions.getTreeIdFromPath(r.getPath(), branchCommit);

            if (gitFunctions.isValidObjectId(treeId)) {
                String fileContents = gitFunctions.getFileContents(treeId);
                result.setPassed(fileContents.toLowerCase().contains(r.getContents().toLowerCase()));
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

        RuleResult result = new RuleResult(false);

        try {
            RevCommit branchCommit = gitFunctions.getBranchCommit(r.getBranch());
            ObjectId pathId = gitFunctions.getTreeIdFromPath(r.getPath(), branchCommit);

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

        Path path = Paths.get(repositoryPath + File.separator + r.getPath());
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
        RevCommit commit = gitFunctions.getCommitWithMessageContaining(r.getContents(), r.getIgnoreCase());

        if (commit == null){
            result.setMessage("No commit with that message was found.");
        }else{
            if (gitFunctions.isPathUpdatedInCommit(r.getPath(), commit)) {
                result.setMessage("That file was not updated in that commit");
            }else{
                result.setPassed(true);
            }
        }

        return result;
    }


    private RuleResult checkCommitWithContentsUpdatedPath(Rule r) {

        RuleResult result = new RuleResult();
        RevCommit commit = gitFunctions.getCommitWithMessageContaining(r.getContents(), r.getIgnoreCase());

        if (commit == null){
            result.setMessage("No commit with that message was found.");
        }else{
            if (gitFunctions.isPathUpdatedInCommit(r.getPath(), commit)) {
                result.setPassed(true);
            }else{
                result.setMessage("That file was not updated in that commit");
            }
        }

        return result;
    }

    private RuleResult checkCommitForPathContains(Rule r, boolean mustBeLastCommit) {

        String path;
        String contents = r.getStringParameter("contents");
        boolean caseInsensitive = r.getBooleanParameter("ignore-case");

        boolean resultsValue = false;
        try {
            if (r.details.containsKey("path")){
                path = r.getStringParameter("path");
                if (mustBeLastCommit){
                    resultsValue = gitFunctions.lastCommitMessageForFileContainsString(path, contents, caseInsensitive);
                }else{
                    resultsValue = gitFunctions.anyCommitMessagesForFileContainsString(path, contents, caseInsensitive);
                }
            }else{
                resultsValue = gitFunctions.anyCommitMessagesContainsString(contents, caseInsensitive);
            }
        } catch (IOException | GitAPIException e) {
            resultsValue = false;
        }

        return new RuleResult(resultsValue);
    }

    private RuleResult checkFileContainsContents(Rule r) {

        RuleResult result = new RuleResult();

        try {
            RevCommit commit = gitFunctions.getCommitFromRefString(Constants.HEAD);
            String fileContents = gitFunctions.getContentsOfFileInCommit(commit, r.getPath());

            boolean foundMatch;
            if (r.getIgnoreCase()) {
                foundMatch = fileContents != null && fileContents.toLowerCase().contains(r.getContents().toLowerCase());
            }
            else{
                foundMatch = fileContents != null && fileContents.contains(r.getContents());
            }

            result.setPassed(foundMatch);

        } catch (Exception e) {
            result = createResultFromException(e);
        }
        return result;
    }

    private RuleResult checkBlobInLocationInHead(Rule r) {

        String id = r.getStringParameter("hash");
        RuleResult result = new RuleResult();

        try {
            RevCommit head = gitFunctions.getCommitFromRefString(Constants.HEAD);
            boolean pathFound = gitFunctions.pathExistsInCommit(head, r.getPath(), id);
            result.setPassed(pathFound);
        } catch (Exception e) {
            result = createResultFromException(e);
        }
        return result;
    }

    private RuleResult checkFileNotExistsInHead(Rule r) {

        // Get the necessary parameters
        RuleResult result = new RuleResult();
        try {
            RevCommit head = gitFunctions.getCommitFromRefString(Constants.HEAD);
            boolean pathFound = gitFunctions.pathExistsInCommit(head, r.getPath());
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
        RuleResult result = new RuleResult(false);
        try {
            RevCommit head = gitFunctions.getCommitFromRefString(Constants.HEAD);
            boolean pathFound = gitFunctions.pathExistsInCommit(head, r.getPath());
            result.setPassed(pathFound);
        } catch (Exception e) {
            result.setMessage("An error occurred when running this rule.");
        }

        return result;
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
