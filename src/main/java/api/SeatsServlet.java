package api;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import util.DBConnection;
import util.Json;

/** GET /api/seats?show=ID  ->  every seat of the show's screen, with taken/free flag. */
@WebServlet("/api/seats")
public class SeatsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        int showId;
        try {
            showId = Integer.parseInt(req.getParameter("show"));
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write(Json.error("Missing or invalid show id."));
            return;
        }

        String sql =
            "SELECT st.row_label, st.seat_number, st.seat_type, " +
            "       (bs.seat_id IS NOT NULL) AS taken " +
            "FROM shows sh " +
            "JOIN seats st ON st.screen_id = sh.screen_id " +
            "LEFT JOIN booking_seats bs ON bs.seat_id = st.seat_id AND bs.show_id = sh.show_id " +
            "WHERE sh.show_id = ? " +
            "ORDER BY st.row_label, st.seat_number";

        StringBuilder out = new StringBuilder("{\"ok\":true,\"seats\":[");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(",");
                    first = false;
                    out.append("{\"id\":").append(Json.q(rs.getString("row_label") + rs.getInt("seat_number")))
                       .append(",\"type\":").append(Json.q(rs.getString("seat_type")))
                       .append(",\"taken\":").append(rs.getInt("taken") == 1)
                       .append("}");
                }
            }
            out.append("]}");
            resp.getWriter().write(out.toString());

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Could not load seats."));
        }
    }
}
