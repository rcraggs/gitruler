package gitruler;

import java.util.Map;

public class Rule {

    public static final String TEXT_RULE_NAME = "text";

    /***
     * Adding a new rule:
     * Add a test to the test repo
     * add the title of the rule in getTitle in Rule.
     * Add the checkrule in the GitInterator
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
}
