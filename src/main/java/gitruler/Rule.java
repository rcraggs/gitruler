package gitruler;

import java.util.Map;

public class Rule {

    public static final String TEXT_RULE_NAME = "text";

    /***
     * Adding a new rule:
     * Add a test to the test repo
     * add the title of the rule in getTitle in Rule.
     * Add the checkrule in the GitInterator
     * Add the documentation
     */
    Map<String, Object> details;

    Rule(Map<String, Object> details){
        this.details = details;
    }

    String getTitle() {

        if (details.containsKey("alternative-title")){
            return getStringParameter("alternative-title");
        }

        switch (getRuleName()){
            case "file-contains-in-head":
                return "The file: " + (String) details.get("path") + " contains the text '" + (String) details.get("contents") + "'";
            case "file-tracked-in-head":
                return "The file is tracked: " + (String) details.get("path");
            case "head-exists":
                return "There is a valid repository";
            case "file-untracked-in-head":
                return "The file is not tracked: " + (String) details.get("path");
            case "file-has-hash-in-head":
                return "An existing file should now be at: " + (String) details.get("path");
            case "last-commit-message-for-file-contains":
                return "Latest commit message for " + (String) details.get("path") + " contains " + (String) details.get("contents");
            case "any-commit-message-for-file-contains":
                return "Any commit message for " + (String) details.get("path") + " contains " + (String) details.get("contents");
            case "any-commit-message-contains":
                return "Any commit message contains '" + (String) details.get("contents") + "'";
            case "commit-with-message-updated-file":
                return "A commit with a message containing '" + (String) details.get("contents") + "' included a change to '" + (String) details.get("path") + "'";
            case "commit-with-message-doesnt-update-file":
                return "A commit with a message containing '" + (String) details.get("contents") + "' does not include a change to '" + (String) details.get("path") + "'";
            case TEXT_RULE_NAME:
                return createTextRuleOutput();
            case "ignored":
                return "Git ignores '" + getPath() +"'";
            case "file-tracked-in-branch":
                return "The file " + getPath() + " is tracked in the branch '" + getBranch() +"'";
            case "branch-exists":
                return "A branch called '" + this.getBranch() + "' exists";
            case "file-contains-in-branch":
                return "The file " + getPath() + " contains the text '" + getContents() + "' in the branch '" + getBranch() + "'";
            case "commit-with-message-was-merged-into-branch":
                return "The branch '" + getBranch() + "' received a commit with the message containing '" + getContents() + "'";
            case "commit-with-message-was-made-on-branch":
                return "The commit whose message contained '" + getContents() + "' was made on the branch '" + getBranch() + "'";
            default:
                return "Unknown rule";
        }
    }

    private String createTextRuleOutput() {

        String heading = (String) details.getOrDefault("heading", "");
        String separator = (String) details.getOrDefault("separator", "-");
        int width = (int) details.getOrDefault("width", 100);
        boolean doubleSpace = (boolean) details.getOrDefault("double-space", false);
        int counter = heading.length();

        StringBuilder result = new StringBuilder();

        if (doubleSpace){
            result.append(System.lineSeparator());
        }

        result.append(heading).append(" ");

        while (result.length() <= width) {
            result.append(separator);
        }

        if (doubleSpace){
            result.append(System.lineSeparator());
        }

        return result.toString();
    }

    String getStringParameter(String key){
        // todo: Check that the parameter exists and is the right type
        return (String) details.get(key);
    }

    String getRuleName(){
        return (String) details.get("rule");
    }

    boolean stopOnFail(){
        return details.containsKey("stop-on-fail") && ((Boolean)details.get("stop-on-fail"));
    }

    boolean hasPreText() {
        return details.containsKey("pre-text");
    }

    boolean hasPostText() {
        return details.containsKey("post-text");
    }

    boolean hasFailureMessage() {
        return details.containsKey("failure-message");
    }

    String getPreText() {
        return getStringParameter("pre-text");
    }

    String getPostText() {
        return getStringParameter("post-text");
    }

    String getFailureMessage() {
        return getStringParameter("failure-message");
    }

    boolean getBooleanParameter(String s) {
        return details.containsKey(s) && (boolean) details.get(s);
    }

    /**
     * Get the available score for getting this rule correct
     * @return the score form the rule config or zero if absent
     */
    double getScoreIfCorrect() {

        Object scoreObj = details.getOrDefault("score-if-correct", 0d);
        if (scoreObj.getClass() == Double.class)
            return (double) scoreObj;
        else if (scoreObj.getClass() == Integer.class)
            return 1.0 * (int) scoreObj;
        else
            return 0d;
    }

    /**
     * Get the path or empty string if there is none
     * @return as described above.
     */
    String getPath() {
        return getStringParamOrDefault("path", "");
    }

    /**
     * Get the branch or empty string if there is none
     * @return as described above.
     */
    String getBranch() {
        return getStringParamOrDefault("branch", "");
    }

    boolean getIgnoreCase() {
        return (boolean) details.getOrDefault("ignore-case", false);
    }

    /**
     * Get the contents of empty string if there is none
     * @return as described above.
     */
    String getContents() {
        return getStringParamOrDefault("contents", "");
    }

    /**
     * get a value out of a rule
     * @param key They key to the value
     * @param defaultValue if the key is not there
     * @return the string value.
     */
    private String getStringParamOrDefault(String key, String defaultValue) {
        if (details.containsKey(key)){
            return (String) details.get(key);
        }else{
            return defaultValue;
        }
    }
}
