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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nerverless.task.model.TransactionId;
import com.nerverless.task.model.TransactionStatus;
import com.nerverless.task.model.Withdrawal;
import com.nerverless.task.service.WithdrawalService.WithdrawalId;

public class WithdrawalRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(WithdrawalRepository.class);

    private final DataSource dataSource;

    public WithdrawalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Save withdrawal request
    public void save(Withdrawal withdrawal) {
        String sql = "REPLACE INTO withdrawal (withdrawal_id, transaction_id, user_id, account_name, to_address, amount, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            statement.setString(1, withdrawal.withdrawalId().value().toString());
            statement.setString(2, withdrawal.transactionId().id().toString());
            statement.setString(3, withdrawal.transactionId().userId());
            statement.setString(4, withdrawal.accountName());
            statement.setString(5, withdrawal.toAddress());
            statement.setBigDecimal(6,  withdrawal.amount());
            statement.setString(7,  withdrawal.status().name());
            statement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {            
            logger.error("Failed to save withdrawal request", e);
        }
    }

    // Find all processing withdrawal requests
    public List<Withdrawal> findWithStatus(TransactionStatus status) {
        List<Withdrawal> withdrawals = new ArrayList<>();
        String sql = "SELECT withdrawal_id, transaction_id, user_id, account_name, to_address, amount FROM withdrawal WHERE status = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                var withdrawalId = new WithdrawalId(UUID.fromString(resultSet.getString("withdrawal_id")));
                var transactionId = new TransactionId(UUID.fromString(resultSet.getString("transaction_id")), resultSet.getString("user_id"));
                var accountName = resultSet.getString("account_name");
                var address = resultSet.getString("to_address");
                var amount = resultSet.getBigDecimal("amount");
                var withdrawal = new Withdrawal(withdrawalId, transactionId, accountName, address, amount, status);
                withdrawals.add(withdrawal);
            }
        } catch (SQLException e) {
            logger.error("Failed to find withdrawal requests with status", e);
        }
        return withdrawals;
    }

    // Find withdrawal by transaction id
    public Optional<Withdrawal> findByTransactionId(TransactionId transactionId) {
        String sql = "SELECT withdrawal_id, user_id, account_name, to_address, amount, status FROM withdrawal WHERE transaction_id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transactionId.id().toString());
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                var withdrawalId = new WithdrawalId(UUID.fromString(resultSet.getString("withdrawal_id")));
                var accountName = resultSet.getString("account_name");
                var address = resultSet.getString("to_address");
                var amount = resultSet.getBigDecimal("amount");
                var status = TransactionStatus.valueOf(resultSet.getString("status"));
                return Optional.of(new Withdrawal(withdrawalId, transactionId, accountName, address, amount, status));
            }
        } catch (SQLException e) {
            logger.error("Failed to find withdrawal request by transaction id {}", transactionId, e);
        }
        return Optional.empty();
    }
}