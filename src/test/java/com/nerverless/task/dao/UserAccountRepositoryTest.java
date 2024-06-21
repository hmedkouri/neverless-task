package com.nerverless.task.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nerverless.task.model.UserAccount;

@ExtendWith(MockitoExtension.class)
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
        UserAccount user = userAccountRepository.findByName("User1");
        assertNotNull(user);
        assertEquals("User1", user.name());
        assertEquals(0, new BigDecimal("1000.00").compareTo(user.balance()));
        assertEquals(0, new BigDecimal("0.00").compareTo(user.reserve()));
    }

    @Test
    void testFindByName_UserNotFound() {
        assertThrows(SQLException.class, () -> userAccountRepository.findByName("NonExistentUser"));
    }

    @Test
    void testUpdate() throws SQLException {
        UserAccount user = userAccountRepository.findByName("User1");
        user = user.withBalance(new BigDecimal("900.00"));
        user = user.withReserve(new BigDecimal("100.00"));
        userAccountRepository.update(user);

        UserAccount updatedUser = userAccountRepository.findByName("User1");
        assertEquals(0, new BigDecimal("900.00").compareTo(updatedUser.balance()));
        assertEquals(0, new BigDecimal("100.00").compareTo(updatedUser.reserve()));
    }
}
