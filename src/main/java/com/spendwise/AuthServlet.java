package com.spendwise;

import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {

    private void sendJson(
            HttpServletResponse response,
            int status,
            String message,
            boolean success) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObject json = new JsonObject();
        json.addProperty("success", success);
        json.addProperty("message", message);

        response.getWriter().write(json.toString());
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            sendJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Not logged in.", false);
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObject json = new JsonObject();
        json.addProperty("success", true);
        json.addProperty("userId", session.getAttribute("userId").toString());
        json.addProperty("fullName",
                (String) session.getAttribute("fullName"));
        json.addProperty("email",
                (String) session.getAttribute("email"));

        response.getWriter().write(json.toString());
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        String action = request.getParameter("action");

        if (action == null) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Action is required.", false);
            return;
        }

        switch (action.toLowerCase(Locale.ROOT)) {
            case "signup" -> signup(request, response);
            case "login" -> login(request, response);
            case "logout" -> logout(request, response);
            default -> sendJson(response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid action.", false);
        }
    }

    private void signup(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (fullName == null || email == null || password == null) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Please fill in all fields.", false);
            return;
        }

        fullName = fullName.trim();
        email = email.trim().toLowerCase(Locale.ROOT);

        if (fullName.isBlank() || fullName.length() > 100
                || email.isBlank() || email.length() > 150
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || password.length() < 8 || password.length() > 72) {

            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Enter a valid name and email. Password must be 8–72 characters.",
                    false);
            return;
        }

        String sql = """
                INSERT INTO users (full_name, email, password_hash)
                VALUES (?, ?, ?)
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, PasswordUtil.hashPassword(password));

            ps.executeUpdate();

            // Automatically log in the newly registered user.
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    sendJson(response,
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "Account created, but login could not be started. Please log in.",
                            false);
                    return;
                }

                long userId = keys.getLong(1);
                createUserSession(request, userId, fullName, email);
            }

            sendJson(response, HttpServletResponse.SC_CREATED,
                    "Account created successfully.", true);

        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                sendJson(response, HttpServletResponse.SC_CONFLICT,
                        "An account with this email already exists.", false);
            } else {
                getServletContext().log("Signup failed", e);
                sendJson(response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could not create account. Please try again.", false);
            }
        }
    }

    private void login(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null || password == null
                || email.isBlank() || password.isEmpty()) {
            sendJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Email and password are required.", false);
            return;
        }

        email = email.trim().toLowerCase(Locale.ROOT);

        String sql = """
                SELECT id, full_name, email, password_hash
                FROM users
                WHERE email = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()
                        || !PasswordUtil.verifyPassword(
                        password, rs.getString("password_hash"))) {

                    sendJson(response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Invalid email or password.", false);
                    return;
                }

                long userId = rs.getLong("id");
                String fullName = rs.getString("full_name");
                String savedEmail = rs.getString("email");

                createUserSession(
                        request, userId, fullName, savedEmail);
            }

            sendJson(response, HttpServletResponse.SC_OK,
                    "Login successful.", true);

        } catch (SQLException e) {
            getServletContext().log("Login failed", e);
            sendJson(response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Login failed. Please try again.", false);
        }
    }

    private void logout(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        sendJson(response, HttpServletResponse.SC_OK,
                "Logged out successfully.", true);
    }

    private void createUserSession(
            HttpServletRequest request,
            long userId,
            String fullName,
            String email) {

        // Prevent session fixation by replacing the old session.
        HttpSession oldSession = request.getSession(false);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("userId", userId);
        session.setAttribute("fullName", fullName);
        session.setAttribute("email", email);
        session.setMaxInactiveInterval(30 * 60);
    }
}
