package com.nerverless.task.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.nerverless.task.model.Report;
import com.nerverless.task.model.TransactionId;
import com.nerverless.task.model.TransactionStatus;

public class ReportTransactionRepository {

    private final DataSource dataSource;

    public ReportTransactionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Find all report report by transactionId ordered by id and created_at desc
    public List<Report> findByTransactionId(UUID transactionId) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT transaction_id, user_id, status, amount, message FROM report_transaction WHERE transaction_id = ? ORDER BY created_at, id DESC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transactionId.toString());
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                var report = new Report(
                    new TransactionId(UUID.fromString(resultSet.getString("transaction_id")), resultSet.getString("user_id")), 
                    resultSet.getBigDecimal("amount"), 
                    TransactionStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("message"));
                reports.add(report);
            }
        }
        return reports;
    }

    // Find last report by transactionId if exists
    public Optional<Report> findLastByTransactionId(UUID transactionId) throws SQLException {
        String sql = "SELECT transaction_id, user_id, status, amount, message FROM report_transaction WHERE transaction_id = ? ORDER BY created_at, id DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transactionId.toString());
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                var report = new Report(
                    new TransactionId(UUID.fromString(resultSet.getString("transaction_id")), resultSet.getString("user_id")), 
                    resultSet.getBigDecimal("amount"), 
                    TransactionStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("message"));
                return Optional.of(report);
            }
        }
        return Optional.empty();
    }

    // Insert report transaction
    public void insert(Report report) throws SQLException {
        String sql = "INSERT INTO report_transaction (transaction_id, user_id, status, amount, message) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, report.transactionId().id().toString());
            statement.setString(2, report.transactionId().userId());
            statement.setString(3, report.status().name());
            statement.setBigDecimal(4, report.amount());
            statement.setString(5, report.message());
            statement.executeUpdate();
        }
    }

}