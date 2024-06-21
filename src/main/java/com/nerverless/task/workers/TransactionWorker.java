package com.nerverless.task.workers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nerverless.task.dao.ReportTransactionRepository;
import com.nerverless.task.dao.UserAccountRepository;
import com.nerverless.task.model.Report;
import com.nerverless.task.model.Result;
import com.nerverless.task.model.Transaction;
import com.nerverless.task.model.Transaction.Transfer;
import com.nerverless.task.model.Transaction.WithdrawalRequest;
import com.nerverless.task.model.TransactionStatus;
import static com.nerverless.task.model.TransactionStatus.COMPLETED;
import static com.nerverless.task.model.TransactionStatus.FAILED;
import static com.nerverless.task.model.TransactionStatus.PROCESSING;

public class TransactionWorker implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(TransactionWorker.class);

    private final DataSource dataSource;
    private final UserAccountRepository userAccountRepository;
    private final ReportTransactionRepository reportTransactionRepository;

    private final BlockingQueue<Transaction> transactionQueue;
    private final BlockingQueue<Report> transactionReportQueue;
    private final BlockingQueue<Transaction> withdrawalQueue;
    private final BlockingQueue<Report> withdrawalReportQueue;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public TransactionWorker(DataSource dataSource,
            BlockingQueue<Transaction> transactionQueue,
            BlockingQueue<Report> transactionReportQueue,
            BlockingQueue<Transaction> withdrawalQueue,
            BlockingQueue<Report> withdrawalReportQueue) {
        this(dataSource, 
            new UserAccountRepository(dataSource), 
            new ReportTransactionRepository(dataSource), 
            transactionQueue, 
            transactionReportQueue, 
            withdrawalQueue, 
            withdrawalReportQueue);
    }

    /*
    * For testing purposes this constructor with the repositories is added
     */
    public TransactionWorker(DataSource dataSource,
            UserAccountRepository userAccountRepository,
            ReportTransactionRepository reportTransactionRepository,
            BlockingQueue<Transaction> transactionQueue,
            BlockingQueue<Report> transactionReportQueue,
            BlockingQueue<Transaction> withdrawalQueue,
            BlockingQueue<Report> withdrawalReportQueue) {

        this.dataSource = dataSource;
        this.userAccountRepository = userAccountRepository;
        this.reportTransactionRepository = reportTransactionRepository;

        this.transactionQueue = transactionQueue;
        this.transactionReportQueue = transactionReportQueue;
        this.withdrawalQueue = withdrawalQueue;
        this.withdrawalReportQueue = withdrawalReportQueue;
    }

    @Override
    public void run() {
        while (running.get()) {
            Transaction message = transactionQueue.poll();
            if (message != null) {
                if (message instanceof Transfer transfer) {
                    process(transfer);
                } else if (message instanceof WithdrawalRequest withdrawal) {
                    process(withdrawal);
                } else {
                    logger.error("Unsupported transaction type: {}", message);
                }
            }
            Report report = withdrawalReportQueue.poll();
            if (report != null) {
                process(report);
            }
        }
    }

    public void stop() {
        running.set(false);
    }

    private void process(Transfer transfer) {
        var result = updateUserAccount(transfer);
        var report = report(transfer, result.value(), result.message());
        send(report);
    }

    private void process(WithdrawalRequest withdrawal) {
        var result = updateUserAccount(withdrawal);
        if (result.value() == PROCESSING) {
            requestWithdrawal(withdrawal);
        }
        var report = report(withdrawal, result.value(), result.message());
        send(report);
    }

    private void process(Report report) {
        try {
            reportTransactionRepository.insert(report);
        } catch (SQLException ex) {
            logger.error("Failed to insert report transaction", ex);
        }
    }

    private Result<TransactionStatus> updateUserAccount(Transfer message) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            // Get user accounts
            var fromAccount = userAccountRepository.findByName(message.fromAccountName());
            var toAccount = userAccountRepository.findByName(message.toAccountName());

            // Validate and process transaction
            if (fromAccount.balance().compareTo(message.amount()) >= 0) {
                fromAccount = fromAccount.withBalance(fromAccount.balance().subtract(message.amount()));
                toAccount = toAccount.withBalance(toAccount.balance().add(message.amount()));
                userAccountRepository.update(fromAccount);
                userAccountRepository.update(toAccount);

                connection.commit();
                return new Result<>(COMPLETED, "Transaction completed successfully");
            } else {
                connection.rollback();
                return new Result<>(FAILED, "Insufficient funds");
            }
        } catch (SQLException e) {            
            return new Result<>(FAILED, "Transaction failed: " + e.getMessage());
        }
    }

    private Result<TransactionStatus> updateUserAccount(WithdrawalRequest withdrawal) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            // Get user accounts
            var fromAccount = userAccountRepository.findByName(withdrawal.accountName());

            // Validate and process withdrawal
            if (fromAccount.balance().compareTo(withdrawal.amount()) >= 0) {
                fromAccount = fromAccount.withBalance(fromAccount.balance().subtract(withdrawal.amount()));
                fromAccount = fromAccount.withReserve(fromAccount.reserve().add(withdrawal.amount()));
                userAccountRepository.update(fromAccount);

                connection.commit();
                return new Result<>(PROCESSING, "Withdrawal initiated");
            } else {
                connection.rollback();
                return new Result<>(FAILED, "Insufficient funds");
            }
        } catch (SQLException e) {            
            return new Result<>(FAILED, "Withdrawal failed: " + e.getMessage());
        }
    }

    private Report report(Transaction transaction, TransactionStatus status, String message) {
        try {
            var report = new Report(transaction.transactionId(), transaction.amount(), status, message);
            reportTransactionRepository.insert(report);
            return report;
        } catch (SQLException ex) {
            logger.error("Failed to insert report transaction", ex);
            return new Report(transaction.transactionId(), transaction.amount(), FAILED, "Transaction failed: " + ex.getMessage());
        }
    }

    private void send(Report report) {
        try {
            transactionReportQueue.put(report);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void requestWithdrawal(Transaction.WithdrawalRequest withdrawal) {
        try {
            withdrawalQueue.put(withdrawal);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
