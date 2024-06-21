# Neverless Home Task

This is a simple money transfer application built using core Java with Javalin for the RESTful API, SQLite for the embedded database, and HTMX for the frontend. The project includes a single-threaded worker to handle transaction messages and update the database inside a transaction.

## Features

- Transfer money between user accounts.
- Check transaction status.
- Asynchronous handling of transactions using a worker and queues.
- Simple frontend with HTMX for interactive forms.

## Requirements

- Java 17+

## Project Structure

- `src/main/java/com/example/hometask/Application.java`: Main application class to start the server.
- `src/main/java/com/example/hometask/worker/TransactionWorker.java`: Worker class to handle transactions.
- `src/main/java/com/example/hometask/repository/UserAccountRepository.java`: Repository class for user account operations.
- `src/main/java/com/example/hometask/model`: Model classes for transaction and report messages.
- `src/main/resources/db/migration`: Database migration scripts.
- `src/main/resources/public/index.html`: HTML page served by the Javalin app.

## Setup

### 1. Clone the Repository

```sh
git clone https://github.com/hmedkouri/nerverless-task.git
cd nerverless-task
```

### 2. Build the Project

```sh
./gradlew build
```

### 3. Run the Application

```sh
./gradlew run
```

The application will start on port 7000.

## Endpoints

### 1. Transfer Money
- **URL**: `/transfer`
- **Method**: `POST`
- **Request Parameters**:
  - `fromUser`: The user account to transfer money from.
  - `toUser`: The user account to transfer money to.
  - `amount`: The amount to transfer.

Example:

```sh
curl -X POST http://localhost:7000/transfer \
     -d "fromUser=Alice" \
     -d "toUser=Bob" \
     -d "amount=100.00"
```

### 2. Check Transaction Status
- **URL**: `/transaction/:id`
- **Method**: `GET`
- **Path Parameters**:
  - `id`: The transaction ID to check.

Example:
     
```sh
curl http://localhost:7000/transaction/1
```

## Frontend
Navigate to `http://localhost:7000` in your browser to access the HTML page with forms to initiate transfers and check transaction statuses. The forms use HTMX to make asynchronous requests to the REST endpoints and display the responses dynamically.

## Testing

Unit tests are provided for the repository classes. To run the tests, use:

```sh
./gradlew test
```

## Technologies Used

- Java 17.
- [Javalin](https://javalin.io/): A simple web framework for Java and Kotlin.
- [SQLite](https://www.sqlite.org/): An embedded SQL database engine.
- [HTMX](https://htmx.org/): A JavaScript library for AJAX, WebSockets, and server-sent events.
- Flyway: Database migration tool.
- Gradle: Build tool.