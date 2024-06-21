package com.nerverless.task;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

import com.nerverless.task.dao.DatabaseConfig;
import com.nerverless.task.model.Report;
import com.nerverless.task.model.Transaction;
import com.nerverless.task.model.Transaction.Transfer;
import com.nerverless.task.model.Transaction.WithdrawalRequest;
import com.nerverless.task.model.TransactionId;
import com.nerverless.task.model.Withdrawal;
import com.nerverless.task.service.ReportService;
import com.nerverless.task.service.WithdrawalService;
import com.nerverless.task.service.WithdrawalServiceStub;
import com.nerverless.task.workers.TransactionWorker;
import com.nerverless.task.workers.WithdrawalWorker;

import io.javalin.Javalin;

public class Application {

    private static final String DB_URL = "jdbc:sqlite:neverless-task.db";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {

        // Initialize connection pool
        DataSource dataSource = DatabaseConfig.createDataSource(DB_URL, 3);

        // Initialize the database schema using Flyway
        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.migrate();

        BlockingQueue<Transaction> transactionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Report> transactionReportQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Transaction> withdrawalQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Withdrawal> withdrawalReportQueue = new LinkedBlockingQueue<>();

        TransactionWorker transactionWorker = buildTransactionWorker(dataSource, transactionQueue, transactionReportQueue, withdrawalQueue, withdrawalReportQueue);

        WithdrawalService withdrawalService = new WithdrawalServiceStub();
        WithdrawalWorker withdrawalWorker = buildWithdrawalWorker(dataSource, withdrawalService, withdrawalQueue, withdrawalReportQueue);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        executorService.execute(transactionWorker);
        executorService.execute(withdrawalWorker);

        ReportService reportService = new ReportService(dataSource);
        
        // Setup Javalin
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/public";
            });
        }).start(7000);

        // Endpoint to transfer money
        app.post("/transfer", ctx -> {
            try {
                String fromUser = ctx.formParam("fromUser");
                String toUser = ctx.formParam("toUser");
                BigDecimal amount = new BigDecimal(ctx.formParam("amount"));

                Transfer transfer = new Transfer(new TransactionId(UUID.randomUUID(), fromUser), fromUser, toUser, amount);
                logger.info("Transaction initiated: {}", transfer);
                transactionQueue.put(transfer);

                ctx.json(String.format("{ 'transation_id':'%s', 'message':'Transfer initiated'}", transfer.transactionId().id()));
            } catch (IllegalArgumentException e) {
                ctx.status(422).result(String.format("Invalid request: %s", e.getMessage()));
            }
        });

        // Endpoint to withdraw money to an external account
        app.post("/withdraw", ctx -> {
            try {
                String fromUser = ctx.formParam("fromUser");
                String toAddress = ctx.formParam("toAddress");
                BigDecimal amount = new BigDecimal(ctx.formParam("amount"));

                WithdrawalRequest withdrawal = new WithdrawalRequest(new TransactionId(UUID.randomUUID(), fromUser), fromUser, toAddress, amount);
                logger.info("Withdrawal initiated: {}", withdrawal);
                transactionQueue.put(withdrawal);

                ctx.json(String.format("{ 'transation_id':'%s', 'message':'Withdrawal initiated'}", withdrawal.transactionId().id()));
            } catch (IllegalArgumentException e) {
                ctx.status(422).result(String.format("Invalid request: %s", e.getMessage()));
            }
        });

        // Endpoint to query transaction status
        app.get("/report", ctx -> {
            try {
                String userId = ctx.queryParam("userId");
                if (userId != null && !userId.isBlank()) {
                    ctx.json(reportService.getReportsByUser(userId));
                } else {
                    String transactionId = ctx.queryParam("transactionId");
                    if (transactionId != null && !transactionId.isBlank()) {
                        reportService.getLatestReportByTransaction(UUID.fromString(transactionId)).ifPresentOrElse(ctx::json, () -> ctx.status(422).result("Transaction not found"));
                    } else {
                        ctx.status(422).result("userId or transactionId is required");
                    }
                }
            } catch (IllegalArgumentException e) {
                ctx.status(422).result(String.format("Invalid request: %s", e.getMessage()));
            }
        });

        // Serve the HTML page
        app.get("/", ctx -> ctx.redirect("/index.html"));

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            transactionWorker.stop();
            executorService.shutdown();
            app.stop();
        }));
    }

    private static TransactionWorker buildTransactionWorker(DataSource dataSource,
        BlockingQueue<Transaction> transactionQueue, BlockingQueue<Report> transactionReportQueue,
        BlockingQueue<Transaction> withdrawalQueue, BlockingQueue<Withdrawal> withdrawalReportQueue) {
        return new TransactionWorker(dataSource, transactionQueue, transactionReportQueue, withdrawalQueue, withdrawalReportQueue);
    }

    private static WithdrawalWorker buildWithdrawalWorker(DataSource dataSource, WithdrawalService withdrawalService, 
        BlockingQueue<Transaction> withdrawalQueue, BlockingQueue<Withdrawal> withdrawalReportQueue) {
        return new WithdrawalWorker(dataSource, withdrawalService, withdrawalQueue, withdrawalReportQueue);   
    }
}
