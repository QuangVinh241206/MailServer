package client;

import java.io.*;
import java.net.*;

/**
 * SMTP Client - Gửi email qua giao thức SMTP
 * Hỗ trợ gửi qua localhost server hoặc Gmail SMTP
 */
public class SMTPClient {

    /**
     * Gửi email qua SMTP server trên localhost
     */
    public static boolean sendEmail(String host, int port, String from, String to, String subject, String body) {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(10000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Đọc banner
            String response = reader.readLine();
            System.out.println("S: " + response);

            // HELO
            writer.println("HELO localhost");
            response = reader.readLine();
            System.out.println("S: " + response);

            // MAIL FROM
            writer.println("MAIL FROM:<" + from + ">");
            response = reader.readLine();
            System.out.println("S: " + response);

            // RCPT TO
            writer.println("RCPT TO:<" + to + ">");
            response = reader.readLine();
            System.out.println("S: " + response);

            // DATA
            writer.println("DATA");
            response = reader.readLine();
            System.out.println("S: " + response);

            // Gửi nội dung email
            writer.println("From: " + from);
            writer.println("To: " + to);
            writer.println("Subject: " + subject);
            writer.println("Date: " + new java.util.Date().toString());
            writer.println("Content-Type: text/plain; charset=UTF-8");
            writer.println();
            // Xử lý byte-stuffing cho dòng bắt đầu bằng dấu chấm
            for (String line : body.split("\r?\n")) {
                if (line.startsWith(".")) {
                    writer.println("." + line);
                } else {
                    writer.println(line);
                }
            }
            writer.println(".");
            String dataResponse = reader.readLine();
            System.out.println("S: " + dataResponse);
            boolean success = dataResponse != null && dataResponse.startsWith("250");

            // QUIT
            writer.println("QUIT");
            try {
                String quitResponse = reader.readLine();
                System.out.println("S: " + quitResponse);
            } catch (Exception ignored) {}

            return success;
        } catch (Exception e) {
            System.err.println("Lỗi gửi email SMTP: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gửi email qua Gmail SMTP (SSL trên cổng 465)
     * Lưu ý: Gmail yêu cầu xác thực 2 bước và Mật khẩu ứng dụng (App Password)
     */
    public static boolean sendViaGmail(String gmailUser, String gmailAppPassword, String to, String subject, String body) {
        try {
            javax.net.ssl.SSLSocketFactory factory =
                (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            javax.net.ssl.SSLSocket socket =
                (javax.net.ssl.SSLSocket) factory.createSocket("smtp.gmail.com", 465);
            socket.setSoTimeout(15000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Đọc banner chào mừng từ Gmail (có thể nhiều dòng)
            String response = readSmtpResponse(reader);
            System.out.println("Gmail S: " + response);

            // EHLO localhost
            writer.println("EHLO localhost");
            response = readSmtpResponse(reader);
            System.out.println("Gmail S: " + response);

            // AUTH LOGIN
            writer.println("AUTH LOGIN");
            response = reader.readLine();
            System.out.println("Gmail S: " + response);

            // Gửi Username (Base64)
            writer.println(java.util.Base64.getEncoder().encodeToString(gmailUser.getBytes()));
            response = reader.readLine();
            System.out.println("Gmail S: " + response);

            // Gửi App Password (Base64)
            writer.println(java.util.Base64.getEncoder().encodeToString(gmailAppPassword.getBytes()));
            response = reader.readLine();
            System.out.println("Gmail S: " + response);

            if (response == null || !response.startsWith("235")) {
                System.err.println("Gmail xác thực thất bại: " + response);
                socket.close();
                return false;
            }

            // MAIL FROM
            writer.println("MAIL FROM:<" + gmailUser + ">");
            response = readSmtpResponse(reader);
            System.out.println("Gmail S: " + response);

            // RCPT TO
            writer.println("RCPT TO:<" + to + ">");
            response = readSmtpResponse(reader);
            System.out.println("Gmail S: " + response);

            // DATA
            writer.println("DATA");
            response = readSmtpResponse(reader);
            System.out.println("Gmail S: " + response);

            // Nội dung email
            writer.println("From: " + gmailUser);
            writer.println("To: " + to);
            writer.println("Subject: " + subject);
            writer.println("Content-Type: text/plain; charset=UTF-8");
            writer.println("MIME-Version: 1.0");
            writer.println();

            // Byte-stuffing
            for (String line : body.split("\r?\n")) {
                if (line.startsWith(".")) {
                    writer.println("." + line);
                } else {
                    writer.println(line);
                }
            }
            writer.println(".");
            String dataResponse = readSmtpResponse(reader);
            System.out.println("Gmail S: " + dataResponse);
            boolean success = dataResponse != null && dataResponse.startsWith("250");

            // QUIT
            writer.println("QUIT");
            try { socket.close(); } catch (Exception ignored) {}

            return success;
        } catch (Exception e) {
            System.err.println("Lỗi gửi Gmail: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static String readSmtpResponse(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) return null;
        // Kiểm tra multi-line response (dạng '250-...')
        while (line.length() >= 4 && line.charAt(3) == '-') {
            String nextLine = reader.readLine();
            if (nextLine == null) break;
            line = nextLine;
        }
        return line;
    }
}
