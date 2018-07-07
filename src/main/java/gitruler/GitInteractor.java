package gitruler;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.*;
import java.util.Objects;
import java.util.Scanner;

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

        switch (r.getRuleName()){
            case "head-exists":
                return checkHeadExists();
            case "file-exists-in-head":
                return checkFileExistsInHead(r);
            case "file-not-exist-in-head":
                return checkFileNotExistsInHead(r);
            case "blob-exists-in-location-in-head":
                return checkBlobInLocationInHead(r);
            case "file-contains-in-head":
                return checkFileContainsContents(r);
            default:
                RuleResult result = new RuleResult();
                result.setPassed(false);
                result.setMessage("Could not run this rule.");
                return result;
        }
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
            boolean pathFound = pathExistsInCommit(Constants.HEAD, path, id);
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
            boolean pathFound = pathExistsInCommit(Constants.HEAD, path);
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
        RuleResult result = new RuleResult();
        try {
            boolean pathFound = pathExistsInCommit(Constants.HEAD, path);
            result.setPassed(pathFound);
        } catch (Exception e) {
            result.setPassed(false);
            result.setMessage("An error occurred when running this rule.");
        }

        return result;
    }

    private boolean pathExistsInCommit(String commitRefString, String path) throws IOException, NullPointerException {
        return pathExistsInCommit(commitRefString, path, "");
    }

    private boolean pathExistsInCommit(String commitRefString, String path, String id ) throws IOException, NullPointerException {

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
                // If an id is passed in check that it matches, otherwise return true anyway
                return (id == null || id.isEmpty() || Objects.equals(objectId.getName(), id));
            }
        }
        return false;
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
