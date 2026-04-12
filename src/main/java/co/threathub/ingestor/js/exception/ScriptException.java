package co.threathub.ingestor.js.exception;

public class ScriptException extends RuntimeException {

    public ScriptException(String message) {
        super(message);
    }

    public ScriptException(String message, Throwable cause) {
        super(message, cause);
    }
}
