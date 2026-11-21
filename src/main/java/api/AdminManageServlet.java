package api;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import util.DBConnection;
import util.Json;

/**
 * Admin only.
 * GET  /api/admin/manage/options -> movies and screens for the dropdowns
 * POST /api/admin/manage/movie   -> title, genre, dur, lang
 * POST /api/admin/manage/show    -> movieId, screenId, date (YYYY-MM-DD), time (HH:MM), price
 */
@WebServlet("/api/admin/manage/*")
public class AdminManageServlet extends HttpServlet {

    private static class InputException extends Exception {
        InputException(String m) { super(m); }
    }

    private boolean isAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null || (s.getAttribute("adminId") == null && s.getAttribute("customerId") == null)) {
            resp.setStatus(401);
            resp.getWriter().write(Json.error("Please sign in."));
            return false;
        }
        if (s.getAttribute("adminId") == null) {
            resp.setStatus(403);
            resp.getWriter().write(Json.error("Admin access only."));
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;
        if (!"/options".equals(req.getPathInfo())) {
            resp.setStatus(404);
            resp.getWriter().write(Json.error("Not found."));
            return;
        }
        StringBuilder out = new StringBuilder("{\"ok\":true,\"movies\":[");
        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT movie_id, title FROM movies WHERE is_active = 1 ORDER BY title");
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(",");
                    first = false;
                    out.append("{\"id\":").append(rs.getInt(1)).append(",\"title\":").append(Json.q(rs.getString(2))).append("}");
                }
            }
            out.append("],\"screens\":[");
            try (PreparedStatement ps = con.prepareStatement("SELECT screen_id, screen_name FROM screens ORDER BY screen_id");
                 ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(",");
                    first = false;
                    out.append("{\"id\":").append(rs.getInt(1)).append(",\"name\":").append(Json.q(rs.getString(2))).append("}");
                }
            }
            out.append("]}");
            resp.getWriter().write(out.toString());
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Could not load options."));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;

        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            if ("/movie".equals(path)) addMovie(req);
            else if ("/show".equals(path)) addShow(req);
            else {
                resp.setStatus(404);
                resp.getWriter().write(Json.error("Not found."));
                return;
            }
            resp.getWriter().write("{\"ok\":true}");
        } catch (InputException e) {
            resp.setStatus(400);
            resp.getWriter().write(Json.error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Could not save. Please try again."));
        }
    }

    private void addMovie(HttpServletRequest req) throws Exception {
        String title = t(req, "title"), genre = t(req, "genre"), lang = t(req, "lang");
        int dur;
        try { dur = Integer.parseInt(t(req, "dur")); } catch (Exception e) { throw new InputException("Enter the duration in minutes."); }
        if (title.isEmpty() || title.length() > 150) throw new InputException("Title must be 1 to 150 characters.");
        if (genre.isEmpty() || genre.length() > 50) throw new InputException("Genre must be 1 to 50 characters.");
        if (lang.isEmpty() || lang.length() > 30) throw new InputException("Language must be 1 to 30 characters.");
        if (dur < 30 || dur > 400) throw new InputException("Duration must be 30 to 400 minutes.");

        try (Connection con = DBConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT 1 FROM movies WHERE LOWER(title) = LOWER(?) AND is_active = 1")) {
                ps.setString(1, title);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new InputException("A movie with this title already exists.");
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO movies (title, genre, duration_min, language, is_active) VALUES (?, ?, ?, ?, 1)")) {
                ps.setString(1, title);
                ps.setString(2, genre);
                ps.setInt(3, dur);
                ps.setString(4, lang);
                ps.executeUpdate();
            }
        }
    }

    private void addShow(HttpServletRequest req) throws Exception {
        int movieId, screenId;
        LocalDate date;
        LocalTime time;
        BigDecimal price;
        try {
            movieId = Integer.parseInt(t(req, "movieId"));
            screenId = Integer.parseInt(t(req, "screenId"));
            date = LocalDate.parse(t(req, "date"));
            time = LocalTime.parse(t(req, "time"));
            price = new BigDecimal(t(req, "price"));
        } catch (Exception e) {
            throw new InputException("Fill in every field correctly.");
        }
        if (date.isBefore(LocalDate.now())) throw new InputException("The date cannot be in the past.");
        if (price.signum() <= 0 || price.compareTo(new BigDecimal("10000")) > 0)
            throw new InputException("Price must be between 1 and 10000.");
        String start = time.withSecond(0).toString().length() == 5 ? time + ":00" : time.toString();

        try (Connection con = DBConnection.getConnection()) {
            int dur;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT duration_min FROM movies WHERE movie_id = ? AND is_active = 1")) {
                ps.setInt(1, movieId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InputException("Movie not found.");
                    dur = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM screens WHERE screen_id = ?")) {
                ps.setInt(1, screenId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new InputException("Screen not found.");
                }
            }
            // Reject a show that overlaps another show on the same screen (15 minute cleaning gap).
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT 1 FROM shows s JOIN movies m ON m.movie_id = s.movie_id " +
                    "WHERE s.screen_id = ? AND s.show_date = ? " +
                    "AND ? < ADDTIME(s.show_time, SEC_TO_TIME((m.duration_min + 15) * 60)) " +
                    "AND s.show_time < ADDTIME(?, SEC_TO_TIME(?))")) {
                ps.setInt(1, screenId);
                ps.setDate(2, java.sql.Date.valueOf(date));
                ps.setString(3, start);
                ps.setString(4, start);
                ps.setInt(5, (dur + 15) * 60);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new InputException("Another show already uses this screen at that time.");
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO shows (movie_id, screen_id, show_date, show_time, base_price) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, movieId);
                ps.setInt(2, screenId);
                ps.setDate(3, java.sql.Date.valueOf(date));
                ps.setString(4, start);
                ps.setBigDecimal(5, price);
                ps.executeUpdate();
            }
        }
    }

    private static String t(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? "" : v.trim();
    }
}
