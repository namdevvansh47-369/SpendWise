package com.spendwise;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/spendwise_db";

    private static final String USER =
            System.getenv("DB_USER") == null
                    ? null : System.getenv("DB_USER").trim();

    private static final String PASSWORD =
            System.getenv("DB_PASSWORD") == null
                    ? null : System.getenv("DB_PASSWORD");


    public static Connection getConnection() throws SQLException {

        System.out.println("DB_USER configured: "
                + (USER != null && !USER.isBlank()));

        System.out.println("DB_PASSWORD configured: "
                + (PASSWORD != null && !PASSWORD.isBlank()));

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL JDBC driver loaded!");

            Connection connection =
                    DriverManager.getConnection(URL, USER, PASSWORD);

            System.out.println("MySQL connection successful!");
            return connection;

        } catch (ClassNotFoundException e) {
            System.err.println("MYSQL DRIVER NOT FOUND:");
            e.printStackTrace();
            throw new SQLException("MySQL JDBC driver not found.", e);

        } catch (SQLException e) {
            System.err.println("MYSQL CONNECTION ERROR:");
            e.printStackTrace();
            throw e;
        }
    }


}
