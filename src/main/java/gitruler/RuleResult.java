package gitruler;

/**
 * Created by rc305 on 05/07/2018.
 */
public class RuleResult {

    private boolean passed;
    private String message = "";

    void setPassed(boolean passed) {
        this.passed = passed;
    }

    void setMessage(String message) {
        this.message = message;
    }

    boolean hasPassed() {
        return this.passed;
    }

    String getMessage(){
        return this.message;
    }
}
