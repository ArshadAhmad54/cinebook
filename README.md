# CineBook | Movie Ticket Booking System

CineBook is a Java web application for browsing movies, choosing showtimes,
selecting seats, and managing ticket bookings.

## Features

- Create an account and sign in
- Browse movies and showtimes
- View seat availability and book tickets
- View booking history and cancel bookings
- Manage movie and booking information through the admin interface

## Tech Stack

| Component | Technology |
| --- | --- |
| Frontend | HTML, CSS, JavaScript |
| Backend | Java Servlets, JDBC |
| Database | MySQL |
| Application server | Apache Tomcat |
| IDE | Eclipse |

## Project Structure

| Path | Contents |
| --- | --- |
| `src/main/java/` | Java servlets and database connection code |
| `src/main/webapp/` | Web interface and `WEB-INF/web.xml` |
| `database/schema.sql` | MySQL database and table definitions |

## Run Locally

1. Clone the repository and import it into Eclipse as an existing project.
2. Run `database/schema.sql` in MySQL Workbench on a fresh setup. The script
   creates the `cinebook` database and its tables. It contains only the schema,
   so add movies and showtimes to try the booking flow.
3. Add MySQL Connector/J to `src/main/webapp/WEB-INF/lib/` if the driver is missing.
4. In your Tomcat launch configuration, set `CINEBOOK_DB_PASSWORD` to the
   password for the MySQL account configured in `DBConnection.java`. Keep the
   password in your local configuration.
5. In Eclipse, right-click the CineBook project and choose
   **Run As → Run on Server**. Select your Tomcat server to open CineBook.
