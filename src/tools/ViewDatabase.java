package tools;

import java.sql.*;

/**
 * Công cụ xem toàn bộ dữ liệu trong file mailserver.db
 */
public class ViewDatabase {
    private static final String DB_URL = "jdbc:sqlite:mailserver.db";

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("               CHI TIẾT CƠ SỞ DỮ LIỆU MAIL SERVER (mailserver.db)              ");
        System.out.println("================================================================================");

        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(DB_URL)) {

                // 1. Xem bảng users
                System.out.println("\n[1] BẢNG USERS (Người dùng):");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.printf("%-5s | %-25s | %-15s | %-20s%n", "ID", "Email", "Password", "Display Name");
                System.out.println("--------------------------------------------------------------------------------");

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM users ORDER BY id ASC")) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        System.out.printf("%-5d | %-25s | %-15s | %-20s%n",
                                rs.getInt("id"),
                                rs.getString("email"),
                                rs.getString("password"),
                                rs.getString("display_name"));
                    }
                    if (count == 0) System.out.println("(Chưa có dữ liệu người dùng)");
                }

                // 2. Xem bảng emails
                System.out.println("\n[2] BẢNG EMAILS (Thư điện tử):");
                System.out.println("--------------------------------------------------------------------------------");
                System.out.printf("%-20s | %-20s | %-25s | %-7s | %-6s | %-19s%n",
                        "Sender", "Recipient", "Subject", "Folder", "Status", "Date");
                System.out.println("--------------------------------------------------------------------------------");

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM emails ORDER BY date DESC")) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        String subject = rs.getString("subject");
                        if (subject != null && subject.length() > 23) {
                            subject = subject.substring(0, 20) + "...";
                        }
                        String status = rs.getInt("is_read") == 1 ? "Đã đọc" : "Chưa đọc";
                        if (rs.getInt("starred") == 1) status += " [*]";

                        System.out.printf("%-20s | %-20s | %-25s | %-7s | %-10s | %-19s%n",
                                rs.getString("sender"),
                                rs.getString("recipient"),
                                subject != null ? subject : "(trống)",
                                rs.getString("folder"),
                                status,
                                rs.getString("date"));
                    }
                    if (count == 0) System.out.println("(Chưa có email nào trong cơ sở dữ liệu)");
                }

                System.out.println("\n================================================================================");
                System.out.println("  Đường dẫn file database: " + new java.io.File("mailserver.db").getAbsolutePath());
                System.out.println("================================================================================");
            }
        } catch (Exception e) {
            System.err.println("Lỗi đọc database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
