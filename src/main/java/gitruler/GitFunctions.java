package gitruler;

import gitruler.exceptions.BranchNotFoundException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

class GitFunctions {

    private Repository repo;

    /**
     * Create the functions for a given repo.
     * @param repo the repo to act on.
     */
    GitFunctions(Repository repo) {
        this.repo = repo;
    }

    /**
     * Get a branch ref from a branch name
     * @param branchName The name to search for.
     * @return A ref to the found branch
     * @throws BranchNotFoundException The branch was not found.
     */
    RevCommit getBranchCommit(String branchName) throws BranchNotFoundException {

        Git git = new Git(repo);
        Optional<Ref> branchOpt;
        RevCommit branchCommit;

        try {
            branchOpt = git.branchList().call().stream().filter(b -> b.getName().contains(branchName)).findFirst();

            if (branchOpt.isPresent()){
                Ref branch = branchOpt.get();
                RevWalk walk = new RevWalk(repo);
                branchCommit = walk.parseCommit(branch.getObjectId());
            }else{
                throw new BranchNotFoundException("Branch " + branchName + " was not found");
            }
        } catch (Exception e) {
            throw new BranchNotFoundException("A branch called " + branchName + " with a valid commit was not found");
        }

        return branchCommit;
    }

    /**
     * Get the tree objectId for the tree that includes the specific path in the last commit for a branch
     * @param path the file path
     * @param commit the commit to start from
     * @return The objectID for the tree
     */
    ObjectId getTreeIdFromPath(String path, RevCommit commit) {

        try {
            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));

