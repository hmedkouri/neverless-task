package com.nerverless.task.dao;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConfig {
    public static DataSource createDataSource(String dbUrl, int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("HikariCP");

        return new HikariDataSource(config);
    }
}