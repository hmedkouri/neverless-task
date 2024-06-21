package com.nerverless.task.workers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sql.DataSource;

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
import com.nerverless.task.model.Withdrawal;

public class TransactionWorkerTest {
    private TransactionWorker transactionWorker;
    private UserAccountRepository userAccountRepository;
    private ReportTransactionRepository reportTransactionRepository;
    private BlockingQueue<Transaction> transactionQueue;
    private BlockingQueue<Report> reportQueue;
    private BlockingQueue<Transaction> withdrawalQueue;
    private BlockingQueue<Withdrawal> withdrawalReportQueue;

    @BeforeEach
    public void setUp() throws SQLException {
        userAccountRepository = mock(UserAccountRepository.class);
        reportTransactionRepository = mock(ReportTransactionRepository.class);
        DataSource dataSource = mock(DataSource.class);
    
        transactionQueue = new LinkedBlockingQueue<>();
        reportQueue = new LinkedBlockingQueue<>();
        withdrawalQueue = new LinkedBlockingQueue<>();
        withdrawalReportQueue = new LinkedBlockingQueue<>();
        transactionWorker = new TransactionWorker(dataSource, userAccountRepository, reportTransactionRepository, transactionQueue, reportQueue, withdrawalQueue, withdrawalReportQueue);
    }

    @Test
    public void testTransactionSuccess() throws InterruptedException {
        Optional<UserAccount> user1 = Optional.of(new UserAccount("User1", new BigDecimal("1000.00"), BigDecimal.ZERO));
        Optional<UserAccount> user2 = Optional.of(new UserAccount("User2", new BigDecimal("1000.00"), BigDecimal.ZERO));
                
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

        verify(userAccountRepository, times(2)).save(any(UserAccount.class));
        ArgumentCaptor<Report> argument = ArgumentCaptor.forClass(Report.class);
        verify(reportTransactionRepository, times(1)).insert(argument.capture());
        assertEquals(transactionMessage.transactionId(), argument.getValue().transactionId());
        assertEquals(TransactionStatus.COMPLETED, argument.getValue().status());

        transactionWorker.stop();
        workerThread.interrupt();
    }

    @Test
    void testProcessTransactionInsufficientFunds() throws InterruptedException {
        Optional<UserAccount> user1 = Optional.of(new UserAccount("User1", new BigDecimal("50.00"), BigDecimal.ZERO));
        Optional<UserAccount> user2 = Optional.of(new UserAccount("User2", new BigDecimal("1000.00"), BigDecimal.ZERO));
        
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

        transactionWorker.stop();
        workerThread.interrupt();
    }

    @Test
    void testProcessTransactionUserAccountNotFound() throws InterruptedException {
        when(userAccountRepository.findByName(anyString())).thenReturn(Optional.empty());

        Transaction transactionMessage = new Transaction.Transfer(new TransactionId(UUID.randomUUID(), "User1"), "User1", "User2", new BigDecimal("100.00"));
        transactionQueue.put(transactionMessage);

        Thread workerThread = new Thread(transactionWorker);
        workerThread.start();

        // Wait for the transaction to be processed
        Report report = reportQueue.take();

        assertEquals("User1", report.transactionId().userId());        
        assertTrue(!report.status().equals(TransactionStatus.COMPLETED));
        assertEquals("Account not found: User1", report.message());

        transactionWorker.stop();
        workerThread.interrupt();
    }
}

