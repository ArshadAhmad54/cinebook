package api;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import util.DBConnection;
import util.Json;

/** GET /api/movies?date=YYYY-MM-DD  ->  movies that have shows on that date. */
@WebServlet("/api/movies")
public class MovieServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        LocalDate date;
        try {
            String d = req.getParameter("date");
            date = (d == null || d.isEmpty()) ? LocalDate.now() : LocalDate.parse(d);
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write(Json.error("Invalid date. Use YYYY-MM-DD."));
            return;
        }

        String sql =
            "SELECT m.movie_id, m.title, m.genre, m.duration_min, m.language, " +
            "       s.show_id, s.show_time, s.base_price " +
            "FROM movies m JOIN shows s ON s.movie_id = m.movie_id " +
            "WHERE m.is_active = 1 AND s.show_date = ? " +
            "ORDER BY m.movie_id, s.show_time";

        StringBuilder out = new StringBuilder("[");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                int lastMovie = -1;
                boolean firstMovie = true;
                while (rs.next()) {
                    int movieId = rs.getInt("movie_id");
                    if (movieId != lastMovie) {
                        if (!firstMovie) out.append("]},");
                        firstMovie = false;
                        out.append("{\"id\":").append(movieId)
                           .append(",\"title\":").append(Json.q(rs.getString("title")))
                           .append(",\"genre\":").append(Json.q(rs.getString("genre")))
                           .append(",\"dur\":").append(rs.getInt("duration_min"))
                           .append(",\"lang\":").append(Json.q(rs.getString("language")))
                           .append(",\"shows\":[");
                        lastMovie = movieId;
                    } else {
                        out.append(",");
                    }
                    out.append("{\"showId\":").append(rs.getInt("show_id"))
                       .append(",\"time\":").append(Json.q(rs.getString("show_time").substring(0, 5)))
                       .append(",\"price\":").append(rs.getBigDecimal("base_price").toPlainString())
                       .append("}");
                }
                if (!firstMovie) out.append("]}");
            }
            out.append("]");
            resp.getWriter().write(out.toString());

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Could not load movies."));
        }
    }
}
