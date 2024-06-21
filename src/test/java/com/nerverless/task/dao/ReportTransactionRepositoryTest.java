package com.nerverless.task.dao;

import static java.lang.String.format;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nerverless.task.model.Report;
import com.nerverless.task.model.TransactionId;
import com.nerverless.task.model.TransactionStatus;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReportTransactionRepositoryTest {

    private ReportTransactionRepository reportTransactionRepository;
    private static final String DB_URL = "jdbc:sqlite:build/tmp/report-test-db.db";

    private UUID transactionId1 = UUID.randomUUID();
    private UUID transactionId2 = UUID.randomUUID();

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
            connection.createStatement().executeUpdate(format("INSERT INTO report_transaction (transaction_id, user_id, status, amount, message) VALUES ('%s', 'User1', 'COMPLETED', 1000, 'Report 1')", transactionId1));
            connection.createStatement().executeUpdate(format("INSERT INTO report_transaction (transaction_id, user_id, status, amount, message) VALUES ('%s', 'User2', 'PROCESSING', 1000, 'Report 2')", transactionId2));
            connection.createStatement().executeUpdate(format("INSERT INTO report_transaction (transaction_id, user_id, status, amount, message) VALUES ('%s', 'User2', 'FAILED', 1000, 'Report 3')", transactionId2));
        }

        reportTransactionRepository = new ReportTransactionRepository(dataSource);

    }

    @Test
    void findByTransactionId_WithValidTransactionId_ReturnsListOfReportTransactions() throws SQLException {
        // Act
        List<Report> actualReportTransactions = reportTransactionRepository.findByTransactionId(transactionId1);

        // Assert
        assertEquals(1, actualReportTransactions.size());

        var report = actualReportTransactions.get(0);

        assertEquals(transactionId1, report.transactionId().id());
        assertEquals(TransactionStatus.COMPLETED, report.status());
        assertEquals(0, new BigDecimal("1000.00").compareTo(report.amount()));
        assertEquals("Report 1", report.message());
    }

    @Test
    void findLastByTransactionId_WithValidTransactionId_ReturnsOptionalReportTransaction() throws SQLException {
        // Act
        Optional<Report> actualReportTransaction = reportTransactionRepository.findLatestByTransactionId(transactionId2);

        // Assert
        assertTrue(actualReportTransaction.isPresent());

        var report = actualReportTransaction.get();        
        assertEquals(transactionId2, report.transactionId().id());
        assertEquals("Report 3", report.message());
        assertEquals(TransactionStatus.FAILED, report.status());
    }

    @Test
    void insert_WithValidReportTransaction_DoesNotThrowException() throws SQLException {
        // Arrange
        TransactionId transactionId = new TransactionId(UUID.randomUUID(), "User1");
        Report reportTransaction = new Report(transactionId, new BigDecimal("1000.00"), TransactionStatus.COMPLETED, "Report 4");

        // Act & Assert
        assertDoesNotThrow(() -> reportTransactionRepository.insert(reportTransaction));
    }
}
