package co.threathub.ingestor.util;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class TemplateManager {
    private static final String[] TEMPLATE_FILES = {"vuln-notification.ftl"};
    private final Template vulnerabilityTemplate;

    public TemplateManager() throws IOException {
        try {
            extractTemplates();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to extract templates", ex);
        }

        Configuration config = new Configuration(new Version("2.3.32"));
        config.setDirectoryForTemplateLoading(new File("templates"));
        config.setDefaultEncoding("UTF-8");

        vulnerabilityTemplate = config.getTemplate("vuln-notification.ftl");
    }

    private void extractTemplates() throws IOException {
        Path targetDir = Paths.get("templates");

        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        ClassLoader classLoader = TemplateManager.class.getClassLoader();

        for (String fileName : TEMPLATE_FILES) {
            Path outputPath = targetDir.resolve(fileName);

            if (Files.exists(outputPath)) {
                continue;
            }

            try (InputStream is = classLoader.getResourceAsStream("templates/" + fileName)) {
                if (is == null) {
                    throw new FileNotFoundException("Resource not found: " + fileName);
                }

                Files.copy(is, outputPath);
            }
        }
    }

    public String renderVulnerabilityTicket(Map<String, Object> model) throws Exception {
        StringWriter out = new StringWriter();
        vulnerabilityTemplate.process(model, out);
        return out.toString();
    }
}