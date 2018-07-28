package gitruler;

import gitruler.exceptions.BranchNotFoundException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
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
                        return commit;
                    }
                }
                else{
                    if (commit.getFullMessage().contains(contents)){
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
    boolean isPathUpdatedByCommit(String path, RevCommit commit) throws IOException, GitAPIException {

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
     * Check whether a path exists in the repo at the time of a commit
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
    boolean wasPathUpdatedInCommit(String path, RevCommit commit) {

        try {
            if (isCommitOrphan(commit)) {
                // This was the first commit, we can just check if the path exists
                return pathExistsInCommit(commit, path, "");
            } else {
                return isPathUpdatedByCommit(path, commit);

            }
        }catch(IOException | GitAPIException e) {
            return false;
        }
    }
}
