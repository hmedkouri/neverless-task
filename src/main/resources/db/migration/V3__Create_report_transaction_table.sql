CREATE TABLE IF NOT EXISTS report_transaction (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id uuid NOT NULL,
    user_id varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    amount decimal(19, 2) NOT NULL,
    message varchar(255),
    created_at timestamp default current_timestamp
);

CREATE INDEX IF NOT EXISTS idx_report_transaction_transaction_id ON report_transaction(transaction_id);