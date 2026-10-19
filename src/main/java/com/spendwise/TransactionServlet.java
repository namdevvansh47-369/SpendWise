package com.spendwise;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/transactions")
public class TransactionServlet extends HttpServlet {

    private final Gson gson = new Gson();

    // Get the logged-in user's ID from the session.
    private Long getLoggedInUserId(HttpServletRequest request) {
        var session = request.getSession(false);

        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("userId");

        if (value == null) {
            return null;
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // GET: Fetch only the logged-in user's transactions.
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Long userId = getLoggedInUserId(request);

        if (userId == null) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in first."
            );
            return;
        }

        String sql = """
                SELECT id, description, amount, type,
                       category, transaction_date
                FROM transactions
                WHERE user_id = ?
                ORDER BY transaction_date DESC, id DESC
                """;

        List<Transaction> transactions = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction t = new Transaction();

                    t.setId(rs.getLong("id"));
                    t.setDescription(rs.getString("description"));
                    t.setAmount(rs.getBigDecimal("amount"));
                    t.setType(rs.getString("type"));
                    t.setCategory(rs.getString("category"));

                    Date transactionDate =
                            rs.getDate("transaction_date");

                    t.setTransaction_date(
                            transactionDate != null
                                    ? transactionDate.toString()
                                    : null
                    );

                    transactions.add(t);
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(transactions));

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not fetch transactions."
            );
        }
    }

    // POST: Create a transaction for the logged-in user.
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Long userId = getLoggedInUserId(request);

        if (userId == null) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in first."
            );
            return;
        }

        try {
            Transaction t = gson.fromJson(
                    request.getReader(),
                    Transaction.class
            );

            if (t == null
                    || t.getDescription() == null
                    || t.getDescription().isBlank()
                    || t.getDescription().length() > 100
                    || t.getAmount() == null
                    || t.getAmount().compareTo(BigDecimal.ZERO) <= 0
                    || t.getType() == null
                    || t.getCategory() == null
                    || t.getCategory().isBlank()
                    || t.getCategory().length() > 50
                    || t.getTransaction_date() == null
                    || t.getTransaction_date().isBlank()) {

                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Please provide valid transaction details."
                );
                return;
            }

            if (!t.getType().equals("INCOME")
                    && !t.getType().equals("EXPENSE")) {

                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Type must be INCOME or EXPENSE."
                );
                return;
            }

            Date transactionDate =
                    Date.valueOf(t.getTransaction_date());

            String sql = """
                    INSERT INTO transactions
                    (user_id, description, amount, type,
                     category, transaction_date)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         sql,
                         PreparedStatement.RETURN_GENERATED_KEYS
                 )) {

                ps.setLong(1, userId);
                ps.setString(2, t.getDescription().trim());
                ps.setBigDecimal(3, t.getAmount());
                ps.setString(4, t.getType());
                ps.setString(5, t.getCategory().trim());
                ps.setDate(6, transactionDate);

                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        t.setId(keys.getLong(1));
                    }
                }
            }

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(gson.toJson(t));

        } catch (IllegalArgumentException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid transaction date. Use YYYY-MM-DD."
            );

        } catch (JsonParseException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid JSON data."
            );

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Could not save transaction."
            );
        }
    }

    // PUT: Update only the logged-in user's transaction.
    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Long userId = getLoggedInUserId(request);

        if (userId == null) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in first."
            );
            return;
        }

        String idParam = request.getParameter("id");

        if (idParam == null || idParam.isBlank()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Transaction ID is required."
            );
            return;
        }

        try {
            long id = Long.parseLong(idParam);

            if (id <= 0) {
                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid transaction ID."
                );
                return;
            }

            Transaction t = gson.fromJson(
                    request.getReader(),
                    Transaction.class
            );

            if (t == null
                    || t.getDescription() == null
                    || t.getDescription().isBlank()
                    || t.getDescription().length() > 100
                    || t.getAmount() == null
                    || t.getAmount().compareTo(BigDecimal.ZERO) <= 0
                    || t.getType() == null
                    || (!t.getType().equals("INCOME")
                    && !t.getType().equals("EXPENSE"))
                    || t.getCategory() == null
                    || t.getCategory().isBlank()
                    || t.getCategory().length() > 50
                    || t.getTransaction_date() == null
                    || t.getTransaction_date().isBlank()) {

                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid transaction details."
                );
                return;
            }

            Date transactionDate =
                    Date.valueOf(t.getTransaction_date());

            String sql = """
                    UPDATE transactions
                    SET description = ?, amount = ?, type = ?,
                        category = ?, transaction_date = ?
                    WHERE id = ? AND user_id = ?
                    """;

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, t.getDescription().trim());
                ps.setBigDecimal(2, t.getAmount());
                ps.setString(3, t.getType());
                ps.setString(4, t.getCategory().trim());
                ps.setDate(5, transactionDate);
                ps.setLong(6, id);
                ps.setLong(7, userId);

                int updated = ps.executeUpdate();

                if (updated == 0) {
                    response.sendError(
                            HttpServletResponse.SC_NOT_FOUND,
                            "Transaction not found."
                    );
                    return;
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(
                    "{\"message\":\"Transaction updated successfully\"}"
            );

        } catch (IllegalArgumentException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid transaction ID or date. Use YYYY-MM-DD."
            );

        } catch (JsonParseException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid JSON data."
            );

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error while updating transaction."
            );
        }
    }

    // DELETE: Delete only the logged-in user's transaction.
    @Override
    protected void doDelete(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Long userId = getLoggedInUserId(request);

        if (userId == null) {
            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Please log in first."
            );
            return;
        }

        String idParam = request.getParameter("id");

        if (idParam == null || idParam.isBlank()) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Transaction ID is required."
            );
            return;
        }

        try {
            long id = Long.parseLong(idParam);

            if (id <= 0) {
                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid transaction ID."
                );
                return;
            }

            String sql =
                    "DELETE FROM transactions WHERE id = ? AND user_id = ?";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setLong(1, id);
                ps.setLong(2, userId);

                int deleted = ps.executeUpdate();

                if (deleted == 0) {
                    response.sendError(
                            HttpServletResponse.SC_NOT_FOUND,
                            "Transaction not found."
                    );
                    return;
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(
                    "{\"message\":\"Transaction deleted successfully\"}"
            );

        } catch (NumberFormatException e) {
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid transaction ID."
            );

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error while deleting transaction."
            );
        }
    }
}
