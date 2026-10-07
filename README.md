# Payment & Ledger Service

A backend payment processing service built with **Java 21, Spring Boot, PostgreSQL, and Spring Data JPA**. The project focuses on reliable money movement, idempotent payment processing, double-entry ledger accounting, database-level concurrency control, and ledger consistency verification.

It is designed around problems commonly found in payment systems rather than a simple CRUD implementation.

## Key Features

* **Idempotent Payments**

  * Prevents duplicate transactions when the same idempotency key is retried.
  * Stores a SHA-256 fingerprint of the original request.
  * Rejects reuse of an idempotency key with different payment details.

* **Payment State Management**

  * `PENDING → PROCESSING → SUCCESS`
  * Failed transactions are recorded as `FAILED`.
  * Concurrent requests cannot simultaneously claim the same pending payment.

* **Double-Entry Ledger**

  * Every successful transfer creates exactly two immutable ledger entries:

    * `DEBIT` from the sender
    * `CREDIT` to the receiver
  * The ledger maintains a zero-sum invariant across all accounts.

* **Concurrency Control**

  * Uses PostgreSQL row-level pessimistic locking through JPA.
  * Locks both accounts before modifying balances.
  * Accounts are always locked in ascending ID order to reduce deadlock risk.
  * Uses optimistic versioning with `@Version` on payment and account entities.

* **Atomic Money Transfer**

  * Balance updates and ledger entries execute within the same database transaction.
  * A failed transfer does not leave partially updated balances or ledger records.

* **Ledger Verification**

  * Compares cached account balances against calculated ledger balances.
  * Verifies that the complete ledger has a net balance of zero.
  * Reports accounts whose cached balance does not match their ledger balance.

* **System Account**

  * Creates a system account automatically when the application starts.
  * New users receive an initial system-funded balance through the same ledger mechanism.

## Architecture

```text
Client
  |
  v
REST Controllers
  |
  +-------------------+
  |                   |
  v                   v
PaymentService    UserService
  |
  v
LedgerService
  |
  +--------------------------+
  |                          |
  v                          v
AccountRepository      LedgerEntryRepository
  |
  v
PostgreSQL
```

### Payment Flow

```text
Payment Request
      |
      v
Validate Request
      |
      v
Generate Request Fingerprint
      |
      v
Find/Create Payment using Idempotency Key
      |
      v
Validate Idempotency Fingerprint
      |
      v
PENDING -> PROCESSING
      |
      v
Lock Sender & Receiver Accounts
      |
      v
Validate Balance
      |
      v
Update Account Balances
      |
      +----------------------+
      |                      |
      v                      v
  DEBIT Entry          CREDIT Entry
      |                      |
      +----------+-----------+
                 |
                 v
             SUCCESS
```

## Concurrency Handling

The service is designed to handle concurrent payment requests safely.

When transferring money, both account rows are locked using a pessimistic database lock:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

The accounts are acquired in ascending account ID order:

```text
Transfer A → B
Transfer B → A

Both operations acquire:
min(A, B) → max(A, B)
```

This gives concurrent transfers a deterministic lock order and reduces the possibility of circular lock acquisition.

For payment processing, the service performs an atomic state transition:

```text
PENDING → PROCESSING
```

Only one concurrent request can successfully perform this transition. Other requests using the same idempotency key observe the existing payment state instead of processing the payment again.

## Double-Entry Ledger

Each successful payment creates two immutable entries.

For a transfer of `₹500` from Account `1` to Account `2`:

```text
Account 1    DEBIT   ₹500
Account 2    CREDIT  ₹500
```

The ledger therefore satisfies:

```text
Total Credits - Total Debits = 0
```

Ledger entries are marked immutable and cannot be updated after creation.

The service also maintains a cached account balance and can independently calculate the balance from ledger entries:

```text
Cached Balance
      |
      | compare
      v
Ledger Balance
```

This provides a mechanism for detecting accounting inconsistencies.

## Payment State Machine

```text
                 +-------------+
                 |   PENDING   |
                 +------+------+
                        |
                        v
                 +-------------+
                 | PROCESSING  |
                 +------+------+
                        |
              +---------+---------+
              |                   |
              v                   v
        +-----------+       +-----------+
        |  SUCCESS  |       |  FAILED   |
        +-----------+       +-----------+
```

Supported statuses:

* `PENDING`
* `PROCESSING`
* `SUCCESS`
* `FAILED`
* `REFUNDED`

## Idempotency

Clients provide an idempotency key with every payment request.

Example:

```json
{
  "senderAccount": 1,
  "receiverAccount": 2,
  "amount": 500.00,
  "idempotencyKey": "payment-abc-123"
}
```

The service stores:

```text
idempotencyKey
requestHash
payment status
```

The request fingerprint is generated from:

```text
senderAccount | receiverAccount | amount
```

and hashed using SHA-256.

If the same key is reused with different payment details, the API returns:

```text
409 CONFLICT
```

This prevents accidental reuse of an idempotency key for a different transaction.

## API Endpoints

### Create User

```http
POST /api/v1/user
```

Example:

```json
{
  "name": "John Doe",
  "email": "john@example.com"
}
```

A user account is created and funded with the configured signup bonus through the system account.

