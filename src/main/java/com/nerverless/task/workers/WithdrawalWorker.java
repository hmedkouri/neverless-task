package com.nerverless.task.workers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nerverless.task.dao.WithdrawalRepository;
import com.nerverless.task.model.Report;
import com.nerverless.task.model.Result;
import com.nerverless.task.model.Transaction;
import com.nerverless.task.model.Transaction.WithdrawalRequest;
import com.nerverless.task.model.TransactionStatus;
import com.nerverless.task.service.WithdrawalService;
import com.nerverless.task.service.WithdrawalService.WithdrawalState;

public class WithdrawalWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WithdrawalWorker.class);

    private final BlockingQueue<Transaction> withdrawalQueue;
    private final BlockingQueue<Report> withdrawalReportQueue;
    private final WithdrawalService withdrawalService;
    private final DataSource dataSource;
    private final WithdrawalRepository withdrawalRepository;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public WithdrawalWorker(DataSource dataSource,
                            WithdrawalService withdrawalService,                            
                            BlockingQueue<Transaction> withdrawalQueue, 
                            BlockingQueue<Report> withdrawalReportQueue) {
        this.withdrawalQueue = withdrawalQueue;
        this.withdrawalReportQueue = withdrawalReportQueue;
        this.withdrawalService = withdrawalService;
        this.dataSource = dataSource;
        this.withdrawalRepository = new WithdrawalRepository(dataSource);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Transaction message = withdrawalQueue.poll(1, TimeUnit.SECONDS);
                if (message == null) {
                    checkProcessingWithdrwals();
                } else if (message instanceof WithdrawalRequest withdrawal) {
                    process(withdrawal);
                } else {
                    logger.error("Unsupported transaction type: {}", message);
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

    private void process(WithdrawalRequest withdrawal) {
        try {
            WithdrawalService.WithdrawalId id = new WithdrawalService.WithdrawalId(withdrawal.transactionId().id());
            WithdrawalService.Address address = new WithdrawalService.Address(withdrawal.toAddress());
            withdrawalService.requestWithdrawal(id, address, withdrawal.amount());
            var report = new Report(withdrawal.transactionId(), withdrawal.amount(), TransactionStatus.PROCESSING, "Withdrawal request sent");
            send(report);
        } catch (Exception e) {
            logger.error("Failed to process withdrawal request: {}", withdrawal, e);
            var report = new Report(withdrawal.transactionId(), withdrawal.amount(), TransactionStatus.FAILED, "Withdrawal request failed");
            send(report);
        }
    }

    private void checkProcessingWithdrwals() {
        try {
            withdrawalRepository.findProcessing().forEach( withdrawal -> {
                var result = checkWithdrawal(withdrawal.withdrawalId());
                var report = new Report(withdrawal.transactionId(), withdrawal.amount(), result.value(), result.message());
                if (result.value() != TransactionStatus.PROCESSING) {
                    send(report);
                }
            });
        } catch (SQLException e) {
            logger.error("Failed to check processing withdrawals", e);
        }
    }

    private void send(Report report) {
        try {
            withdrawalReportQueue.put(report);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Result<TransactionStatus> checkWithdrawal(WithdrawalService.WithdrawalId id) {
        Result result = new Result<>(TransactionStatus.PROCESSING, "Withdrawal in progress");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);                        
            WithdrawalService.WithdrawalState state = withdrawalService.getRequestState(id);
            if (state == WithdrawalService.WithdrawalState.COMPLETED) {
                withdrawalRepository.update(id, WithdrawalState.COMPLETED, "Withdrawal completed");
                result = new Result<>(TransactionStatus.COMPLETED, "Withdrawal completed");
            } else if (state == WithdrawalService.WithdrawalState.FAILED) {
                withdrawalRepository.update(id, WithdrawalState.FAILED, "Withdrawal failed");
                result = new Result<>(TransactionStatus.FAILED, "Withdrawal failed");
            }
            connection.commit();            
        } catch (SQLException e) {            
            logger.error("Failed to check withdrawal request: {}", id, e);
        }
        return result;
    }
}
