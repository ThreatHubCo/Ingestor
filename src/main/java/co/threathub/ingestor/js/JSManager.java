package co.threathub.ingestor.js;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.js.api.ConfigApi;
import co.threathub.ingestor.js.api.LogApi;
import co.threathub.ingestor.js.api.SqlApi;
import co.threathub.ingestor.js.api.TicketApi;
import co.threathub.ingestor.log.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JSManager {
    private final Context context;

    public JSManager(Ingestor ingestor) {
        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.NONE)
                .allowAccessAnnotatedBy(HostAccess.Export.class)
                .allowPublicAccess(true)
                .allowListAccess(true)
                .allowMapAccess(true)
                .build();

        context = Context.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false") // Hide startup message
                .allowHostAccess(hostAccess)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .build();

        Value bindings = context.getBindings("js");

        bindings.putMember("th", ProxyObject.fromMap(Map.of(
                "sql", new SqlApi(ingestor),
                "config", new ConfigApi(ingestor),
                "log", new LogApi(),
                "tickets", new TicketApi(ingestor)
        )));
    }

    public void test() {
        InputStream is = JSManager.class.getClassLoader().getResourceAsStream("test.js");

        if (is == null) {
            throw new RuntimeException("File not found!");
        }

        try {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            context.eval("js", content);
        } catch (Exception ex) {
            Logger.warn("Script error: " + ex.getMessage());
        }
    }
}
