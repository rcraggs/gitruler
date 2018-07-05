package gitruler;

import java.util.Map;

public class Rule {

    private Map<String, Object> details;

    Rule(Map<String, Object> details){
        this.details = details;
    }

    String getTitle() {

        switch ((String) details.get("rule")){
            case "file-exists-in-head":
                return "The file exists: " + (String) details.get("path");
            default:
                return "Unknown rule";
        }
    }

}
