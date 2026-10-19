
package com.spendwise;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/api/export")
public class ExportServlet extends HttpServlet {

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

    private String escapeCsv(String value) {
        if (value == null) {
            value = "";
        }

        // Reduce spreadsheet formula-injection risk.
        if (!value.isEmpty()
                && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        Long userId = getLoggedInUserId(request);

        // Do not allow CSV export without login.
        if (userId == null) {
            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Please log in first\"}");
            return;
        }

        String sql = """
                SELECT id, description, amount, type,
                       category, transaction_date
                FROM transactions
                WHERE user_id = ?
                ORDER BY transaction_date DESC, id DESC
                """;

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"spendwise-transactions.csv\""
        );
        response.setCharacterEncoding("UTF-8");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                // UTF-8 BOM helps Excel display text correctly.
                response.getOutputStream().write(
                        new byte[]{
                                (byte) 0xEF,
                                (byte) 0xBB,
                                (byte) 0xBF
                        }
                );

                try (PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                response.getOutputStream(),
                                StandardCharsets.UTF_8))) {

                    writer.println(
                            "ID,Description,Amount,Type,Category,Transaction Date"
                    );

                    while (rs.next()) {
                        writer.print(rs.getLong("id"));
                        writer.print(",");

                        writer.print(
                                escapeCsv(rs.getString("description")));
                        writer.print(",");

                        writer.print(
                                rs.getBigDecimal("amount").toPlainString());
                        writer.print(",");

                        writer.print(
                                escapeCsv(rs.getString("type")));
                        writer.print(",");

                        writer.print(
                                escapeCsv(rs.getString("category")));
                        writer.print(",");

                        writer.println(
                                escapeCsv(
                                        rs.getDate("transaction_date")
                                                .toString()
                                )
                        );
                    }

                    writer.flush();
                }
            }

        } catch (SQLException e) {
            getServletContext().log("CSV export failed", e);

            if (!response.isCommitted()) {
                response.reset();
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could not export transactions."
                );
            }
        }
    }
}
