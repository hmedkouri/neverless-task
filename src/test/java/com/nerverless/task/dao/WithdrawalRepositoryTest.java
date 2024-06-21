package com.nerverless.task.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.nerverless.task.model.TransactionId;
import com.nerverless.task.model.TransactionStatus;
import com.nerverless.task.model.Withdrawal;
import com.nerverless.task.service.WithdrawalService.WithdrawalId;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WithdrawalRepositoryTest {

    private WithdrawalRepository withdrawalRepository;

    private static final String DB_URL = "jdbc:sqlite:build/tmp/withdrawal-test-db.db";
    UUID transactionId1 = UUID.randomUUID();

    @BeforeEach
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
            connection.createStatement().executeUpdate("INSERT INTO withdrawal (withdrawal_id, transaction_id, user_id, account_name, to_address, status, amount, message) VALUES ('" + UUID.randomUUID() + "', '" + transactionId1 + "', 'User1', 'UserAcct1', 'Address1', 'COMPLETED', 1000, 'Withdrawal 1')");
            connection.createStatement().executeUpdate("INSERT INTO withdrawal (withdrawal_id, transaction_id, user_id, account_name, to_address, status, amount, message) VALUES ('" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', 'User2', 'UserAcct2', 'Address2', 'PROCESSING', 1000, 'Withdrawal 2')");
            connection.createStatement().executeUpdate("INSERT INTO withdrawal (withdrawal_id, transaction_id, user_id, account_name, to_address, status, amount, message) VALUES ('" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', 'User3', 'UserAcct3', 'Address3', 'FAILED', 1000, 'Withdrawal 3')");
        }

        withdrawalRepository = new WithdrawalRepository(dataSource);
    }

    @Test
    void findByTransactionId_WithValidTransactionId_ReturnsListOfWithdrawals() throws SQLException {
        // Act
        TransactionId transactionId = new TransactionId(transactionId1, "User1");
        Optional<Withdrawal> actualWithdrawal = withdrawalRepository.findByTransactionId(transactionId);

        // Assert
        assertFalse(actualWithdrawal.isEmpty());
        assertEquals("User1", actualWithdrawal.get().transactionId().userId());
        assertEquals(TransactionStatus.COMPLETED, actualWithdrawal.get().status());
        assertEquals(0, new BigDecimal("1000.00").compareTo(actualWithdrawal.get().amount()));
    }

    @Test
    void findByTransactionId_WithInvalidTransactionId_ReturnsEmptyList() throws SQLException {
        // Act
        TransactionId transactionId = new TransactionId(UUID.randomUUID(), "User1");
        Optional<Withdrawal> actualWithdrawal = withdrawalRepository.findByTransactionId(transactionId);

        // Assert
        assertTrue(actualWithdrawal.isEmpty());
    }

    @Test
    void findByUserId_WithValidUserId_ReturnsListOfWithdrawals() throws SQLException {
        // Act
        List<Withdrawal> actualWithdrawals = withdrawalRepository.findWithStatus(TransactionStatus.PROCESSING);

        // Assert
        assertEquals(1, actualWithdrawals.size());
        assertEquals("User2", actualWithdrawals.get(0).transactionId().userId());
        assertEquals(TransactionStatus.PROCESSING, actualWithdrawals.get(0).status());
        assertEquals(0, new BigDecimal("1000.00").compareTo(actualWithdrawals.get(0).amount()));
    }

    @Test
    void insert_WithValidWithdrawal_InsertAndUpdated() throws SQLException {
        // Arrange
        WithdrawalId withdrawalId = new WithdrawalId(UUID.randomUUID());
        TransactionId transactionId = new TransactionId(UUID.randomUUID(), "User1");

        Withdrawal withdrawal = new Withdrawal(withdrawalId, transactionId, "UserAcct1", "Address", new BigDecimal("1000.00"), TransactionStatus.PROCESSING);

        // Act & Assert
        withdrawalRepository.save(withdrawal);

        Optional<Withdrawal> actualWithdrawal = withdrawalRepository.findByTransactionId(transactionId);
        assertFalse(actualWithdrawal.isEmpty());
        assertEquals("User1", actualWithdrawal.get().transactionId().userId());
        assertEquals(TransactionStatus.PROCESSING, actualWithdrawal.get().status());
        assertEquals(0, new BigDecimal("1000.00").compareTo(actualWithdrawal.get().amount()));
        assertEquals("Address", actualWithdrawal.get().toAddress());

        withdrawal = new Withdrawal(withdrawalId, transactionId, "UserAcct1", "Address", new BigDecimal("1000.00"), TransactionStatus.COMPLETED);
        withdrawalRepository.save(withdrawal);

        actualWithdrawal = withdrawalRepository.findByTransactionId(transactionId);
        assertFalse(actualWithdrawal.isEmpty());
        assertEquals("User1", actualWithdrawal.get().transactionId().userId());
        assertEquals(TransactionStatus.COMPLETED, actualWithdrawal.get().status());
        assertEquals(0, new BigDecimal("1000.00").compareTo(actualWithdrawal.get().amount()));
        assertEquals("Address", actualWithdrawal.get().toAddress());
    }
}
