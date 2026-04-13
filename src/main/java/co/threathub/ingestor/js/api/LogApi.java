package co.threathub.ingestor.js.api;

import co.threathub.ingestor.log.Logger;
import org.graalvm.polyglot.HostAccess;

public class LogApi {

    @HostAccess.Export
    public void info(String text) {
        Logger.info(text);
    }

    @HostAccess.Export
    public void warn(String text) {
        Logger.warn(text);
    }

    @HostAccess.Export
    public void error(String text) {
        Logger.error(text);
    }

    @HostAccess.Export
    public void debug(String text) {
        Logger.debug(text);
    }
}
