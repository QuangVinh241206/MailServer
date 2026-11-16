package database;

import model.Email;
import model.User;

import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Quản lý cơ sở dữ liệu SQLite cho Mail Server
 * Lưu trữ email và thông tin người dùng
 */
public class DatabaseManager {
    private Connection connection;
    private static final String DB_URL = "jdbc:sqlite:mailserver.db";

    public DatabaseManager() {
        try {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {}
            connection = DriverManager.getConnection(DB_URL);
            Statement pragma = connection.createStatement();
            pragma.execute("PRAGMA journal_mode=WAL;");
            pragma.execute("PRAGMA busy_timeout=5000;");
            pragma.close();

            createTables();
            createDefaultUsers();
            createDefaultEmails();
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối database: " + e.getMessage());
        }
    }

    public static String normalizeEmail(String email) {
        if (email == null) return "";
        email = email.trim().toLowerCase();
        if (email.startsWith("<") && email.endsWith(">")) {
            email = email.substring(1, email.length() - 1).trim();
        }
        if (!email.isEmpty() && !email.contains("@")) {
            email = email + "@localhost";
        }
        return email;
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();

        // Bảng users
        stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "email TEXT UNIQUE NOT NULL," +
                "password TEXT NOT NULL," +
                "display_name TEXT" +
                ")");

        // Bảng emails
        stmt.execute("CREATE TABLE IF NOT EXISTS emails (" +
                "id TEXT PRIMARY KEY," +
                "sender TEXT NOT NULL," +
                "recipient TEXT NOT NULL," +
                "subject TEXT," +
                "body TEXT," +
                "date TEXT NOT NULL," +
                "is_read INTEGER DEFAULT 0," +
                "folder TEXT DEFAULT 'INBOX'," +
                "starred INTEGER DEFAULT 0" +
                ")");

