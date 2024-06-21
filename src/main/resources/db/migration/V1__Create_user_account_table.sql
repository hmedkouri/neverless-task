CREATE TABLE IF NOT EXISTS user_account (
    name text primary key,
    balance decimal(19, 2) not null,
    reserve decimal(19, 2) not null
);
