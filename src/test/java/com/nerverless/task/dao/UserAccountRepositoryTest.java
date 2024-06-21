package com.nerverless.task.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.nerverless.task.model.UserAccount;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserAccountRepositoryTest {

    private UserAccountRepository userAccountRepository;
    private static final String DB_URL = "jdbc:sqlite:build/tmp/test-db.db";
    

    @BeforeAll
    void setUp() throws SQLException {
        // Setup the database connection        
        DataSource dataSource = DatabaseConfig.createDataSource(DB_URL, 2);
        
        // Initialize the database schema using Flyway
        Flyway flyway = Flyway.configure().dataSource(dataSource)
            .cleanDisabled(false)
            .load();        
        flyway.clean();
        flyway.migrate();
        
        // Insert initial data
        try (Connection connection = dataSource.getConnection()) {            
            connection.createStatement().executeUpdate("INSERT INTO user_account (name, balance, reserve) VALUES ('User1', 1000, 0)");
            connection.createStatement().executeUpdate("INSERT INTO user_account (name, balance, reserve) VALUES ('User2', 1000, 0)");
        }

        userAccountRepository = new UserAccountRepository(dataSource);
    }

    @Test
    void testFindByName() throws SQLException {
        Optional<UserAccount> user = userAccountRepository.findByName("User1");
        assertFalse(user.isEmpty());
        assertEquals("User1", user.get().name());
        assertEquals(0, new BigDecimal("1000.00").compareTo(user.get().balance()));
        assertEquals(0, new BigDecimal("0.00").compareTo(user.get().reserve()));
    }

    @Test
    void testFindByName_UserNotFound() {
        assertTrue(userAccountRepository.findByName("NonExistentUser").isEmpty());
    }

    @Test
    void testUpdate() throws SQLException {
        Optional<UserAccount> user = userAccountRepository.findByName("User1");
        var update = user.get().withBalance(new BigDecimal("900.00"));
        update = update.withReserve(new BigDecimal("100.00"));
        userAccountRepository.save(update);

        Optional<UserAccount> updatedUser = userAccountRepository.findByName("User1");
        assertFalse(updatedUser.isEmpty());
        assertEquals(0, new BigDecimal("900.00").compareTo(updatedUser.get().balance()));
        assertEquals(0, new BigDecimal("100.00").compareTo(updatedUser.get().reserve()));
    }
}