            while (treeWalk.next()) {
                if (treeWalk.getPathString() != null){
                    return treeWalk.getObjectId(0);
                }
            }
        } catch (IOException ignored) {}

        return null;
    }


    /**
     * Get the contents of a file from a tree
     * @param treeId The Id to look at
     * @return The file's contents
     * @throws IOException If the file could not be found
     */
     String getFileContents(ObjectId treeId) throws IOException {
        ObjectLoader loader = repo.open(treeId);
        InputStream in = loader.openStream();
        Scanner s = new Scanner(in).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }


    /**
     * Get a commit that was made with a commit message containing certain text
     * @param contents The message must contain this content
     * @param caseInsensitive whether to ignore case when matching text
     * @return The commit if found or null
     */
    RevCommit getCommitWithMessageContaining(String contents, boolean caseInsensitive) {

        Git git = new Git(repo);
        Iterable<RevCommit> log = null;
        try {
            log = git.log().all().call();

            for (RevCommit commit: log) {

                if (caseInsensitive) {
                    if (commit.getFullMessage().toLowerCase().contains(contents.toLowerCase())){

                        RevWalk revWalk = new RevWalk(repo);
                        commit = revWalk.parseCommit(commit.getId());
                        return commit;
                    }
                }
                else{
                    if (commit.getFullMessage().contains(contents)){
                        RevWalk revWalk = new RevWalk(repo);
                        commit = revWalk.parseCommit(commit.getId());
                        return commit;
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Check if an objectId is valid
     * @param id the id
     * @return true if it refers to an object
     */
    boolean isValidObjectId(ObjectId id) {
        return id != ObjectId.zeroId() && id != null;
    }

    /**
     * Check if a commit has parents
     * @param commit the commit to check
     * @return true if it has no parents
     */
    boolean isCommitOrphan(RevCommit commit) {
        RevCommit[] parents = commit.getParents();
        return parents.length == 0;
    }

    /**
     * Is a file different between a commit and its parent?
     * @param path the file to look at
     * @param commit the later commit
     * @return True if the file changed
     * @throws IOException the test failed
     * @throws GitAPIException the test failed
     */
    private boolean doesDiffWithParentContainCommit(String path, RevCommit commit) throws IOException, GitAPIException {

        Git git = new Git(repo);
        CanonicalTreeParser commitTree = getCommitTreeParser(commit.getTree().getId());

        // Get the parent of this commit to compare it with
        RevCommit[] parents = commit.getParents();
        RevCommit parent = parents[0];
        RevWalk revWalk = new RevWalk(repo);
        parent = revWalk.parseCommit( parent.getId() );
        CanonicalTreeParser parentTree = getCommitTreeParser(parent.getTree().getId());

        List<DiffEntry> changes = git.diff()
                .setOldTree(parentTree)
                .setNewTree(commitTree)
                .setPathFilter(PathFilter.create(path))
                .call();

        return changes.size() > 0;
    }

    /**
     * Get the tree from a commit
     * @param treeId the id of a tree to get parser for
     * @return tree parser
     * @throws IOException If it failed to get the tree
     */
    private CanonicalTreeParser getCommitTreeParser(ObjectId treeId) throws IOException {

        Git git = new Git(repo);

        CanonicalTreeParser commitTree = new CanonicalTreeParser();
        try( ObjectReader reader = git.getRepository().newObjectReader() ) {
            commitTree.reset(reader, treeId);
        }
        return commitTree;
    }

    /**
     * Check whether a path exists in the repo at the time of a commit.
     * @param commit the commit to check against
     * @param path the path of the file to check
     * @return true if that file existed at that commit
     * @throws IOException Git error
     */
    boolean pathExistsInCommit(RevCommit commit, String path) throws IOException {
        return pathExistsInCommit(commit, path, "");
    }

    /**
     * Check whether a path exists in the repo at the time of a commit with a certain hash id
     * @param commit the commit to check against
     * @param path the path of the file to check
     * @param id the sha1 hash of the file contents or empty if we don't care
     * @return true if that file existed at that commit
     * @throws IOException Git error
     */
    boolean pathExistsInCommit(RevCommit commit, String path, String id) throws IOException {

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

    /**
     * Get the commit from a ref string
     * @param ref the id of the commit as a string
     * @return the commit object
     * @throws IOException git exception
     */
    RevCommit getCommitFromRefString(String ref) throws IOException {
        ObjectId commitId = repo.resolve(ref);
        RevWalk revWalk = new RevWalk(repo);
        return revWalk.parseCommit(commitId);
    }

    /**
     * Checks whether a file was introduced or changes in a commit
     * @param path The file
     * @param commit The commit to check
     * @return True if it was changed.
     */
    boolean isPathUpdatedInCommit(String path, RevCommit commit) {

        try {
            if (isCommitOrphan(commit)) {
                // This was the first commit, we can just check if the path exists
                return pathExistsInCommit(commit, path, "");
            } else {
                return doesDiffWithParentContainCommit(path, commit);

            }
        }catch(IOException | GitAPIException e) {
            return false;
        }
    }

    /**
     * Get the contents of a file as it was at the point in time a commit occurred
     * @param commit The commit to check
     * @param path The file to get the contents of
     * @return The file contents
     * @throws IOException Git exceptions
     * @throws NullPointerException Git exceptions
     */
    String getContentsOfFileInCommit(RevCommit commit, String path) throws IOException, NullPointerException {
        ObjectId treeIfOfFileInCommit = getTreeIdFromPath(path, commit);
        return getFileContents(treeIfOfFileInCommit);
    }

    boolean anyCommitMessagesForFileContainsString(String path, String contents, boolean caseInsensitive) throws IOException, GitAPIException {

        Git git = new Git(repo);
        Iterable<RevCommit> log = git.log().addPath(path).call();
        return doesCommitListIncludeContentInAMessage(contents, caseInsensitive, log);
    }

    boolean anyCommitMessagesContainsString(String contents, boolean caseInsensitive) throws IOException, GitAPIException {

        Git git = new Git(repo);
        Iterable<RevCommit> log = git.log().all().call();
        return doesCommitListIncludeContentInAMessage(contents, caseInsensitive, log);
    }

    boolean lastCommitMessageForFileContainsString(String path, String contents, boolean caseInsensitive) throws IOException, GitAPIException {
        RevCommit commit = getLatestCommitForPath(path);

        if (caseInsensitive) {
            return commit.getFullMessage().toLowerCase().contains(contents.toLowerCase());
        }else{
            return commit.getFullMessage().contains(contents);
        }
    }

    private RevCommit getLatestCommitForPath(String path) throws GitAPIException {
        Git git = new Git(repo);
        Iterable<RevCommit> log = git.log().addPath(path).call();
        RevCommit latestCommit = null;

        double latestTimeCommit = 0;
        for (RevCommit commit: log) {
            if (commit.getCommitTime() > latestTimeCommit) {
                latestTimeCommit = commit.getCommitTime();
                latestCommit = commit;
            }
        }

        return latestCommit;
    }

    /**
     * Check whether there exists a branch with a given name.
     * @param branchName the name of the branch.
     * @return True if the branch exists.
     * @throws GitAPIException Git exception.
     */
    boolean doesBranchExist(String branchName) throws GitAPIException {
        Git git = new Git(repo);
        Optional<Ref> branchOpt = git.branchList().call().stream().filter(b -> b.getName().contains(branchName)).findFirst();
        return branchOpt.isPresent();
    }

    /**
     * Unility method check a list of commits for a commit message
     * @param contents the text to look into the commit message for
     * @param caseInsensitive whether to check case
     * @param log the list of commits
     * @return true if the messages contains text.
     */
    private Boolean doesCommitListIncludeContentInAMessage(String contents, boolean caseInsensitive, Iterable<RevCommit> log) {

        for (RevCommit commit : log) {
            if (caseInsensitive) {
                if (commit.getFullMessage().toLowerCase().contains(contents.toLowerCase())){
                    return true;
                }
            }
            else{
                if (commit.getFullMessage().contains(contents)){
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Does the commit with the given message have a child commit that is on a branch with the given name?
     * @param branch The name of the branch
     * @param commitMessageContents The contents used to find the parent commit
     * @param ignoreMessageCase Whether to ignore the case when finding the commit
     * @return True of the commit with that message as merged on committed into a commit on the given branch
     * @throws IOException Git exception
     * @throws GitAPIException Git exception
     */
    boolean isChildOfCommitOnBranch(String branch, String commitMessageContents, boolean ignoreMessageCase) throws IOException, GitAPIException, BranchNotFoundException {

        // get the commit with that message
        RevCommit commitWithMessage = getCommitWithMessageContaining(commitMessageContents, ignoreMessageCase);

        if (commitWithMessage == null){
            return false;
        }

        // Get a walker for the branch
        RevWalk revWalk = new RevWalk(repo);
        revWalk.markStart( revWalk.parseCommit(getBranchCommit(branch).getId()));

        // Look at each commit in the branch
        for( RevCommit commit : revWalk ) {

            // if the parent of this commit (in the branch) is our commit then we succeeded
            for (RevCommit parentCommit : commit.getParents()){
                if (parentCommit.getId().equals(commitWithMessage.getId())){
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check whether the commits underneath a branch reference include a commit with a given message
     * @param branch the branch name
     * @param commitMessage The text within the message
     * @param ignoreCase Whether to ignore the case when checking the commit message
     * @return True of the commit with that message was under the branch ref
     * @throws BranchNotFoundException Git exception
     * @throws IOException Git exception
     */
    boolean wasCommitWithMessageMadeOnBranch(String branch, String commitMessage, boolean ignoreCase) throws Exception {

        RevCommit commitWithMessage = getCommitWithMessageContaining(commitMessage, ignoreCase);

        if (commitWithMessage == null){
            throw new Exception("No commit with that message was found");
        }

        // Get a walker for the branch
        RevWalk revWalk = new RevWalk(repo);
        revWalk.markStart( revWalk.parseCommit(getBranchCommit(branch).getId()));

        // Look at each commit in the branch
        for( RevCommit commit : revWalk ) {
            if (commit.getId().equals(commitWithMessage.getId())){
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a tag with the given name exists in the repository
     * @param tag the name of the tag
     * @return true if the tag exists
     */
    boolean doesTagExist(String tag) throws IOException {
        return repo.findRef(tag) != null;
    }

    /**
     * Check whether a given commit is tagged with a tag with the given name
     * @param commit The commit to check
     * @param tag the name of the tag
     * @return True if the commit is correctly tagged.
     */
    boolean isCommitTagged(RevCommit commit, String tag) throws IOException {

        Ref tagRef = repo.findRef(tag);
        return tagRef != null && tagRef.getObjectId().equals(commit.toObjectId());
    }
}
