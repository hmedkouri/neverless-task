package com.nerverless.task.workers;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nerverless.task.dao.WithdrawalRepository;
import com.nerverless.task.model.Result;
import com.nerverless.task.model.Transaction;
import com.nerverless.task.model.Transaction.WithdrawalRequest;
import com.nerverless.task.model.TransactionStatus;
import com.nerverless.task.model.Withdrawal;
import com.nerverless.task.service.WithdrawalService;

public class WithdrawalWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WithdrawalWorker.class);

    private final BlockingQueue<Transaction> withdrawalQueue;
    private final BlockingQueue<Withdrawal> withdrawalReportQueue;
    private final WithdrawalService withdrawalService;
    private final WithdrawalRepository withdrawalRepository;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public WithdrawalWorker(DataSource dataSource,
                            WithdrawalService withdrawalService,                            
                            BlockingQueue<Transaction> withdrawalQueue, 
                            BlockingQueue<Withdrawal> withdrawalReportQueue) {
        this.withdrawalQueue = withdrawalQueue;
        this.withdrawalReportQueue = withdrawalReportQueue;
        this.withdrawalService = withdrawalService;
        this.withdrawalRepository = new WithdrawalRepository(dataSource);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                Transaction message = withdrawalQueue.poll(1, TimeUnit.SECONDS);
                logger.trace("Processing message: {}", message);
                if (message == null) {
                    checkProcessingWithdrwals();
                } else if (message instanceof WithdrawalRequest withdrawal) {
                    request(withdrawal);
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

    private void request(WithdrawalRequest request) {
        WithdrawalService.WithdrawalId id = new WithdrawalService.WithdrawalId(request.transactionId().id());
        WithdrawalService.Address address = new WithdrawalService.Address(request.toAddress());
        withdrawalService.requestWithdrawal(id, address, request.amount());
        Withdrawal withdrawal = new Withdrawal(id, request.transactionId(), request.accountName(), request.toAddress(), request.amount(), TransactionStatus.PROCESSING);
        saveAndSend(withdrawal);
    }

    private void checkProcessingWithdrwals() {        
        withdrawalRepository.findWithStatus(TransactionStatus.PROCESSING).forEach( withdrawal -> {
            var result = checkWithdrawal(withdrawal);
            logger.trace("Checking withdrawal: withdrawalId={} - {}", withdrawal.withdrawalId(), result);
            if (result.value() != TransactionStatus.PROCESSING) {
                withdrawal = new Withdrawal(withdrawal.withdrawalId(), withdrawal.transactionId(), withdrawal.accountName(), withdrawal.toAddress(), withdrawal.amount(), result.value());
                saveAndSend(withdrawal);
            }
        });
    }

    private void saveAndSend(Withdrawal withdrawal) {
        withdrawalRepository.save(withdrawal);
        send(withdrawal);
    }

    private void send(Withdrawal withdrawal) {
        try {
            withdrawalReportQueue.put(withdrawal);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Result<TransactionStatus> checkWithdrawal(Withdrawal withdrwal) {
        try {             
            WithdrawalService.WithdrawalState state = withdrawalService.getRequestState(withdrwal.withdrawalId());
            if (null == state) {
                return new Result<>(TransactionStatus.PROCESSING, "Withdrawal in progress");
            } else return switch (state) {
                case COMPLETED -> new Result<>(TransactionStatus.COMPLETED, "Withdrawal completed");
                case FAILED -> new Result<>(TransactionStatus.FAILED, "Withdrawal failed");
                default -> new Result<>(TransactionStatus.PROCESSING, "Withdrawal in progress");
            };
        } catch (IllegalArgumentException e) {
            logger.error("Failed to request withdrawal state: transactionId={}, withdrawalId={}", withdrwal.transactionId(), withdrwal.withdrawalId(), e);
            var message = "Withdrawal failed: " + e.getMessage();
            return new Result<>(TransactionStatus.FAILED, message);
        }
    }
}
