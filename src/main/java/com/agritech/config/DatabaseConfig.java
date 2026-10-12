package com.agritech.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        String springDatasourceUrl = System.getenv("SPRING_DATASOURCE_URL");

        // If standard SPRING_DATASOURCE_URL is provided (e.g. from Render postgres or properties)
        if (springDatasourceUrl != null && !springDatasourceUrl.isBlank()) {
            String user = System.getenv("SPRING_DATASOURCE_USERNAME");
            String pass = System.getenv("SPRING_DATASOURCE_PASSWORD");
            return DataSourceBuilder.create()
                    .url(springDatasourceUrl)
                    .username(user != null ? user : "postgres")
                    .password(pass != null ? pass : "")
                    .build();
        }

        // If DATABASE_URL is provided (Render / Heroku format: postgres://user:password@host:port/dbname)
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            try {
                if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
                    URI uri = new URI(databaseUrl);
                    String userInfo = uri.getUserInfo();
                    String user = "";
                    String password = "";
                    if (userInfo != null && userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        user = parts[0];
                        password = parts[1];
                    }

                    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                    String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();

                    return DataSourceBuilder.create()
                            .url(jdbcUrl)
                            .username(user)
                            .password(password)
                            .driverClassName("org.postgresql.Driver")
                            .build();
                } else if (databaseUrl.startsWith("jdbc:")) {
                    return DataSourceBuilder.create()
                            .url(databaseUrl)
                            .username(System.getenv("DB_USERNAME"))
                            .password(System.getenv("DB_PASSWORD"))
                            .build();
                }
            } catch (Exception ignored) {}
        }

        // Default to local MySQL properties
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:mysql://localhost:3306/agritech?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }
        String dbUser = System.getenv("DB_USERNAME");
        String dbPass = System.getenv("DB_PASSWORD");

        return DataSourceBuilder.create()
                .url(dbUrl)
                .username(dbUser != null ? dbUser : "root")
                .password(dbPass != null ? dbPass : "aryan")
                .build();
    }
}
