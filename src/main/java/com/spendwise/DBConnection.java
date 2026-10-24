package com.spendwise;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL = System.getenv()
            .getOrDefault(
                    "DB_URL",
                    "jdbc:mysql://localhost:3306/spendwise_db"
            );

    private static final String USER =
            System.getenv("DB_USER") == null
                    ? null : System.getenv("DB_USER").trim();

    private static final String PASSWORD =
            System.getenv("DB_PASSWORD") == null
                    ? null : System.getenv("DB_PASSWORD");

    private static void diagnoseConnection() {
        try {
            String address = URL.substring("jdbc:mysql://".length());
            URI uri = URI.create("mysql://" + address);

            String host = uri.getHost();
            int port = uri.getPort();

            System.out.println("DB host configured: " + (host != null));
            System.out.println("DB port configured: " + port);

            InetAddress[] addresses = InetAddress.getAllByName(host);
            System.out.println("DNS resolution successful. Addresses found: "
                    + addresses.length);

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                System.out.println("TCP connection successful!");
            }

        } catch (Exception e) {
            System.err.println("DB NETWORK DIAGNOSTIC FAILED: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {

        System.out.println("DB_USER configured: "
                + (USER != null && !USER.isBlank()));

        System.out.println("DB_PASSWORD configured: "
                + (PASSWORD != null && !PASSWORD.isBlank()));

        diagnoseConnection();

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
