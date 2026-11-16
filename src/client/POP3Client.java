package client;

import model.Email;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * POP3 Client - Nhận email qua giao thức POP3
 */
public class POP3Client {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected = false;

    /**
     * Kết nối tới POP3 server
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(10000);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            String response = reader.readLine();
            System.out.println("POP3 S: " + response);
            connected = response != null && response.startsWith("+OK");
            return connected;
        } catch (Exception e) {
            System.err.println("Lỗi kết nối POP3: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đăng nhập
     */
    public boolean login(String username, String password) {
        try {
            writer.println("USER " + username);
            String response = reader.readLine();
            System.out.println("POP3 S: " + response);

            writer.println("PASS " + password);
            response = reader.readLine();
            System.out.println("POP3 S: " + response);

            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            System.err.println("Lỗi đăng nhập POP3: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy số lượng email
     */
    public int getMessageCount() {
        try {
            writer.println("STAT");
            String response = reader.readLine();
            if (response != null && response.startsWith("+OK")) {
                String[] parts = response.split("\\s+");
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            System.err.println("Lỗi STAT: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Lấy nội dung email theo số thứ tự
     */
    public Email retrieveMessage(int msgNum) {
        try {
            writer.println("RETR " + msgNum);
            String response = reader.readLine();

            if (response != null && response.startsWith("+OK")) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals(".")) break;
                    // Undo byte-stuffing
                    if (line.startsWith("..")) {
                        content.append(line.substring(1));
                    } else {
                        content.append(line);
                    }
                    content.append("\n");
                }
                return parseEmail(content.toString());
            }
        } catch (Exception e) {
            System.err.println("Lỗi RETR: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy tất cả email
     */
    public List<Email> retrieveAllMessages() {
        List<Email> emails = new ArrayList<>();
        int count = getMessageCount();
        for (int i = 1; i <= count; i++) {
            Email email = retrieveMessage(i);
            if (email != null) {
                emails.add(email);
            }
        }
        return emails;
    }

    /**
     * Chuyển thư mục (INBOX, SENT, STARRED, TRASH, DRAFTS)
     */
    public boolean switchFolder(String folder) {
        try {
            writer.println("FOLD " + folder);
            String response = reader.readLine();
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            System.err.println("Lỗi FOLD: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy tất cả email trong một thư mục
     */
    public List<Email> retrieveFolderMessages(String folder) {
        if (switchFolder(folder)) {
            return retrieveAllMessages();
        }
        return new ArrayList<>();
    }

    /**
     * Đánh dấu / Bỏ đánh dấu sao
     */
    public boolean toggleStar(int msgNum) {
        try {
            writer.println("STAR " + msgNum);
            String response = reader.readLine();
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            System.err.println("Lỗi STAR: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đánh dấu đã đọc
     */
    public boolean markRead(int msgNum) {
        try {
            writer.println("READ " + msgNum);
            String response = reader.readLine();
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            System.err.println("Lỗi READ: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đếm số email chưa đọc
     */
    public int getUnreadCount() {
        try {
            writer.println("UNREAD");
            String response = reader.readLine();
            if (response != null && response.startsWith("+OK")) {
                String[] parts = response.split("\\s+");
                if (parts.length > 1) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi UNREAD: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Đăng ký tài khoản mới qua mạng
     */
    public boolean register(String email, String password, String displayName) {
        try {
            writer.println("REG " + email + " " + password + " " + (displayName != null ? displayName : email));
            String response = reader.readLine();
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            System.err.println("Lỗi REG: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lưu thư đã gửi (ví dụ qua Gmail) vào server
     */
    public boolean saveSentEmail(String to, String subject, String body) {
        try {
            writer.println("SAVE_SENT " + to + "|" + subject + "|" + body.replace("\n", "\\n"));
            String response = reader.readLine();
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            System.err.println("Lỗi SAVE_SENT: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tìm kiếm email theo từ khóa
     */
    public List<Email> searchEmails(String keyword) {
        try {
            writer.println("SEARCH " + keyword);
            String response = reader.readLine();
            if (response != null && response.startsWith("+OK")) {
                return retrieveAllMessages();
            }
        } catch (Exception e) {
            System.err.println("Lỗi SEARCH: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Xóa email
     */
    public boolean deleteMessage(int msgNum) {
        try {
            writer.println("DELE " + msgNum);
            String response = reader.readLine();
            return response != null && response.startsWith("+OK");
        } catch (Exception e) {
            System.err.println("Lỗi DELE: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ngắt kết nối
     */
    public void disconnect() {
        try {
            if (writer != null) writer.println("QUIT");
            if (reader != null) reader.readLine();
            if (socket != null) socket.close();
            connected = false;
        } catch (Exception e) {
            System.err.println("Lỗi disconnect: " + e.getMessage());
        }
    }

    public boolean isConnected() { return connected; }

    /**
     * Parse nội dung email từ raw text
     */
    private Email parseEmail(String rawContent) {
        Email email = new Email();
        String[] lines = rawContent.split("\n");
        boolean headersDone = false;
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            if (!headersDone) {
                if (line.trim().isEmpty()) {
                    headersDone = true;
                } else if (line.toLowerCase().startsWith("from:")) {
                    String from = line.substring(5).trim();
                    if (from.contains("<") && from.contains(">")) {
                        from = from.substring(from.indexOf("<") + 1, from.indexOf(">"));
                    }
                    email.setSender(from);
                } else if (line.toLowerCase().startsWith("to:")) {
                    String to = line.substring(3).trim();
                    if (to.contains("<") && to.contains(">")) {
                        to = to.substring(to.indexOf("<") + 1, to.indexOf(">"));
                    }
                    email.setRecipient(to);
                } else if (line.toLowerCase().startsWith("subject:")) {
                    email.setSubject(line.substring(8).trim());
                } else if (line.toLowerCase().startsWith("date:")) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
                        email.setDate(sdf.parse(line.substring(5).trim()));
                    } catch (Exception e) {
                        email.setDate(new Date());
                    }
                } else if (line.toLowerCase().startsWith("message-id:")) {
                    String msgId = line.substring(11).trim();
                    if (msgId.startsWith("<")) msgId = msgId.substring(1);
                    if (msgId.contains("@")) msgId = msgId.substring(0, msgId.indexOf("@"));
                    if (msgId.endsWith(">")) msgId = msgId.substring(0, msgId.length() - 1);
                    email.setId(msgId);
                } else if (line.toLowerCase().startsWith("x-folder:")) {
                    email.setFolder(line.substring(9).trim());
                } else if (line.toLowerCase().startsWith("x-read:")) {
                    email.setRead("1".equals(line.substring(7).trim()));
                } else if (line.toLowerCase().startsWith("x-starred:")) {
                    email.setStarred("1".equals(line.substring(10).trim()));
                }
            } else {
                body.append(line).append("\n");
            }
        }
        email.setBody(body.toString().trim());
        return email;
    }
}
