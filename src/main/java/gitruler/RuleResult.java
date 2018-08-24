package gitruler;

/**
 * Created by rc305 on 05/07/2018.
 */
public class RuleResult {

    private boolean passed;
    private String message = "";
    private String exceptionMessage = "";
    private String exceptionTrace = "";
    public boolean exceptionOccurred;

    public RuleResult(boolean defaultPassed) {
        passed = defaultPassed;
    }

    public RuleResult() {
        this(false);
    }

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

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getExceptionTrace() {
        return exceptionTrace;
    }

    public void setExceptionTrace(String exceptionTrace) {
        this.exceptionTrace = exceptionTrace;
    }

    void setFailWithMessage(String message){
        this.setPassed(false);
        this.setMessage(message);
    }
}
