package com.spendwise;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/category-budgets")
public class CategoryBudgetServlet extends HttpServlet {

    private final Gson gson = new Gson();

    private static final String[] CATEGORIES = {
            "Food", "Transport", "Education", "Shopping",
            "Entertainment", "Health", "Bills", "Other"
    };

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

    // GET: Load category budgets and expenses for the logged-in user.
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Long userId = getLoggedInUserId(request);

        if (userId == null) {
            response.sendError(401, "Please log in first.");
            return;
        }

        YearMonth selectedMonth;

        try {
            String monthParam = request.getParameter("month");

            selectedMonth = (monthParam == null || monthParam.isBlank())
                    ? YearMonth.now()
                    : YearMonth.parse(monthParam);

        } catch (DateTimeParseException e) {
            response.sendError(400, "Month must use YYYY-MM format.");
            return;
        }

        LocalDate start = selectedMonth.atDay(1);
        LocalDate end = selectedMonth.plusMonths(1).atDay(1);

        String expenseSql = """
                SELECT category, COALESCE(SUM(amount), 0) AS spent
                FROM transactions
                WHERE user_id = ?
                  AND type = 'EXPENSE'
                  AND transaction_date >= ?
                  AND transaction_date < ?
                GROUP BY category
                """;

        String budgetSql = """
                SELECT category, budget_amount
                FROM category_budgets
                WHERE user_id = ?
                  AND budget_month = ?
                """;

        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        Map<String, BigDecimal> budgets = new LinkedHashMap<>();

        try (Connection con = DBConnection.getConnection()) {

            try (PreparedStatement ps = con.prepareStatement(expenseSql)) {
                ps.setLong(1, userId);
                ps.setDate(2, java.sql.Date.valueOf(start));
                ps.setDate(3, java.sql.Date.valueOf(end));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        expenses.put(
                                rs.getString("category"),
                                rs.getBigDecimal("spent")
                        );
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(budgetSql)) {
                ps.setLong(1, userId);
                ps.setDate(2, java.sql.Date.valueOf(start));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        budgets.put(
                                rs.getString("category"),
                                rs.getBigDecimal("budget_amount")
                        );
                    }
                }
            }

            Map<String, Boolean> allCategories = new LinkedHashMap<>();

            for (String category : CATEGORIES) {
                allCategories.put(category, true);
            }

            expenses.keySet().forEach(c -> allCategories.put(c, true));
            budgets.keySet().forEach(c -> allCategories.put(c, true));

            List<Map<String, Object>> result = new ArrayList<>();

            for (String category : allCategories.keySet()) {
                BigDecimal spent = expenses.getOrDefault(
                        category, BigDecimal.ZERO
                );

                BigDecimal budget = budgets.getOrDefault(
                        category, BigDecimal.ZERO
                );

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category", category);
                item.put("budget", budget);
                item.put("spent", spent);
                item.put("remaining", budget.subtract(spent));
                item.put("hasBudget",
                        budget.compareTo(BigDecimal.ZERO) > 0);

                result.add(item);
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("month", selectedMonth.toString());
            output.put("categories", result);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(output));

        } catch (SQLException e) {
            getServletContext().log(
                    "Could not load category budgets", e
            );
            response.sendError(500, "Could not load category budgets.");
        }
    }

    // POST: Create or update a category budget for the logged-in user.
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Long userId = getLoggedInUserId(request);

        if (userId == null) {
            response.sendError(401, "Please log in first.");
            return;
        }

        try {
            JsonObject body = gson.fromJson(
                    request.getReader(), JsonObject.class
            );

            if (body == null
                    || !body.has("month")
                    || !body.has("category")
                    || !body.has("budget")
                    || body.get("month").isJsonNull()
                    || body.get("category").isJsonNull()
                    || body.get("budget").isJsonNull()) {

                response.sendError(
                        400, "Month, category and budget are required."
                );
                return;
            }

            String monthText = body.get("month").getAsString().trim();
            String category = body.get("category").getAsString().trim();
            BigDecimal amount = body.get("budget").getAsBigDecimal();

            YearMonth month = YearMonth.parse(monthText);

            if (category.isBlank()
                    || category.length() > 50
                    || amount.compareTo(BigDecimal.ZERO) <= 0) {

                response.sendError(
                        400, "Enter a valid category and positive budget."
                );
                return;
            }

            LocalDate firstDay = month.atDay(1);

            String sql = """
                    INSERT INTO category_budgets
                        (user_id, budget_month, category, budget_amount)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        budget_amount = ?
                    """;

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setLong(1, userId);
                ps.setDate(2, java.sql.Date.valueOf(firstDay));
                ps.setString(3, category);
                ps.setBigDecimal(4, amount);
                ps.setBigDecimal(5, amount);

                ps.executeUpdate();
            }

            JsonObject output = new JsonObject();
            output.addProperty("success", true);
            output.addProperty("message", "Category budget saved.");

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(gson.toJson(output));

        } catch (NumberFormatException e) {
            response.sendError(400, "Budget must be a valid number.");

        } catch (DateTimeParseException e) {
            response.sendError(400, "Month must use YYYY-MM format.");

        } catch (JsonParseException e) {
            response.sendError(400, "Invalid JSON body.");

        } catch (IllegalStateException | UnsupportedOperationException e) {
            response.sendError(400, "Invalid budget data.");

        } catch (SQLException e) {
            getServletContext().log(
                    "Could not save category budget", e
            );
            response.sendError(500, "Could not save category budget.");

        } catch (Exception e) {
            getServletContext().log(
                    "Unexpected error saving category budget", e
            );
            response.sendError(500, "Could not save category budget.");
        }
    }
}
