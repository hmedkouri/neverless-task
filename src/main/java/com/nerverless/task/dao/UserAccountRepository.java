package com.nerverless.task.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nerverless.task.model.UserAccount;

public class UserAccountRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserAccountRepository.class);

    private final DataSource dataSource;

    public UserAccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // CRUD operations for UserAccount
    public List<UserAccount> findAll() {
        List<UserAccount> users = new ArrayList<>();
        String sql = "SELECT name, balance, reserve FROM user_account";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                var user = new UserAccount(resultSet.getString("name"), resultSet.getBigDecimal("balance"), resultSet.getBigDecimal("reserve"));                
                users.add(user);
            }
        } catch (SQLException e) {
            logger.error("Failed to get all users", e);
        }
        return users;
    }

    // Find user account by Name
    public Optional<UserAccount> findByName(String name) {
        String sql = "SELECT name, balance, reserve FROM user_account WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return Optional.of(new UserAccount(resultSet.getString("name"), resultSet.getBigDecimal("balance"), resultSet.getBigDecimal("reserve")));
            }
        } catch (SQLException e) {
            logger.error("Failed to get user by name", e);
        }
        return Optional.empty();
    }

    // Save user account
    public void save(UserAccount user) {
        String sql = "REPLACE INTO user_account (name, balance, reserve) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.name());
            statement.setBigDecimal(2, user.balance());
            statement.setBigDecimal(3, user.reserve());
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save user account", e);
        }
    }
}

