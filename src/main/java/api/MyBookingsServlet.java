package api;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import util.DBConnection;
import util.Json;

/** GET /api/mybookings  ->  the signed-in customer's bookings (newest first). */
@WebServlet("/api/mybookings")
public class MyBookingsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession s = req.getSession(false);
        Integer customerId = s == null ? null : (Integer) s.getAttribute("customerId");
        if (customerId == null) {
            resp.setStatus(401);
            resp.getWriter().write(Json.error("Please sign in."));
            return;
        }

        String bookingsSql =
            "SELECT b.booking_id, b.status, b.total_amount, sh.show_date, sh.show_time, m.title " +
            "FROM bookings b " +
            "JOIN shows sh ON sh.show_id = b.show_id " +
            "JOIN movies m ON m.movie_id = sh.movie_id " +
            "WHERE b.customer_id = ? " +
            "ORDER BY b.booked_at DESC, b.booking_id DESC";

        String seatsSql =
            "SELECT bs.booking_id, st.row_label, st.seat_number " +
            "FROM booking_seats bs " +
            "JOIN bookings b ON b.booking_id = bs.booking_id " +
            "JOIN seats st ON st.seat_id = bs.seat_id " +
            "WHERE b.customer_id = ? " +
            "ORDER BY st.row_label, st.seat_number";

        try (Connection con = DBConnection.getConnection()) {

            Map<String, List<String>> seatsByBooking = new HashMap<>();
            try (PreparedStatement ps = con.prepareStatement(seatsSql)) {
                ps.setInt(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        seatsByBooking
                            .computeIfAbsent(rs.getString("booking_id"), k -> new ArrayList<>())
                            .add(rs.getString("row_label") + rs.getInt("seat_number"));
                    }
                }
            }

            StringBuilder out = new StringBuilder("{\"ok\":true,\"bookings\":[");
            try (PreparedStatement ps = con.prepareStatement(bookingsSql)) {
                ps.setInt(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) out.append(",");
                        first = false;
                        String id = rs.getString("booking_id");
                        out.append("{\"id\":").append(Json.q(id))
                           .append(",\"status\":").append(Json.q(rs.getString("status")))
                           .append(",\"total\":").append(rs.getBigDecimal("total_amount").toPlainString())
                           .append(",\"date\":").append(Json.q(rs.getDate("show_date").toString()))
                           .append(",\"time\":").append(Json.q(rs.getString("show_time").substring(0, 5)))
                           .append(",\"title\":").append(Json.q(rs.getString("title")))
                           .append(",\"seats\":[");
                        List<String> seats = seatsByBooking.getOrDefault(id, new ArrayList<>());
                        for (int i = 0; i < seats.size(); i++) {
                            if (i > 0) out.append(",");
                            out.append(Json.q(seats.get(i)));
                        }
                        out.append("]}");
                    }
                }
            }
            out.append("]}");
            resp.getWriter().write(out.toString());

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Could not load your bookings."));
        }
    }
}