### Create Payment

```http
POST /api/v1/payment
```

Example:

```json
{
  "senderAccount": 1,
  "receiverAccount": 2,
  "amount": 250.00,
  "idempotencyKey": "txn-123456"
}
```

Possible responses include:

```text
200 OK
202 ACCEPTED
400 BAD REQUEST
409 CONFLICT
```

`202 ACCEPTED` is returned when the payment is still being processed.

### Get Account Balance

```http
GET /api/v1/ledger/accounts/{id}
```

Example response:

```json
{
  "accountId": 1,
  "cachedBalance": 1750.00,
  "ledgerBalance": 1750.00,
  "consistent": true
}
```

### Verify Ledger

```http
GET /api/v1/ledger/verify
```

Example response:

```json
{
  "totalNet": 0.00,
  "zeroSum": true,
  "mismatchedAccountIds": []
}
```

## Database Model

```text
users
  |
  | 1:1
  v
accounts
  |
  +----------------------+
  |                      |
  |                      |
  v                      v
payments           ledger_entries
```

### Users

Stores application users.

### Accounts

Stores:

* Account ID
* User ID
* Current balance
* Account type
* Version

### Payments

Stores:

* Source account
* Destination account
* Amount
* Payment status
* Idempotency key
* Request hash
* Timestamps
* Version

The idempotency key has a unique database constraint.

### Ledger Entries

Stores:

* Payment ID
* Account ID
* Entry direction
* Amount
* Creation timestamp

Ledger entries are immutable after creation.

## Tech Stack

| Technology      | Purpose                         |
| --------------- | ------------------------------- |
| Java 21         | Application development         |
| Spring Boot     | Backend framework               |
| Spring Web MVC  | REST APIs                       |
| Spring Data JPA | Persistence layer               |
| Hibernate       | ORM                             |
| PostgreSQL      | Relational database             |
| Maven           | Build and dependency management |
| JUnit           | Testing                         |

## Project Structure

```text
src/main/java/dev/prathamesh
├── config
│   └── SystemAccountInitializer.java
│
├── controller
│   ├── LedgerController.java
│   ├── PaymentController.java
│   └── UserController.java
│
├── exception
│   ├── GlobalException.java
│   └── IdempotencyConflictException.java
│
├── model
│   ├── AccountModel.java
│   ├── LedgerEntryModel.java
│   ├── PaymentModel.java
│   └── UserModel.java
│
├── repository
│   ├── AccountRepository.java
│   ├── LedgerEntryRepository.java
│   ├── PaymentRepository.java
│   └── UserRepository.java
│
├── service
│   ├── LedgerService.java
│   ├── PaymentService.java
│   └── UserService.java
│
└── types
    ├── AccountBalanceResponse.java
    ├── AccountType.java
    ├── EntryDirection.java
    ├── LedgerVerifyResponse.java
    ├── PaymentRequest.java
    ├── PaymentResponse.java
    └── PaymentStatus.java
```

## Getting Started

### Prerequisites

* Java 21
* Maven
* PostgreSQL

### Configure Database

Set the PostgreSQL connection URL through the `DATABASE_URL` environment variable.

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/payment_ledger"
```

For Windows:

```cmd
set DATABASE_URL=jdbc:postgresql://localhost:5432/payment_ledger
```

The application uses:

```properties
spring.datasource.url=${DATABASE_URL}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

### Run the Application

Using Maven:

```bash
./mvnw spring-boot:run
```

On Windows:

```cmd
mvnw.cmd spring-boot:run
```

The application will start with the default Spring Boot configuration.

## Testing

Run the test suite with:

```bash
./mvnw test
```

## Design Decisions

### Why Idempotency?

Network failures can cause clients to retry a payment request after the server has already processed it. Without idempotency, the same payment could potentially be executed multiple times.

The idempotency key makes repeated requests refer to the same payment operation.

### Why Double-Entry Ledger?

Account balances alone do not provide an independent accounting trail.

Every transfer records both sides of the transaction, allowing the system to independently calculate balances and verify that:

```text
Total Debits = Total Credits
```

### Why Database-Level Locking?

Application-level synchronization only protects threads inside a single application instance.

Database row-level locking provides coordination between concurrent transactions and remains effective when multiple application instances access the same PostgreSQL database.

### Why Lock Accounts in a Fixed Order?

Two concurrent transfers can otherwise acquire locks in opposite orders:

```text
Transaction A: Lock Account 1 → waits for Account 2
Transaction B: Lock Account 2 → waits for Account 1
```

This creates a potential deadlock.

Using a deterministic ordering:

```text
Lock lower ID → Lock higher ID
```

reduces this risk.

### Why Keep Both Balance and Ledger?

The account balance provides an efficient value for normal reads, while the ledger provides an independent source for verification.

This allows the service to detect inconsistencies instead of relying on a single representation of account state.

## Future Improvements

Potential extensions include:

* Authentication and authorization
* Payment retry workflow for failed transactions
* Refund processing
* Pagination for transaction history
* Stronger integration and concurrency tests
* Database migration management with Flyway
* Observability with metrics and structured logging
* Rate limiting and API security
* Distributed deployment and failure recovery
