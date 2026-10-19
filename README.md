# SpendWise — Personal Expense Tracker

SpendWise is a full-stack personal expense tracking web application built to help users manage their income, expenses, and monthly budgets through a clean dashboard.

## Features

- **User Authentication** — Sign up, log in, and log out securely.
- **Expense Management** — Add, view, update, and delete transactions.
- **Dashboard** — View financial summaries and spending trends.
- **Budget Management** — Set monthly budgets and category-wise budgets.
- **Visual Analytics** — Explore spending patterns through charts.
- **CSV Export** — Export transaction records.
- **Responsive Interface** — Use the application across desktop and mobile screen sizes.

## Tech Stack

| Layer | Technologies |
|---|---|
| Frontend | HTML, CSS, JavaScript |
| Charts | Chart.js |
| Backend | Java, Jakarta Servlets |
| Database | MySQL |
| Database Connectivity | JDBC |
| Build Tool | Maven |
| Server | Apache Tomcat 11 |

## Project Structure

```text
SpendWise/
├── src/
│   └── main/
│       ├── java/com/spendwise/
│       └── webapp/
│           ├── WEB-INF/
│           ├── css/
│           ├── js/
│           ├── index.html
│           └── login.html
├── pom.xml
├── test-transaction.http
└── README.md
```

## Prerequisites

- JDK 17
- Apache Tomcat 11
- MySQL Server
- IntelliJ IDEA or another Java IDE
- Maven support

## Setup

1. Clone this repository.
2. Open the project in IntelliJ IDEA.
3. Create the required MySQL database and tables using the project's database schema.
4. Configure the `DB_USER` and `DB_PASSWORD` environment variables with your MySQL credentials.
5. Build the project as a WAR using Maven.
6. Deploy the WAR to Apache Tomcat 11.
7. Open the application in your browser at `http://localhost:8080/SpendWise`.

**Note:** The database schema SQL script is not currently included in this repository. Prepare the schema before running the application on a new machine.

## Security

- Passwords are handled using BCrypt hashing.
- Database credentials should be supplied through environment variables.
- Never commit passwords, access tokens, or other secrets to version control.

## Future Improvements

- Add automated tests.
- Improve deployment and hosting.
- Add more detailed financial reports.
- Add demo screenshots and a walkthrough.

## Author

Developed as a personal full-stack web development project.
