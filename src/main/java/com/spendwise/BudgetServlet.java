
package com.spendwise;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

@WebServlet("/api/budget")
public class BudgetServlet extends HttpServlet {

    private final Gson gson = new Gson();

    private Long getLoggedInUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return null;
        }

        Object userId = session.getAttribute("userId");

        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }

        if (userId instanceof String) {
            try {
                return Long.parseLong((String) userId);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private boolean requireLogin(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (getLoggedInUserId(request) == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Please log in first\"}");
            return false;
        }

        return true;
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!requireLogin(request, response)) {
            return;
        }

        Long userId = getLoggedInUserId(request);
        LocalDate month;

        try {
            String monthParam = request.getParameter("month");

            month = (monthParam == null || monthParam.isBlank())
                    ? LocalDate.now().withDayOfMonth(1)
                    : YearMonth.parse(monthParam).atDay(1);

        } catch (DateTimeParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(
                    "{\"error\":\"Month must use YYYY-MM format\"}");
            return;
        }

        String budgetSql = """
                SELECT budget_amount
                FROM monthly_budgets
                WHERE user_id = ? AND budget_month = ?
                """;

        String expenseSql = """
                SELECT COALESCE(SUM(amount), 0)
                FROM transactions
                WHERE user_id = ?
                  AND type = 'EXPENSE'
                  AND transaction_date >= ?
                  AND transaction_date < ?
                """;

        try (Connection conn = DBConnection.getConnection()) {

            BigDecimal budget = BigDecimal.ZERO;
            BigDecimal expenses = BigDecimal.ZERO;

            try (PreparedStatement stmt =
                         conn.prepareStatement(budgetSql)) {

                stmt.setLong(1, userId);
                stmt.setDate(2, Date.valueOf(month));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        budget = rs.getBigDecimal("budget_amount");
                        if (budget == null) {
                            budget = BigDecimal.ZERO;
                        }
                    }
                }
            }

            try (PreparedStatement stmt =
                         conn.prepareStatement(expenseSql)) {

                stmt.setLong(1, userId);
                stmt.setDate(2, Date.valueOf(month));
                stmt.setDate(3, Date.valueOf(month.plusMonths(1)));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        expenses = rs.getBigDecimal(1);
                        if (expenses == null) {
                            expenses = BigDecimal.ZERO;
                        }
                    }
                }
            }

            Map<String, Object> result = Map.of(
                    "month", month.toString().substring(0, 7),
                    "budget", budget,
                    "expenses", expenses,
                    "remaining", budget.subtract(expenses),
                    "hasBudget",
                    budget.compareTo(BigDecimal.ZERO) > 0
            );

            response.getWriter().write(gson.toJson(result));

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(
                    "{\"error\":\"Could not retrieve budget\"}");
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!requireLogin(request, response)) {
            return;
        }

        Long userId = getLoggedInUserId(request);

        try {
            BudgetRequest data =
                    gson.fromJson(request.getReader(), BudgetRequest.class);

            if (data == null || data.month == null
                    || data.budget == null
                    || data.budget.compareTo(BigDecimal.ZERO) <= 0) {

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write(
                        "{\"error\":\"Valid month and positive budget are required\"}");
                return;
            }

            LocalDate month = YearMonth.parse(data.month).atDay(1);

            String sql = """
                    INSERT INTO monthly_budgets
                        (user_id, budget_month, budget_amount)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        budget_amount = ?
                    """;

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, userId);
                stmt.setDate(2, Date.valueOf(month));
                stmt.setBigDecimal(3, data.budget);
                stmt.setBigDecimal(4, data.budget);

                stmt.executeUpdate();
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(
                    "{\"message\":\"Monthly budget saved successfully\"}");

        } catch (DateTimeParseException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(
                    "{\"error\":\"Invalid month format. Use YYYY-MM\"}");

        } catch (JsonParseException | NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(
                    "{\"error\":\"Invalid budget request\"}");

        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(
                    "{\"error\":\"Could not save budget\"}");
        }
    }

    private static class BudgetRequest {
        String month;
        BigDecimal budget;
    }
}
