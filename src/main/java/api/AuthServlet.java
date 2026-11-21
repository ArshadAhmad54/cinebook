package api;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import util.DBConnection;
import util.Json;
import util.Passwords;

/**
 * GET  /api/auth/me        -> who is signed in (if anyone)
 * POST /api/auth/register  -> name, email, password
 * POST /api/auth/login     -> email, password
 * POST /api/auth/logout
 */
@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {

    /** A problem the user can fix; carries the HTTP status to send. */
    private static class AuthException extends Exception {
        final int status;
        AuthException(int status, String message) { super(message); this.status = status; }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        if (!"/me".equals(req.getPathInfo())) {
            resp.setStatus(404);
            resp.getWriter().write(Json.error("Not found."));
            return;
        }
        HttpSession s = req.getSession(false);
        if (s != null && s.getAttribute("adminId") != null) {
            resp.getWriter().write("{\"ok\":true,\"loggedIn\":true,\"role\":\"admin\",\"name\":"
                    + Json.q((String) s.getAttribute("name")) + ",\"email\":\"\"}");
            return;
        }
        if (s == null || s.getAttribute("customerId") == null) {
            resp.getWriter().write("{\"ok\":true,\"loggedIn\":false}");
            return;
        }
        resp.getWriter().write("{\"ok\":true,\"loggedIn\":true,\"name\":" + Json.q((String) s.getAttribute("name"))
                + ",\"email\":" + Json.q((String) s.getAttribute("email")) + ",\"role\":\"customer\"}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            switch (path) {
                case "/register": register(req, resp); break;
                case "/login":    login(req, resp);    break;
                case "/admin-login": adminLogin(req, resp); break;
                case "/logout":
                    HttpSession s = req.getSession(false);
                    if (s != null) s.invalidate();
                    resp.getWriter().write("{\"ok\":true}");
                    break;
                default:
                    resp.setStatus(404);
                    resp.getWriter().write(Json.error("Not found."));
            }
        } catch (AuthException e) {
            resp.setStatus(e.status);
            resp.getWriter().write(Json.error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().write(Json.error("Something went wrong. Please try again."));
        }
    }

    private void register(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, AuthException, SQLException {

        String name = trim(req.getParameter("name"));
        String email = trim(req.getParameter("email"));
        String password = req.getParameter("password");

        if (name.length() < 2) throw new AuthException(400, "Enter your full name.");
        if (!email.matches("^\\S+@\\S+\\.\\S+$")) throw new AuthException(400, "Enter a valid email address.");
        if (password == null || password.length() < 6 || password.length() > 100)
            throw new AuthException(400, "Password must be 6 to 100 characters.");

        String hash = Passwords.hash(password);
        int id;

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Integer existingId = null;
                String existingName = null, existingHash = null;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT customer_id, full_name, password_hash FROM customers WHERE email = ? FOR UPDATE")) {
                    ps.setString(1, email);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existingId = rs.getInt(1);
                            existingName = rs.getString(2);
                            existingHash = rs.getString(3);
                        }
                    }
                }

                if (existingId != null) {
                    if (existingHash != null)
                        throw new AuthException(409, "An account with this email already exists. Please sign in.");
                    // Email was used for a guest booking: attach a password to that customer.
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE customers SET password_hash = ? WHERE customer_id = ?")) {
                        ps.setString(1, hash);
                        ps.setInt(2, existingId);
                        ps.executeUpdate();
                    }
                    id = existingId;
                    name = existingName;
                } else {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO customers (full_name, email, phone, password_hash) VALUES (?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, name);
                        ps.setString(2, email);
                        ps.setString(3, "");
                        ps.setString(4, hash);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            id = keys.getInt(1);
                        }
                    }
                }
                con.commit();
            } catch (AuthException | SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }

        startSession(req, id, name, email);
        resp.getWriter().write("{\"ok\":true,\"name\":" + Json.q(name) + ",\"email\":" + Json.q(email) + "}");
    }

    private void login(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, AuthException, SQLException {

        String email = trim(req.getParameter("email"));
        String password = req.getParameter("password");

        int id = -1;
        String name = null, storedHash = null;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT customer_id, full_name, password_hash FROM customers WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    id = rs.getInt(1);
                    name = rs.getString(2);
                    storedHash = rs.getString(3);
                }
            }
        }

        // Same message for "no such email" and "wrong password" so emails can't be probed.
        if (id < 0 || !Passwords.verify(password, storedHash))
            throw new AuthException(401, "Invalid email or password.");

        startSession(req, id, name, email);
        resp.getWriter().write("{\"ok\":true,\"name\":" + Json.q(name) + ",\"email\":" + Json.q(email) + "}");
    }

    /** POST /api/auth/admin-login  (username, password)  ->  checks the admins table. */
    private void adminLogin(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, AuthException, SQLException {
        String username = trim(req.getParameter("username"));
        String password = req.getParameter("password");

        int id = -1;
        String storedHash = null;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT admin_id, password_hash FROM admins WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { id = rs.getInt(1); storedHash = rs.getString(2); }
            }
        }
        if (id < 0 || password == null || !Passwords.verify(password, storedHash))
            throw new AuthException(401, "Invalid username or password.");

        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession s = req.getSession(true);
        s.setAttribute("adminId", id);
        s.setAttribute("name", username);
        s.setMaxInactiveInterval(60 * 60);
        resp.getWriter().write("{\"ok\":true,\"name\":" + Json.q(username) + ",\"role\":\"admin\"}");
    }

    /** Always start a fresh session at login, so an old session id cannot be reused. */
    private void startSession(HttpServletRequest req, int customerId, String name, String email) {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession s = req.getSession(true);
        s.setAttribute("customerId", customerId);
        s.setAttribute("name", name);
        s.setAttribute("email", email);
        s.setMaxInactiveInterval(2 * 60 * 60);
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
}