        // Index cho tìm kiếm nhanh
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_recipient ON emails(recipient)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_sender ON emails(sender)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_folder ON emails(folder)");
        stmt.close();
    }

    private void createDefaultUsers() {
        // Tạo tài khoản mặc định nếu chưa có
        String[][] defaultUsers = {
            {"admin@localhost", "admin", "Admin"},
            {"user1@localhost", "123456", "User 1"},
            {"user2@localhost", "123456", "User 2"},
            {"test@localhost", "test", "Test User"}
        };

        for (String[] user : defaultUsers) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO users (email, password, display_name) VALUES (?, ?, ?)")) {
                ps.setString(1, user[0]);
                ps.setString(2, user[1]);
                ps.setString(3, user[2]);
                ps.executeUpdate();
            } catch (SQLException e) {
                // Bỏ qua nếu đã tồn tại
            }
        }
    }

    private void createDefaultEmails() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM emails");
            boolean hasEmails = rs.next() && rs.getInt(1) > 0;
            rs.close();
            stmt.close();
            if (hasEmails) {
                return; // Đã có email, không cần tạo mẫu
            }

            // Tạo các email mẫu cho user1@localhost
            Email e1 = new Email("admin@localhost", "user1@localhost", "Chào mừng bạn đến với Mail Server!",
                    "Chào mừng bạn đến với hệ thống Mail Server JavaFX.\n\n" +
                    "Hệ thống hỗ trợ đầy đủ các tính năng:\n" +
                    "- Giao thức SMTP gửi thư trên Localhost và Gmail\n" +
                    "- Giao thức POP3 nhận thư và đồng bộ hộp thư\n" +
                    "- Cơ sở dữ liệu SQLite lưu trữ dữ liệu an toàn\n" +
                    "- Giao diện tương tự Gmail dễ sử dụng\n\n" +
                    "Chúc bạn có trải nghiệm tuyệt vời!\nBan Quản trị Mail Server");
            e1.setStarred(true);
            saveEmail(e1);

            Email e2 = new Email("user2@localhost", "user1@localhost", "Tài liệu môn học Lập trình mạng",
                    "Chào bạn,\n\nMình gửi bạn tài liệu bài tập lớn môn Lập trình mạng về đề tài Mail Server & Client.\n" +
                    "Các yêu cầu chính đã hoàn thiện gồm:\n" +
                    "1. SMTP & POP3 Server/Client\n" +
                    "2. Gửi thư Gmail và Localhost\n" +
                    "3. Lưu trữ Database Server\n\n" +
                    "Thân ái,\nUser 2");
            saveEmail(e2);

            Email e3 = new Email("user1@localhost", "user2@localhost", "Re: Báo cáo tiến độ đồ án",
                    "Chào User 2,\n\nĐã nhận được thông tin. Mình đang chạy thử nghiệm hệ thống và kiểm tra các giao thức mạng.\n\nUser 1");
            e3.setFolder("SENT");
            e3.setRead(true);
            saveEmail(e3);

        } catch (SQLException e) {
            System.err.println("Lỗi tạo email mẫu: " + e.getMessage());
        }
    }

    // ==================== USER OPERATIONS ====================

    public synchronized boolean authenticateUser(String email, String password) {
        try {
            email = normalizeEmail(email);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM users WHERE LOWER(email) = ? AND password = ?");
            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            boolean result = rs.next();
            rs.close();
            ps.close();
            return result;
        } catch (SQLException e) {
            System.err.println("Lỗi xác thực: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean registerUser(String email, String password, String displayName) {
        try {
            email = normalizeEmail(email);
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (email, password, display_name) VALUES (?, ?, ?)");
            ps.setString(1, email);
            ps.setString(2, password);
            ps.setString(3, displayName);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký: " + e.getMessage());
            return false;
        }
    }

    public synchronized User getUser(String email) {
        try {
            email = normalizeEmail(email);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM users WHERE LOWER(email) = ?");
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            User user = null;
            if (rs.next()) {
                user = new User();
                user.setId(rs.getInt("id"));
                user.setEmail(rs.getString("email"));
                user.setPassword(rs.getString("password"));
                user.setDisplayName(rs.getString("display_name"));
            }
            rs.close();
            ps.close();
            return user;
        } catch (SQLException e) {
            System.err.println("Lỗi lấy user: " + e.getMessage());
        }
        return null;
    }

    public synchronized List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM users");
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setEmail(rs.getString("email"));
                user.setPassword(rs.getString("password"));
                user.setDisplayName(rs.getString("display_name"));
                users.add(user);
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách users: " + e.getMessage());
        }
        return users;
    }

    // ==================== EMAIL OPERATIONS ====================

    public synchronized void saveEmail(Email email) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO emails (id, sender, recipient, subject, body, date, is_read, folder, starred) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setString(1, email.getId());
            ps.setString(2, normalizeEmail(email.getSender()));
            ps.setString(3, normalizeEmail(email.getRecipient()));
            ps.setString(4, email.getSubject());
            ps.setString(5, email.getBody());
            ps.setString(6, sdf.format(email.getDate()));
            ps.setInt(7, email.isRead() ? 1 : 0);
            ps.setString(8, email.getFolder());
            ps.setInt(9, email.isStarred() ? 1 : 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi lưu email: " + e.getMessage());
        }
    }

    public synchronized List<Email> getAllEmails() {
        List<Email> emails = new ArrayList<>();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM emails ORDER BY date DESC");
            while (rs.next()) {
                emails.add(resultSetToEmail(rs));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Lỗi lấy toàn bộ email: " + e.getMessage());
        }
        return emails;
    }

    public synchronized List<Email> getEmailsByRecipient(String recipient) {
        List<Email> emails = new ArrayList<>();
        try {
            recipient = normalizeEmail(recipient);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM emails WHERE LOWER(recipient) = ? ORDER BY date DESC");
            ps.setString(1, recipient);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                emails.add(resultSetToEmail(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi lấy email: " + e.getMessage());
        }
        return emails;
    }

    public synchronized List<Email> getEmailsByRecipientAndFolder(String recipient, String folder) {
        List<Email> emails = new ArrayList<>();
        try {
            recipient = normalizeEmail(recipient);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM emails WHERE LOWER(recipient) = ? AND folder = ? ORDER BY date DESC");
            ps.setString(1, recipient);
            ps.setString(2, folder);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                emails.add(resultSetToEmail(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi lấy email theo folder: " + e.getMessage());
        }
        return emails;
    }

    public synchronized List<Email> getSentEmails(String sender) {
        List<Email> emails = new ArrayList<>();
        try {
            sender = normalizeEmail(sender);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM emails WHERE LOWER(sender) = ? AND folder = 'SENT' ORDER BY date DESC");
            ps.setString(1, sender);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                emails.add(resultSetToEmail(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi lấy email đã gửi: " + e.getMessage());
        }
        return emails;
    }

    public synchronized List<Email> getStarredEmails(String recipient) {
        List<Email> emails = new ArrayList<>();
        try {
            recipient = normalizeEmail(recipient);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM emails WHERE (LOWER(recipient) = ? OR LOWER(sender) = ?) AND starred = 1 ORDER BY date DESC");
            ps.setString(1, recipient);
            ps.setString(2, recipient);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                emails.add(resultSetToEmail(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi lấy email starred: " + e.getMessage());
        }
        return emails;
    }

    public synchronized void markAsRead(String emailId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE emails SET is_read = 1 WHERE id = ?");
            ps.setString(1, emailId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi đánh dấu đã đọc: " + e.getMessage());
        }
    }

    public synchronized void toggleStarred(String emailId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE emails SET starred = CASE WHEN starred = 0 THEN 1 ELSE 0 END WHERE id = ?");
            ps.setString(1, emailId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi toggle star: " + e.getMessage());
        }
    }

    public synchronized void moveToFolder(String emailId, String folder) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE emails SET folder = ? WHERE id = ?");
            ps.setString(1, folder);
            ps.setString(2, emailId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi di chuyển email: " + e.getMessage());
        }
    }

    public synchronized void deleteEmail(String emailId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM emails WHERE id = ?");
            ps.setString(1, emailId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi xóa email: " + e.getMessage());
        }
    }

    public synchronized List<Email> searchEmails(String recipient, String keyword) {
        List<Email> emails = new ArrayList<>();
        try {
            recipient = normalizeEmail(recipient);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM emails WHERE (LOWER(recipient) = ? OR LOWER(sender) = ?) " +
                "AND (subject LIKE ? OR body LIKE ? OR sender LIKE ?) ORDER BY date DESC");
            ps.setString(1, recipient);
            ps.setString(2, recipient);
            ps.setString(3, "%" + keyword + "%");
            ps.setString(4, "%" + keyword + "%");
            ps.setString(5, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                emails.add(resultSetToEmail(rs));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.err.println("Lỗi tìm kiếm: " + e.getMessage());
        }
        return emails;
    }

    public synchronized int getUnreadCount(String recipient) {
        try {
            recipient = normalizeEmail(recipient);
            PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM emails WHERE LOWER(recipient) = ? AND is_read = 0 AND folder = 'INBOX'");
            ps.setString(1, recipient);
            ResultSet rs = ps.executeQuery();
            int count = 0;
            if (rs.next()) count = rs.getInt(1);
            rs.close();
            ps.close();
            return count;
        } catch (SQLException e) {
            System.err.println("Lỗi đếm email chưa đọc: " + e.getMessage());
        }
        return 0;
    }

    private Email resultSetToEmail(ResultSet rs) throws SQLException {
        Email email = new Email();
        email.setId(rs.getString("id"));
        email.setSender(rs.getString("sender"));
        email.setRecipient(rs.getString("recipient"));
        email.setSubject(rs.getString("subject"));
        email.setBody(rs.getString("body"));
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            email.setDate(sdf.parse(rs.getString("date")));
        } catch (Exception e) {
            email.setDate(new java.util.Date());
        }
        email.setRead(rs.getInt("is_read") == 1);
        email.setFolder(rs.getString("folder"));
        email.setStarred(rs.getInt("starred") == 1);
        return email;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Lỗi đóng database: " + e.getMessage());
        }
    }
}
