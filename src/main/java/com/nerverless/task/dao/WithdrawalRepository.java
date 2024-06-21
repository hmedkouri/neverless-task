package com.nerverless.task.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.nerverless.task.model.TransactionId;
import com.nerverless.task.model.Withdrawal;
import com.nerverless.task.service.WithdrawalService.Address;
import com.nerverless.task.service.WithdrawalService.WithdrawalId;
import com.nerverless.task.service.WithdrawalService.WithdrawalState;

public class WithdrawalRepository {
    
    private final DataSource dataSource;

    public WithdrawalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Save withdrawal request
    public void save(WithdrawalId id, TransactionId transactionId, Address address, BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO withdrawal (withdrawal_id, transaction_id, user_id, address, amount) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value().toString());
            statement.setString(2, transactionId.id().toString());
            statement.setString(3, transactionId.userId());
            statement.setString(4, address.value());
            statement.setBigDecimal(5, amount);
            statement.executeUpdate();
        }
    }

    // Update withdrawal request
    public void update(WithdrawalId id, WithdrawalState state, String message) throws SQLException {
        String sql = "UPDATE withdrawal SET status = ?, message = ? WHERE withdrawal_id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, state.name());
            statement.setString(2, message);
            statement.setString(3, id.value().toString());
            statement.executeUpdate();
        }
    }

    // Find all processing withdrawal requests
    public List<Withdrawal> findProcessing() throws SQLException {
        List<Withdrawal> withdrawals = new ArrayList<>();
        String sql = "SELECT withdrawal_id, transaction_id, user_id, account_name, to_address, amount FROM withdrawal WHERE status = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, WithdrawalState.PROCESSING.name());
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                var withdrawalId = new WithdrawalId(UUID.fromString(resultSet.getString("withdrawal_id")));
                var transactionId = new TransactionId(UUID.fromString(resultSet.getString("transaction_id")), resultSet.getString("user_id"));
                var accountName = resultSet.getString("account_name");
                var address = resultSet.getString("address");
                var amount = resultSet.getBigDecimal("amount");
                var withdrawal = new Withdrawal(withdrawalId, transactionId, accountName, address, amount);
                withdrawals.add(withdrawal);
            }
        }
        return withdrawals;
    }
}