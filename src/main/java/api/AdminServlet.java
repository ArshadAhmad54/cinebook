package api;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import util.DBConnection;
import util.Json;

/** GET /api/admin/summary -> revenue, counts and every booking. Only for a signed-in admin. */
@WebServlet("/api/admin/summary")
public class AdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession s = req.getSession(false);
        if (s == null || (s.getAttribute("adminId") == null && s.getAttribute("customerId") == null)) {
            resp.setStatus(401);
            resp.getWriter().write(Json.error("Please sign in."));
            return;
        }
        if (s.getAttribute("adminId") == null) {
            resp.setStatus(403);
            resp.getWriter().write(Json.error("Admin access only."));
            return;
        }

        String sql =
            "SELECT b.booking_id, b.status, b.total_amount, b.booked_at, sh.show_date, sh.show_time, " +
            "       m.title, c.full_name, c.email, p.method, " +
            "       GROUP_CONCAT(CONCAT(st.row_label, st.seat_number) ORDER BY st.row_label, st.seat_number SEPARATOR ',') AS seats " +
            "FROM bookings b " +
            "JOIN shows sh ON sh.show_id = b.show_id " +
            "JOIN movies m ON m.movie_id = sh.movie_id " +
            "JOIN customers c ON c.customer_id = b.customer_id " +
            "LEFT JOIN payments p ON p.booking_id = b.booking_id " +
            "LEFT JOIN booking_seats bs ON bs.booking_id = b.booking_id " +
            "LEFT JOIN seats st ON st.seat_id = bs.seat_id " +
            "GROUP BY b.booking_id, b.status, b.total_amount, b.booked_at, sh.show_date, sh.show_time, " +
            "         m.title, c.full_name, c.email, p.method " +
            "ORDER BY b.booked_at DESC, b.booking_id DESC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder rows = new StringBuilder();
            java.math.BigDecimal revenue = java.math.BigDecimal.ZERO;
            int confirmed = 0, tickets = 0;
            boolean first = true;
            while (rs.next()) {
                String status = rs.getString("status");
                String seats = rs.getString("seats");
                if ("Confirmed".equals(status)) {
                    confirmed++;
                    revenue = revenue.add(rs.getBigDecimal("total_amount"));
                    if (seats != null && !seats.isEmpty()) tickets += seats.split(",").length;
                }
                if (!first) rows.append(",");
                first = false;
                String method = rs.getString("method");
                rows.append("{\"id\":").append(Json.q(rs.getString("booking_id")))
                    .append(",\"title\":").append(Json.q(rs.getString("title")))
                    .append(",\"date\":").append(Json.q(rs.getDate("show_date").toString()))
                    .append(",\"time\":").append(Json.q(rs.getString("show_time").substring(0, 5)))
                    .append(",\"seats\":").append(Json.q(seats == null ? "" : seats))
                    .append(",\"name\":").append(Json.q(rs.getString("full_name")))
                    .append(",\"email\":").append(Json.q(rs.getString("email")))
                    .append(",\"method\":").append(Json.q(method == null ? "" : method))
                    .append(",\"total\":").append(rs.getBigDecimal("total_amount").toPlainString())
                    .append(",\"status\":").append(Json.q(status)).append("}");
            }
            resp.getWriter().write("{\"ok\":true,\"revenue\":" + revenue.toPlainString()
                    + ",\"confirmed\":" + confirmed + ",\"tickets\":" + tickets
                    + ",\"bookings\":[" + rows + "]}");

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Could not load admin data."));
        }
    }
}
