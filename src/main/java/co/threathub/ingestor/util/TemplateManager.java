package co.threathub.ingestor.util;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public class TemplateManager {
    private final Template vulnerabilityTemplate;

    public TemplateManager() throws IOException {
        Configuration config = new Configuration(new Version("2.3.32"));
        config.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        config.setDefaultEncoding("UTF-8");

        // Load templates
        vulnerabilityTemplate = config.getTemplate("vuln-notification.ftl");
    }

    public String renderVulnerabilityTicket(Map<String, Object> model) throws Exception {
        StringWriter out = new StringWriter();
        vulnerabilityTemplate.process(model, out);
        return out.toString();
    }
}