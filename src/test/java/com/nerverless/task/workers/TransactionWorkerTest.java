package com.nerverless.task.workers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nerverless.task.dao.ReportTransactionRepository;
import com.nerverless.task.dao.UserAccountRepository;
import com.nerverless.task.model.Report;
import com.nerverless.task.model.Transaction;
import com.nerverless.task.model.TransactionId;
import com.nerverless.task.model.TransactionStatus;
import com.nerverless.task.model.UserAccount;

public class TransactionWorkerTest {
    private TransactionWorker transactionWorker;
    private UserAccountRepository userAccountRepository;
    private ReportTransactionRepository reportTransactionRepository;
    private Connection connection;
    private BlockingQueue<Transaction> transactionQueue;
    private BlockingQueue<Report> reportQueue;
    private BlockingQueue<Transaction> withdrawalQueue;
    private BlockingQueue<Report> withdrawalReportQueue;

    @BeforeEach
    public void setUp() throws SQLException {
        userAccountRepository = mock(UserAccountRepository.class);
        reportTransactionRepository = mock(ReportTransactionRepository.class);
        connection = mock(Connection.class);

        transactionQueue = new LinkedBlockingQueue<>();
        reportQueue = new LinkedBlockingQueue<>();
        transactionWorker = new TransactionWorker(connection, userAccountRepository, reportTransactionRepository, transactionQueue, reportQueue, withdrawalQueue, withdrawalReportQueue);
    }

    @Test
    public void testTransactionSuccess() throws InterruptedException, SQLException {
        UserAccount user1 = new UserAccount("User1", new BigDecimal("1000.00"), BigDecimal.ZERO);
        UserAccount user2 = new UserAccount("User2", new BigDecimal("1000.00"), BigDecimal.ZERO);
                
        when(userAccountRepository.findByName("User1")).thenReturn(user1);
        when(userAccountRepository.findByName("User2")).thenReturn(user2);

        Transaction transactionMessage = new Transaction.Transfer(new TransactionId(UUID.randomUUID(), "User1"), "User1", "User2", new BigDecimal("100.00"));
        transactionQueue.put(transactionMessage);

        Thread workerThread = new Thread(transactionWorker);
        workerThread.start();

        // Wait for the transaction to be processed
        Report report = reportQueue.take();

        assertEquals("User1", report.transactionId().userId());
        assertTrue(report.status().equals(TransactionStatus.COMPLETED));
        assertEquals("Transaction completed successfully", report.message());

        verify(userAccountRepository, times(2)).update(any(UserAccount.class));
        ArgumentCaptor<Report> argument = ArgumentCaptor.forClass(Report.class);
        verify(reportTransactionRepository, times(1)).insert(argument.capture());
        assertEquals(transactionMessage.transactionId(), argument.getValue().transactionId());
        assertEquals(TransactionStatus.COMPLETED, argument.getValue().status());
        verify(connection, times(1)).commit();

        transactionWorker.stop();
        workerThread.interrupt();
    }

    @Test
    void testProcessTransactionInsufficientFunds() throws SQLException, InterruptedException {
        UserAccount user1 = new UserAccount("User1", new BigDecimal("50.00"), BigDecimal.ZERO);
        UserAccount user2 = new UserAccount("User2", new BigDecimal("1000.00"), BigDecimal.ZERO);
        
        when(userAccountRepository.findByName("User1")).thenReturn(user1);
        when(userAccountRepository.findByName("User2")).thenReturn(user2);

        Transaction transactionMessage = new Transaction.Transfer(new TransactionId(UUID.randomUUID(), "User1"), "User1", "User2", new BigDecimal("100.00"));
        transactionQueue.put(transactionMessage);

        Thread workerThread = new Thread(transactionWorker);
        workerThread.start();

        // Wait for the transaction to be processed
        Report report = reportQueue.take();

        assertEquals("User1", report.transactionId().userId());
        assertTrue(report.status().equals(TransactionStatus.FAILED));
        assertEquals("Insufficient funds", report.message());

        ArgumentCaptor<Report> argument = ArgumentCaptor.forClass(Report.class);
        verify(reportTransactionRepository, times(1)).insert(argument.capture());
        assertEquals(transactionMessage.transactionId(), argument.getValue().transactionId());
        assertEquals(TransactionStatus.FAILED, argument.getValue().status());
        verify(connection, times(1)).rollback();

        transactionWorker.stop();
        workerThread.interrupt();
    }

    @Test
    void testProcessTransactionException() throws SQLException, InterruptedException {
        when(userAccountRepository.findByName(anyString())).thenThrow(new SQLException("Database error"));

        Transaction transactionMessage = new Transaction.Transfer(new TransactionId(UUID.randomUUID(), "User1"), "User1", "User2", new BigDecimal("100.00"));
        transactionQueue.put(transactionMessage);

        Thread workerThread = new Thread(transactionWorker);
        workerThread.start();

        // Wait for the transaction to be processed
        Report report = reportQueue.take();

        assertEquals("User1", report.transactionId().userId());        
        assertTrue(!report.status().equals(TransactionStatus.COMPLETED));
        assertTrue(report.message().contains("Transaction failed: Database error"));

        verify(connection, times(1)).rollback();

        transactionWorker.stop();
        workerThread.interrupt();
    }
}

