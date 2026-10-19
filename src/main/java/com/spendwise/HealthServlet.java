package com.spendwise;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/health")
public class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");

        try (Connection connection = DBConnection.getConnection()) {

            if (connection.isValid(3)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println(
                        "SpendWise backend is running!"
                );
                response.getWriter().println(
                        "MySQL connection successful!"
                );
            } else {
                response.sendError(
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "MySQL connection is not valid."
                );
            }

        } catch (SQLException e) {
            getServletContext().log(
                    "SpendWise database connection failed.", e
            );
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database connection failed. Check Tomcat logs."
            );
        }
    }
}

