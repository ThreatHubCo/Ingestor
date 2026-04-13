package co.threathub.ingestor.js;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.js.api.*;
import co.threathub.ingestor.js.enums.ScriptType;
import co.threathub.ingestor.log.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class JSManager {
    private static final String[] SCRIPT_FILES = {"scripts/test.js"};

    private final Map<ScriptType, String> loadedScripts = new HashMap<>();
    private final Context context;

    public JSManager(Ingestor ingestor) {
        try {
            extractScripts();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to extract scripts", ex);
        }

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

        bindings.putMember("api", ProxyObject.fromMap(Map.of(
                "sql", new SqlApi(ingestor),
                "config", new ConfigApi(ingestor),
                "log", new LogApi(),
                "tickets", new TicketApi(ingestor),
                "http", new HttpApi()
        )));

        loadScript(ScriptType.SOFTWARE_ESCALATION, "scripts/test.js");
        executeScript(ScriptType.SOFTWARE_ESCALATION);
    }

    private void extractScripts() throws IOException {
        Path targetDir = Paths.get("scripts");

        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        ClassLoader classLoader = JSManager.class.getClassLoader();

        for (String fileName : SCRIPT_FILES) {
            Path outputPath = targetDir.resolve(fileName);

            if (Files.exists(outputPath)) {
                continue;
            }

            try (InputStream is = classLoader.getResourceAsStream("scripts/" + fileName)) {
                if (is == null) {
                    throw new FileNotFoundException("Script not found in resources: " + fileName);
                }

                Files.createDirectories(outputPath.getParent());
                Files.copy(is, outputPath);
            }
        }
    }

    private void loadScript(ScriptType type, String fileName) {
        Path path = Paths.get("scripts", fileName);

        if (!Files.exists(path)) {
            throw new RuntimeException("Script not found: " + path.toAbsolutePath());
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);

            loadedScripts.put(type, content);

            Logger.info("Script loaded from filesystem: " + fileName + " (" + type.name() + ")");
        } catch (IOException ex) {
            Logger.warn("Script load error (" + fileName + "): " + ex.getMessage());
        }
    }

    public void executeScript(ScriptType type) {
        String script = loadedScripts.get(type);

        if (script == null) {
            throw new RuntimeException("Script not loaded: " + type.name());
        }
        context.eval("js", script);
    }
}
