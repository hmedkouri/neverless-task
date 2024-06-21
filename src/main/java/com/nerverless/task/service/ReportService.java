package com.nerverless.task.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.nerverless.task.dao.ReportTransactionRepository;
import com.nerverless.task.model.Report;

/**
 * Report service implementation to retrieve reports on transactions and
 * withdrawals for a given user or a given transaction ID.
 */
public class ReportService {

    private final ReportTransactionRepository reportTransactionRepository;

    public ReportService(DataSource dataSource) {
        this.reportTransactionRepository = new ReportTransactionRepository(dataSource);
    }

    public List<Report> getReportsByUser(String userId) {
        return reportTransactionRepository.findByUserId(userId);
    }

    public List<Report> getReportsByTransaction(UUID transactionId) {
        return reportTransactionRepository.findByTransactionId(transactionId);
    }

    public Optional<Report> getLatestReportByTransaction(UUID transactionId) {
        return reportTransactionRepository.findLatestByTransactionId(transactionId);
    }
}
