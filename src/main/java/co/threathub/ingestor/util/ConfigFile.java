package co.threathub.ingestor.util;

import lombok.Getter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

@Getter
public class ConfigFile {
    private static final String PROPERTIES_FILE_NAME = "config.properties";

    private String url;
    private String username;
    private String password;
    private long maxLifetime;
    private String defenderBaseApiUrl;
    private boolean allowUnrestrictedSqlInJs;
    private String reportUserUsername;
    private String reportUserPassword;

    public ConfigFile() {
        Path appDirFile = Path.of(System.getProperty("user.dir"), PROPERTIES_FILE_NAME);

        if (Files.notExists(appDirFile)) {
            try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME)) {
                if (resourceStream == null) {
                    throw new RuntimeException(PROPERTIES_FILE_NAME + " not found in resources");
                }
                Files.copy(resourceStream, appDirFile, StandardCopyOption.REPLACE_EXISTING);
                System.out.println(PROPERTIES_FILE_NAME + " copied to app directory");
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + PROPERTIES_FILE_NAME + " to app dir", e);
            }
        }

        try (InputStream fis = Files.newInputStream(appDirFile)) {
            Properties prop = new Properties();
            prop.load(fis);

            this.url = prop.getProperty("database.url");
            this.username = prop.getProperty("database.username");
            this.password = prop.getProperty("database.password");
            this.maxLifetime = Long.parseLong(prop.getProperty("database.maxLifetime", "600000"));
            this.defenderBaseApiUrl = prop.getProperty("defender.baseApiUrl", "https://api-eu3.securitycenter.microsoft.com");
            this.allowUnrestrictedSqlInJs = Boolean.parseBoolean(prop.getProperty("js.allow-unrestricted-sql"));
            this.reportUserUsername = prop.getProperty("database.reportUserUsername");
            this.reportUserPassword = prop.getProperty("database.reportUserPassword");
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load " + PROPERTIES_FILE_NAME, ex);
        }
    }
}