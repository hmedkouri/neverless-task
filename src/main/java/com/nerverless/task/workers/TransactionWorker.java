package com.nerverless.task.workers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
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
import com.nerverless.task.model.Withdrawal;

public class TransactionWorker implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(TransactionWorker.class);

    private final UserAccountRepository userAccountRepository;
    private final ReportTransactionRepository reportTransactionRepository;

    private final BlockingQueue<Transaction> transactionQueue;
    private final BlockingQueue<Report> transactionReportQueue;
    private final BlockingQueue<Transaction> withdrawalQueue;
    private final BlockingQueue<Withdrawal> withdrawalReportQueue;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public TransactionWorker(DataSource dataSource,
            BlockingQueue<Transaction> transactionQueue,
            BlockingQueue<Report> transactionReportQueue,
            BlockingQueue<Transaction> withdrawalQueue,
            BlockingQueue<Withdrawal> withdrawalReportQueue) {
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
            BlockingQueue<Withdrawal> withdrawalReportQueue) {

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
            try {
                Transaction message = transactionQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    if (message instanceof Transfer transfer) {
                        logger.info("Processing transfer: {}", transfer);
                        process(transfer);
                    } else if (message instanceof WithdrawalRequest withdrawal) {
                        logger.info("Processing withdrawal request: {}", withdrawal);
                        process(withdrawal);
                    } else {
                        logger.error("Unsupported transaction type: {}", message);
                    }
                }
                Withdrawal withdrawal = withdrawalReportQueue.poll(1, TimeUnit.SECONDS);                
                if (withdrawal != null) {
                    logger.info("Processing withdrawal: {}", withdrawal);
                    process(withdrawal);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
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

    private void process(Withdrawal withdrawal) {
        var result = updateUserAccount(withdrawal);
        if (result.value() == PROCESSING) {
            return;
        }
        var report = report(withdrawal, result.value(), result.message());
        send(report);
    }

    private Result<TransactionStatus> updateUserAccount(Transfer message) {
        // Get user accounts
        var fromAccount = userAccountRepository.findByName(message.fromAccountName());
        if (fromAccount.isEmpty()) {
            return new Result<>(FAILED, "Account not found: " + message.fromAccountName());
        }
        var toAccount = userAccountRepository.findByName(message.toAccountName());
        if (toAccount.isEmpty()) {
            return new Result<>(FAILED, "Account not found: " + message.toAccountName());
        }

        // Validate and process transaction
        if (fromAccount.get().balance().compareTo(message.amount()) >= 0) {
            var fromAcct = fromAccount.get().withBalance(fromAccount.get().balance().subtract(message.amount()));
            var toAcct = toAccount.get().withBalance(toAccount.get().balance().add(message.amount()));
            userAccountRepository.save(fromAcct);
            userAccountRepository.save(toAcct);

            return new Result<>(COMPLETED, "Transaction completed successfully");
        } else {

            return new Result<>(FAILED, "Insufficient funds");
        }
    }

    private Result<TransactionStatus> updateUserAccount(WithdrawalRequest withdrawal) {
        // Get user accounts
        var fromAccount = userAccountRepository.findByName(withdrawal.accountName());
        if (fromAccount.isEmpty()) {
            return new Result<>(FAILED, "Account not found: " + withdrawal.accountName());
        }

        // Validate and process withdrawal
        if (fromAccount.get().balance().compareTo(withdrawal.amount()) >= 0) {
            var fromAcct = fromAccount.get().withBalance(fromAccount.get().balance().subtract(withdrawal.amount()));
            fromAcct = fromAcct.withReserve(fromAcct.reserve().add(withdrawal.amount()));
            userAccountRepository.save(fromAcct);

            return new Result<>(PROCESSING, "Withdrawal initiated");
        } else {
            return new Result<>(FAILED, "Insufficient funds");
        }
    }

    private Result<TransactionStatus> updateUserAccount(Withdrawal withdrawal) {
        // Get user accounts
        var fromAccount = userAccountRepository.findByName(withdrawal.accountName());
        if (fromAccount.isEmpty()) {
            return new Result<>(FAILED, "Account not found: " + withdrawal.accountName());
        }

        // Validate and process withdrawal
        switch (withdrawal.status()) {
            case COMPLETED -> {
                var fromAcct = fromAccount.get().withReserve(fromAccount.get().reserve().subtract(withdrawal.amount()));
                userAccountRepository.save(fromAcct);
                return new Result<>(COMPLETED, "Withdrawal completed");
            }
            case FAILED -> {
                var fromAcct = fromAccount.get().withBalance(fromAccount.get().balance().add(withdrawal.amount()));
                fromAcct = fromAcct.withReserve(fromAcct.reserve().subtract(withdrawal.amount()));
                userAccountRepository.save(fromAcct);
                return new Result<>(FAILED, "Withdrawal failed");
            }
            default -> {
                return new Result<>(PROCESSING, "Withdrawal in progress");
            }
        }
    }

    private Report report(Transaction transaction, TransactionStatus status, String message) {
        var report = new Report(transaction.transactionId(), transaction.amount(), status, message);
        reportTransactionRepository.insert(report);
        return report;
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
