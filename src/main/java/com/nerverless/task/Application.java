package com.nerverless.task;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import org.flywaydb.core.Flyway;

import com.nerverless.task.model.Report;
import com.nerverless.task.model.Transaction;
import com.nerverless.task.model.Transaction.Transfer;
import com.nerverless.task.model.Transaction.WithdrawalRequest;
import com.nerverless.task.model.TransactionId;
import com.nerverless.task.service.WithdrawalService;
import com.nerverless.task.service.WithdrawalServiceStub;
import com.nerverless.task.workers.TransactionWorker;
import com.nerverless.task.workers.WithdrawalWorker;

import io.javalin.Javalin;

public class Application {

    private static final String DB_URL = "jdbc:sqlite:neverless-task.db";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        try {
            Connection connection = DriverManager.getConnection(DB_URL);

            // Initialize the database schema using Flyway
            Flyway flyway = Flyway.configure().dataSource(DB_URL, null, null).load();
            flyway.migrate();

            BlockingQueue<Transaction> transactionQueue = new LinkedBlockingQueue<>();
            BlockingQueue<Report> transactionReportQueue = new LinkedBlockingQueue<>();
            BlockingQueue<Transaction> withdrawalQueue = new LinkedBlockingQueue<>();
            BlockingQueue<Report> withdrawalReportQueue = new LinkedBlockingQueue<>();
            
            TransactionWorker transactionWorker = new TransactionWorker(connection, transactionQueue, transactionReportQueue, withdrawalQueue, withdrawalReportQueue);
            
            WithdrawalService withdrawalService = new WithdrawalServiceStub();
            WithdrawalWorker withdrawalWorker = new WithdrawalWorker(withdrawalService, connection, withdrawalQueue, withdrawalReportQueue);

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            executorService.execute(transactionWorker);
            executorService.execute(withdrawalWorker);

            // Setup Javalin
            Javalin app = Javalin.create(config -> {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.directory = "/public";
                });
            }).start(7000);

            // Endpoint to transfer money
            app.post("/transfer", ctx -> {
                String fromUser = ctx.formParam("fromUser");
                String toUser = ctx.formParam("toUser");
                BigDecimal amount = new BigDecimal(ctx.formParam("amount"));

                Transfer transfer = new Transfer(new TransactionId(UUID.randomUUID(), fromUser), fromUser, toUser, amount);
                logger.info("Transaction initiated: {}", transfer);
                transactionQueue.put(transfer);

                ctx.result("Transaction initiated");
            });

            // Endpoint to withdraw money to an external account
            app.post("/withdraw", ctx -> {
                String fromUser = ctx.formParam("fromUser");
                String toAddress = ctx.formParam("toAddress");
                BigDecimal amount = new BigDecimal(ctx.formParam("amount"));

                WithdrawalRequest withdrawal = new WithdrawalRequest(new TransactionId(UUID.randomUUID(), fromUser), fromUser, toAddress, amount);
                logger.info("Withdrawal initiated: {}", withdrawal);
                transactionQueue.put(withdrawal);

                ctx.result("Transaction initiated");
            });

            // Endpoint to query transaction status
            app.get("/report", ctx -> {
                String userId = ctx.queryParam("userId");

                logger.info("Querying report for user: {}", userId);

                Report report = transactionReportQueue.take();
                if (report.transactionId().userId().equals(userId)) {
                    ctx.json(report);
                } else {
                    ctx.status(404).result("No report found for user: " + userId);
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

        } catch (SQLException e) {
            logger.error("Failed to connect to the database", e);
        }
    }
}
