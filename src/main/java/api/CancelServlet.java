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

/** POST /api/cancel  (bookingId)  ->  cancels the signed-in customer's own booking and frees its seats. */
@WebServlet("/api/cancel")
public class CancelServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        HttpSession s = req.getSession(false);
        Integer customerId = s == null ? null : (Integer) s.getAttribute("customerId");
        if (customerId == null) {
            resp.setStatus(401);
            resp.getWriter().write(Json.error("Please sign in."));
            return;
        }

        String bookingId = req.getParameter("bookingId");
        if (bookingId == null || bookingId.isEmpty() || bookingId.length() > 10) {
            resp.setStatus(400);
            resp.getWriter().write(Json.error("Missing booking id."));
            return;
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String status;
                // The customer_id check means you can only cancel your own bookings.
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT status FROM bookings WHERE booking_id = ? AND customer_id = ? FOR UPDATE")) {
                    ps.setString(1, bookingId);
                    ps.setInt(2, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            resp.setStatus(404);
                            resp.getWriter().write(Json.error("Booking not found."));
                            return;
                        }
                        status = rs.getString(1);
                    }
                }
                if (!"Confirmed".equals(status)) {
                    con.rollback();
                    resp.setStatus(400);
                    resp.getWriter().write(Json.error("This booking is already cancelled."));
                    return;
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE bookings SET status = 'Cancelled' WHERE booking_id = ?")) {
                    ps.setString(1, bookingId);
                    ps.executeUpdate();
                }
                // Deleting the seat rows is what makes the seats free again.
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM booking_seats WHERE booking_id = ?")) {
                    ps.setString(1, bookingId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE payments SET status = 'Refunded' WHERE booking_id = ? AND status = 'Paid'")) {
                    ps.setString(1, bookingId);
                    ps.executeUpdate();
                }
                con.commit();
                resp.getWriter().write("{\"ok\":true}");
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Could not cancel the booking."));
        }
    }
}
