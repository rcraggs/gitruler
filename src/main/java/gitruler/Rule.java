package gitruler;

import java.util.Map;

public class Rule {

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
            case "file-exists-in-head":
                return "The file exists: " + (String) details.get("path");
            case "head-exists":
                return "There is a valid repository";
            case "file-not-exist-in-head":
                return "The file should not exist: " + (String) details.get("path");
            case "blob-exists-in-location-in-head":
                return "An existing file should now be at: " + (String) details.get("path");
            default:
                return "Unknown rule";
        }
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

    String getPreText() {
        return getStringParameter("pre-text");
    }

    String getPostText() {
        return getStringParameter("post-text");
    }
}
