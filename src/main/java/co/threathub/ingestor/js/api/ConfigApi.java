package co.threathub.ingestor.js.api;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.config.ConfigEntry;
import co.threathub.ingestor.config.ConfigKey;
import co.threathub.ingestor.js.JSValueConverter;
import co.threathub.ingestor.js.exception.ScriptException;
import co.threathub.ingestor.log.Logger;
import co.threathub.ingestor.util.Utils;
import lombok.RequiredArgsConstructor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class ConfigApi {
    private final Ingestor ingestor;

    @HostAccess.Export
    public Value getAll() {
        Map<String, Object> results = new HashMap<>();

        for (Map.Entry<ConfigKey, ConfigEntry> entry : ingestor.getConfigRepository().getAll().entrySet()) {
            results.put(entry.getKey().name(), entry.getValue().getValue());
        }

        String json = Utils.GSON.toJson(results);
        return Context.getCurrent().eval("js", "(" + json + ")");
    }

    @HostAccess.Export
    public Value get(String key) {
        try {
            ConfigKey enumKey = ConfigKey.valueOf(key);
            ConfigEntry entry = ingestor.getConfigRepository().getAll().get(enumKey);
            Object returnValue = entry != null ? entry.getValue() : null;

            return Context.getCurrent().asValue(JSValueConverter.convert(returnValue));
        } catch (IllegalArgumentException ex) {
            throw new ScriptException("Invalid config key: " + key, ex);
        } catch (Exception ex) {
            throw new ScriptException("Failed to get config key: " + ex.getMessage(), ex);
        }
    }
}