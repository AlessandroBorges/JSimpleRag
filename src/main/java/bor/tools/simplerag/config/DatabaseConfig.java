package bor.tools.simplerag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Database configuration for JSimpleRag.
 *
 * Configures the PostgreSQL datasource with PGVector support.
 * Ensures proper connection properties for vector operations.
 */
@Configuration
public class DatabaseConfig {

    /**
     * Primary datasource configuration.
     * Uses application properties with prefix 'spring.datasource'
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * JdbcTemplate bean for JDBC operations.
     * Used primarily by DocChunkJdbcRepository for vector operations.
     */
    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}