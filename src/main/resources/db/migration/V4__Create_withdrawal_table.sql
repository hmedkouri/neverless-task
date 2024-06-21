CREATE TABLE withdrawal (
    withdrawal_id uuid PRIMARY KEY,
    transaction_id uuid NOT NULL,
    user_id varchar(255) NOT NULL,
    account_name varchar(255) NOT NULL,
    to_address varchar(255) NOT NULL,
    status varchar(20) NOT NULL,
    amount decimal(19, 2) NOT NULL,
    message varchar(255),
    created_at timestamp default current_timestamp
);

CREATE INDEX withdrawal_status_idx ON withdrawal (status);
CREATE INDEX withdrawal_transaction_id_idx ON withdrawal (transaction_id);