# Library Management System

A complete Java 17 desktop application for managing a small school library. It uses Swing for the interface and human-readable CSV files for storage, so no database server or third-party runtime library is required.

## Features

- Dashboard with member, title, available-copy, active-loan, and overdue totals.
- A single startup screen with member access and password-protected librarian access.
- Member self-registration with automatically generated `U001`-style membership IDs.
- Phone-verified self-service borrowing with system-controlled 14-day due dates.
- A dedicated member-only kiosk window that does not expose administrative screens.
- Register and update active or inactive library members.
- Search members by ID, name, email, or phone.
- Add and update books, authors, categories, and inventory quantities.
- Search the catalogue by ID, title, author, or category.
- Borrow books using friendly member and book selectors.
- Return books and calculate overdue fines at **$0.50 per late day**.
- Automatically create and open a PDF fine invoice when an overdue book is returned.
- Reopen or regenerate fine invoices from the circulation history.
- View active loans or complete circulation history with member names and book titles.
- Sort all management tables by clicking a column heading.
- Save every successful change automatically to UTF-8 CSV files.
- Safely quote commas, quotation marks, newlines, and Khmer text in CSV values.

## Requirements

- JDK 17 or newer
- macOS, Windows, or Linux with a graphical desktop

The project has no external runtime dependencies.

## Build and run

From this folder:

```bash
./build.sh
./run.sh
```

This opens one application with two roles:

- **Library Member** opens the registration and borrowing portal.
- **Administrator Login** opens the librarian dashboard after authentication.

For the classroom demo, the default librarian credentials are:

```text
Username: admin
Password: admin123
```

The admin sidebar includes **Member Portal** and **Log Out**, so the complete demonstration stays inside one window and only one `Main` class needs to be run.

To skip the role screen and start in dedicated kiosk mode on a member-facing computer:

```bash
./run.sh --self-service
```

The convenience launcher does the same thing:

```bash
./self-service.sh
```

Use the separate self-service mode on a member-facing computer. It intentionally does not expose member records, inventory editing, returns, or other administrative screens.
The kiosk masks phone verification, requires explicit loan confirmation, and automatically clears an inactive session.

The build creates:

```text
dist/LibraryManagementSystem.jar
dist/data/
```

`run.sh` runs from `dist`, so the packaged application uses `dist/data` and does not alter the source sample data.
Once created, `dist/data` is preserved by later builds and test runs; rebuilding never replaces live library records with the samples.
Fine invoices are saved under `dist/data/invoices/` when the packaged application is used.

To use a different data folder:

```bash
./run.sh --data-dir=/absolute/path/to/library-data
```

To verify startup without opening the interface:

```bash
./run.sh --smoke-test
```

## Open in IntelliJ IDEA

1. Open this project folder.
2. Select JDK 17 or newer as the Project SDK.
3. Run `Main.java`.
4. Choose **Library Member** or sign in as the librarian from the role-selection screen.

The Maven file is included for IDE import. The shell scripts remain the simplest way to build, test, and create the executable JAR.

## Test

```bash
./test.sh
```

The dependency-free test suite covers:

- Fine calculations and date boundaries
- CSV quoting, Unicode, reloads, and malformed input
- Member and book validation
- Self-registration, generated membership IDs, duplicate contact checks, and phone verification
- Self-service borrowing with a fixed 14-day term
- Coordination between simultaneously open admin and self-service applications
- Kiosk confirmation and session cleanup
- Borrowing, returning, inventory, and duplicate-loan rules
- PDF fine invoice creation, stable filenames, and invoice eligibility rules
- Persistence and aggregate consistency
- Startup smoke mode
- A headless end-to-end Swing workflow using real fields and buttons

## CSV storage

The application creates missing files with these exact schemas:

```text
users.csv: userId,name,email,phone,status
books.csv: bookId,title,author,category,quantity,available
loans.csv: loanId,userId,bookId,borrowDate,dueDate,returnDate,fineAmount
```

Dates use ISO format (`YYYY-MM-DD`). A blank return date means the loan is active. Writes use a temporary file followed by atomic replacement where the operating system supports it.
The application uses `data/.library.lock` plus reload-before-mutation so an admin window and a self-service kiosk can safely share the same CSV folder.

## Main business rules

- Member and book IDs are unique without regard to letter case.
- Self-registration requires a name, valid email, and 8–15 digit phone number.
- A phone number or email already used for self-registration cannot create another membership.
- Self-registered members are active and receive the next available `U`-number.
- Self-service borrowing matches the membership ID and registered phone number.
- Self-service borrow and due dates are set by the system; the standard loan term is 14 days.
- Only active members can borrow.
- A member cannot have two active loans for the same book.
- Due dates cannot be earlier than the borrowing date.
- Book quantity cannot fall below the number of borrowed copies.
- Returning a loan restores exactly one available copy.
- The due date itself has no fine; each day after it costs $0.50.
- A returned loan generates a PDF fine invoice only when its final fine is greater than $0.00.
- CSV data is checked for duplicate IDs, missing references, and inventory inconsistencies during startup.

Phone matching ignores common separators such as spaces, hyphens, and parentheses. This is lightweight identity verification for a local school project, not SMS/OTP authentication.

## Project layout

```text
src/main/java/com/istad/library/
  model/      Domain objects
  storage/    CSV repositories and parser
  service/    Business rules and transactions
  ui/         Swing application screens
src/test/java/com/istad/library/
data/         Sample CSV data
```
