package com.nerverless.task.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.nerverless.task.model.UserAccount;

public class UserAccountRepository {
    private final DataSource dataSource;

    public UserAccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // CRUD operations for UserAccount
    public List<UserAccount> findAll() throws SQLException {
        List<UserAccount> users = new ArrayList<>();
        String sql = "SELECT name, balance, reserve FROM user_account";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                var user = new UserAccount(resultSet.getString("name"), resultSet.getBigDecimal("balance"), resultSet.getBigDecimal("reserve"));                
                users.add(user);
            }
        }
        return users;
    }

    // Find user account by Name
    public UserAccount findByName(String name) throws SQLException {
        String sql = "SELECT name, balance, reserve FROM user_account WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return new UserAccount(resultSet.getString("name"), resultSet.getBigDecimal("balance"), resultSet.getBigDecimal("reserve"));
            } else {
                throw new SQLException("User not found");
            }
        }
    }

    // Save user account
    public void save(UserAccount user) throws SQLException {
        String sql = "INSERT INTO user_account (name, balance, reserve) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.name());
            statement.setBigDecimal(2, user.balance());
            statement.setBigDecimal(3, user.reserve());
            statement.executeUpdate();
        }
    }

    // Update user account
    public void update(UserAccount user) throws SQLException {
        String sql = "UPDATE user_account SET balance = ?, reserve = ? WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {            
            statement.setBigDecimal(1, user.balance());
            statement.setBigDecimal(2, user.reserve());
            statement.setString(3, user.name());
            statement.executeUpdate();
        }
    }
}

