package com.fotofetch.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Slf4j
public class DataSourceConfig {

    @Value("${DATABASE_URL:#{null}}")
    private String databaseUrl;

    @Value("${spring.datasource.url:#{null}}")
    private String springDatasourceUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String springDatasourceUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String springDatasourcePassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        // Option 1: Parse DATABASE_URL environment variable provided by Render / Heroku / Railway
        if (databaseUrl != null && !databaseUrl.isBlank() &&
                (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            log.info("Detected DATABASE_URL environment variable (Render PostgreSQL)");
            try {
                URI dbUri = new URI(databaseUrl);
                String userInfo = dbUri.getUserInfo();
                String username = null;
                String password = null;

                if (userInfo != null && userInfo.contains(":")) {
                    String[] userParts = userInfo.split(":", 2);
                    username = userParts[0];
                    password = userParts[1];
                } else if (userInfo != null) {
                    username = userInfo;
                }

                int port = dbUri.getPort() == -1 ? 5432 : dbUri.getPort();
                String host = dbUri.getHost();
                String path = dbUri.getPath();
                String dbName = (path != null && path.length() > 1) ? path.substring(1) : "fotofetch";

                String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
                log.info("Converted DATABASE_URL to JDBC URL: jdbc:postgresql://{}:{}/{}", host, port, dbName);

                config.setJdbcUrl(jdbcUrl);
                if (username != null) config.setUsername(username);
                if (password != null) config.setPassword(password);
                config.setDriverClassName("org.postgresql.Driver");
            } catch (Exception ex) {
                log.warn("Could not parse DATABASE_URL as URI, falling back to standard configuration: {}", ex.getMessage());
                configureStandard(config);
            }
        } else {
            configureStandard(config);
        }

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }

    private void configureStandard(HikariConfig config) {
        String url = (springDatasourceUrl != null && !springDatasourceUrl.isBlank())
                ? springDatasourceUrl
                : "jdbc:postgresql://localhost:5432/fotofetch";

        config.setJdbcUrl(url);
        config.setUsername(springDatasourceUsername != null ? springDatasourceUsername : "postgres");
        config.setPassword(springDatasourcePassword != null ? springDatasourcePassword : "admin");

        if (url.startsWith("jdbc:h2:")) {
            config.setDriverClassName("org.h2.Driver");
        } else {
            config.setDriverClassName("org.postgresql.Driver");
        }
    }
}
