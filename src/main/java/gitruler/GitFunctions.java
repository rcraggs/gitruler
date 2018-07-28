package gitruler;

import gitruler.exceptions.BranchNotFoundException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.util.Optional;

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
    Ref getBranchRef(String branchName) throws BranchNotFoundException {

        Git git = new Git(repo);
        Optional<Ref> branchOpt = null;
        Ref branch;

        try {
            branchOpt = git.branchList().call().stream().filter(b -> b.getName().contains(branchName)).findFirst();

            if (branchOpt.isPresent()){
                branch = branchOpt.get();
            }else{
                throw new BranchNotFoundException("Branch " + branchName + " was not found");
            }
        } catch (GitAPIException e) {
            throw new BranchNotFoundException("Branch " + branchName + " was not found");
        }

        return branch;
    }
}
