package api;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import util.DBConnection;
import util.Json;

/**
 * POST /api/book
 * form fields: showId, seats (comma separated, e.g. "A1,A2"), name, email, phone (optional)
 * Books all seats in ONE transaction, so it either fully succeeds or changes nothing.
 */
@WebServlet("/api/book")
public class BookServlet extends HttpServlet {

    private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Thrown for problems the user can fix (bad input, seat taken). */
    private static class BookingException extends Exception {
        BookingException(String msg) { super(msg); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            int showId = parseShowId(req.getParameter("showId"));
            Set<String> seats = parseSeats(req.getParameter("seats"));
            String name = trim(req.getParameter("name"));
            String email = trim(req.getParameter("email"));
            String phone = trim(req.getParameter("phone"));
            String method = trim(req.getParameter("method"));

            if (name.length() < 2) throw new BookingException("Enter your full name.");
            if (!email.matches("^\\S+@\\S+\\.\\S+$")) throw new BookingException("Enter a valid email address.");

            if (!java.util.Arrays.asList("UPI", "Card", "NetBanking").contains(method))
                throw new BookingException("Choose a payment method.");

            // A signed-in customer always books under their own account.
            jakarta.servlet.http.HttpSession hs = req.getSession(false);
            Integer sessionCustomer = hs == null ? null : (Integer) hs.getAttribute("customerId");

            String[] result = book(showId, seats, name, email, phone, sessionCustomer, method);
            resp.getWriter().write("{\"ok\":true,\"bookingId\":" + Json.q(result[0])
                    + ",\"total\":" + result[1] + "}");

        } catch (BookingException e) {
            resp.setStatus(400);
            resp.getWriter().write(Json.error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Booking failed. Please try again."));
        }
    }

    private String[] book(int showId, Set<String> seats, String name, String email, String phone,
                          Integer sessionCustomer, String method)
            throws SQLException, BookingException {

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Lock this show so two people cannot book at the same moment.
                int screenId;
                BigDecimal basePrice;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT screen_id, base_price FROM shows WHERE show_id = ? FOR UPDATE")) {
                    ps.setInt(1, showId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new BookingException("Show not found.");
                        screenId = rs.getInt("screen_id");
                        basePrice = rs.getBigDecimal("base_price");
                    }
                }

                // 2. Look up each seat, make sure it is free, and add up the price.
                int[] seatIds = new int[seats.size()];
                BigDecimal[] prices = new BigDecimal[seats.size()];
                BigDecimal total = BigDecimal.ZERO;
                int i = 0;
                for (String label : seats) {
                    String row = label.substring(0, 1);
                    int number = Integer.parseInt(label.substring(1));

                    int seatId;
                    String type;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT seat_id, seat_type FROM seats " +
                            "WHERE screen_id = ? AND row_label = ? AND seat_number = ?")) {
                        ps.setInt(1, screenId);
                        ps.setString(2, row);
                        ps.setInt(3, number);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new BookingException("Seat " + label + " does not exist.");
                            seatId = rs.getInt("seat_id");
                            type = rs.getString("seat_type");
                        }
                    }

                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT 1 FROM booking_seats WHERE show_id = ? AND seat_id = ?")) {
                        ps.setInt(1, showId);
                        ps.setInt(2, seatId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) throw new BookingException("Seat " + label + " was just taken. Choose again.");
                        }
                    }

                    // Premium seats cost 20% more, rounded to whole rupees (same as the page).
                    BigDecimal price = "Premium".equals(type)
                            ? basePrice.multiply(new BigDecimal("1.2")).setScale(0, java.math.RoundingMode.HALF_UP)
                            : basePrice.setScale(0, java.math.RoundingMode.HALF_UP);
                    seatIds[i] = seatId;
                    prices[i] = price;
                    total = total.add(price);
                    i++;
                }

                // 3. Find or create the customer by email.
                int customerId = sessionCustomer != null ? sessionCustomer : findOrCreateCustomer(con, name, email, phone);

                // 4. Create the booking and its seats.
                String bookingId = "CB" + randomId(6);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO bookings (booking_id, customer_id, show_id, booked_at, total_amount, status) " +
                        "VALUES (?, ?, ?, NOW(), ?, 'Confirmed')")) {
                    ps.setString(1, bookingId);
                    ps.setInt(2, customerId);
                    ps.setInt(3, showId);
                    ps.setBigDecimal(4, total);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO booking_seats (booking_id, show_id, seat_id, price) VALUES (?, ?, ?, ?)")) {
                    for (int k = 0; k < seatIds.length; k++) {
                        ps.setString(1, bookingId);
                        ps.setInt(2, showId);
                        ps.setInt(3, seatIds[k]);
                        ps.setBigDecimal(4, prices[k]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                // 5. Record the (demo) payment.
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO payments (booking_id, amount, method, status) VALUES (?, ?, ?, 'Paid')")) {
                    ps.setString(1, bookingId);
                    ps.setBigDecimal(2, total);
                    ps.setString(3, method);
                    ps.executeUpdate();
                }

                con.commit();
                return new String[] { bookingId, total.toPlainString() };

            } catch (SQLIntegrityConstraintViolationException e) {
                con.rollback();
                throw new BookingException("A seat was just taken. Choose again.");
            } catch (BookingException | SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private int findOrCreateCustomer(Connection con, String name, String email, String phone) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT customer_id FROM customers WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO customers (full_name, email, phone) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, phone);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static int parseShowId(String s) throws BookingException {
        try { return Integer.parseInt(s); }
        catch (Exception e) { throw new BookingException("Missing or invalid show."); }
    }

    private static Set<String> parseSeats(String s) throws BookingException {
        Set<String> seats = new LinkedHashSet<>();
        if (s != null) {
            for (String part : s.split(",")) {
                String seat = part.trim().toUpperCase();
                if (!seat.matches("^[A-Z][0-9]{1,2}$")) throw new BookingException("Invalid seat: " + part.trim());
                seats.add(seat);
            }
        }
        if (seats.isEmpty() || seats.size() > 6) throw new BookingException("Select 1 to 6 seats.");
        return seats;
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static String randomId(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
        return sb.toString();
    }
}
