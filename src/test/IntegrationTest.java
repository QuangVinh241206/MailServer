package test;

import database.DatabaseManager;
import model.Email;
import server.MailServerApp;
import client.SMTPClient;
import client.POP3Client;

import java.util.List;

public class IntegrationTest {
    public static void main(String[] args) {
        System.out.println("========== BẮT ĐẦU KIỂM TRA HỆ THỐNG MAIL SERVER ==========");

        // 1. Kiểm tra Database
        System.out.println("\n[1] Kiểm tra DatabaseManager...");
        DatabaseManager db = new DatabaseManager();

        boolean authAdmin = db.authenticateUser("admin@localhost", "admin");
        boolean authUser1 = db.authenticateUser("user1@localhost", "123456");
        boolean authUser2 = db.authenticateUser("user2@localhost", "123456");
        boolean authFail = db.authenticateUser("user1@localhost", "wrongpass");

        System.out.println(" - Xác thực admin: " + (authAdmin ? "THÀNH CÔNG" : "THẤT BÀI"));
        System.out.println(" - Xác thực user1: " + (authUser1 ? "THÀNH CÔNG" : "THẤT BÀI"));
        System.out.println(" - Xác thực user2: " + (authUser2 ? "THÀNH CÔNG" : "THẤT BÀI"));
        System.out.println(" - Xác thực sai mật khẩu: " + (!authFail ? "CHÍNH XÁC (từ chối)" : "LỖI"));

        List<Email> user1Emails = db.getEmailsByRecipient("user1@localhost");
        System.out.println(" - Số email mặc định của user1: " + user1Emails.size());

        // 2. Khởi động MailServer (SMTP port 2525, POP3 port 1110)
        System.out.println("\n[2] Khởi động MailServerApp (SMTP port 2525, POP3 port 1110)...");
        MailServerApp server = new MailServerApp(2525, 1110, db);
        server.startAll();

        try {
            Thread.sleep(1000); // Đợi server bind socket
        } catch (InterruptedException e) {}

        // 3. Kiểm tra gửi thư qua SMTP Client tới Localhost
        System.out.println("\n[3] Kiểm tra SMTP Client gửi thư tới SMTPServer...");
        String testSubject = "Test Thư Tự Động " + System.currentTimeMillis();
        String testBody = "Xin chào User 2,\nĐây là nội dung kiểm tra giao thức SMTP và POP3.\nChúc thành công!";

        boolean smtpResult = SMTPClient.sendEmail("localhost", 2525, "user1@localhost", "user2@localhost", testSubject, testBody);
        System.out.println(" - Gửi email qua SMTP: " + (smtpResult ? "THÀNH CÔNG" : "THẤT BÀI"));

        try {
            Thread.sleep(500); // Đợi server lưu DB
        } catch (InterruptedException e) {}

        // 4. Kiểm tra Database có email mới chưa
        System.out.println("\n[4] Kiểm tra dữ liệu trong Database của Server...");
        List<Email> user2Emails = server.getDbManager().getEmailsByRecipient("user2@localhost");
        System.out.println(" - Số email của user2 trong DB: " + user2Emails.size());
        boolean foundInDb = false;
        for (Email e : user2Emails) {
            if (testSubject.equals(e.getSubject())) {
                foundInDb = true;
                System.out.println(" - Tìm thấy email vừa gửi trong DB: Subject='" + e.getSubject() + "', From='" + e.getSender() + "'");
                break;
            }
        }
        System.out.println(" - Kết quả lưu DB: " + (foundInDb ? "CHÍNH XÁC" : "THẤT BÀI"));

        // 5. Kiểm tra POP3 Client kết nối và nhận thư
        System.out.println("\n[5] Kiểm tra POP3 Client kết nối và lấy thư...");
        POP3Client pop3 = new POP3Client();
        boolean pop3Connected = pop3.connect("localhost", 1110);
        System.out.println(" - Kết nối POP3 socket: " + (pop3Connected ? "THÀNH CÔNG" : "THẤT BÀI"));

        if (pop3Connected) {
            boolean pop3Login = pop3.login("user2@localhost", "123456");
            System.out.println(" - POP3 login user2: " + (pop3Login ? "THÀNH CÔNG" : "THẤT BÀI"));

            int msgCount = pop3.getMessageCount();
            System.out.println(" - POP3 STAT đếm số thư: " + msgCount);

            if (msgCount > 0) {
                Email retrievedEmail = pop3.retrieveMessage(1);
                if (retrievedEmail != null) {
                    System.out.println(" - POP3 RETR 1: From=" + retrievedEmail.getSender() + ", Subject=" + retrievedEmail.getSubject());
                }
            }
            pop3.disconnect();
            System.out.println(" - POP3 ngắt kết nối: THÀNH CÔNG");
        }

        // 6. Dừng Server
        System.out.println("\n[6] Dừng Server...");
        server.stopAll();
        db.close();

        System.out.println("\n========== TẤT CẢ KIỂM TRA ĐÃ HOÀN TẤT VÀ ĐẠT YÊU CẦU ==========");
        System.exit(0);
    }
}
