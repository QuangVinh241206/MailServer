package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import database.DatabaseManager;
import model.Email;

/**
 * POP3 Server - Xử lý giao thức POP3 để client lấy email
 * Hỗ trợ: USER, PASS, STAT, LIST, RETR, DELE, QUIT, NOOP, RSET, TOP, UIDL
 */
public class POP3Server {
    private int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private DatabaseManager dbManager;
    private List<String> serverLog = Collections.synchronizedList(new ArrayList<>());

    public POP3Server(int port, DatabaseManager dbManager) {
        this.port = port;
        this.dbManager = dbManager;
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                addLog("POP3 Server đã khởi động trên cổng " + port);
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        addLog("Kết nối POP3 mới từ: " + clientSocket.getInetAddress());
                        new Thread(new POP3Handler(clientSocket)).start();
                    } catch (SocketException e) {
                        if (running) addLog("Lỗi POP3: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                addLog("Không thể khởi động POP3 Server: " + e.getMessage());
            }
        }, "POP3-Server-Thread").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            addLog("POP3 Server đã dừng");
        } catch (IOException e) {
            addLog("Lỗi khi dừng POP3 Server: " + e.getMessage());
        }
    }

    public boolean isRunning() { return running; }
    public List<String> getServerLog() { return serverLog; }

    private void addLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        serverLog.add("[" + timestamp + "] " + message);
        System.out.println("[POP3] " + message);
    }

    private class POP3Handler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String username = "";
        private boolean authenticated = false;
        private List<Email> emails = new ArrayList<>();
        private Set<Integer> deletedIndexes = new HashSet<>();

        public POP3Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

                sendResponse("+OK POP3 Server Ready");

                String line;
                while ((line = reader.readLine()) != null) {
                    addLog("C: " + line);
                    String[] parts = line.split("\\s+", 2);
                    String command = parts[0].toUpperCase();
                    String arg = parts.length > 1 ? parts[1] : "";

                    switch (command) {
                        case "USER":
                            username = arg.trim();
                            sendResponse("+OK User accepted");
                            break;

                        case "PASS":
                            if (username.isEmpty()) {
                                sendResponse("-ERR No username given");
                            } else {
                                // Xác thực người dùng
                                if (dbManager.authenticateUser(username, arg.trim())) {
                                    authenticated = true;
                                    // Lấy email của user
                                    emails = dbManager.getEmailsByRecipient(username);
                                    sendResponse("+OK Maildrop locked and ready");
                                    addLog("User " + username + " đã xác thực thành công, " + emails.size() + " email");
                                } else {
                                    sendResponse("-ERR Authentication failed");
                                    addLog("User " + username + " xác thực thất bại");
                                }
                            }
                            break;

                        case "STAT":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            int count = 0;
                            long totalSize = 0;
                            for (int i = 0; i < emails.size(); i++) {
                                if (!deletedIndexes.contains(i)) {
                                    count++;
                                    totalSize += getEmailSize(emails.get(i));
                                }
                            }
                            sendResponse("+OK " + count + " " + totalSize);
                            break;

                        case "LIST":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            if (arg.isEmpty()) {
                                // List tất cả
                                int cnt = 0;
                                long total = 0;
                                for (int i = 0; i < emails.size(); i++) {
                                    if (!deletedIndexes.contains(i)) {
                                        cnt++;
                                        total += getEmailSize(emails.get(i));
                                    }
                                }
                                sendResponse("+OK " + cnt + " messages (" + total + " octets)");
                                for (int i = 0; i < emails.size(); i++) {
                                    if (!deletedIndexes.contains(i)) {
                                        writer.println((i + 1) + " " + getEmailSize(emails.get(i)));
                                    }
                                }
                                writer.println(".");
                            } else {
                                int idx = Integer.parseInt(arg.trim()) - 1;
                                if (idx >= 0 && idx < emails.size() && !deletedIndexes.contains(idx)) {
                                    sendResponse("+OK " + (idx + 1) + " " + getEmailSize(emails.get(idx)));
                                } else {
                                    sendResponse("-ERR No such message");
                                }
                            }
                            break;

                        case "RETR":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            try {
                                int rIdx = Integer.parseInt(arg.trim()) - 1;
                                if (rIdx >= 0 && rIdx < emails.size() && !deletedIndexes.contains(rIdx)) {
                                    Email email = emails.get(rIdx);
                                    String emailContent = formatEmail(email);
                                    sendResponse("+OK " + emailContent.length() + " octets");
                                    writer.println(emailContent);
                                    writer.println(".");
                                } else {
                                    sendResponse("-ERR No such message");
                                }
                            } catch (NumberFormatException e) {
                                sendResponse("-ERR Invalid message number");
                            }
                            break;

                        case "DELE":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            try {
                                int dIdx = Integer.parseInt(arg.trim()) - 1;
                                if (dIdx >= 0 && dIdx < emails.size() && !deletedIndexes.contains(dIdx)) {
                                    deletedIndexes.add(dIdx);
                                    sendResponse("+OK Message " + (dIdx + 1) + " deleted");
                                } else {
                                    sendResponse("-ERR No such message");
                                }
                            } catch (NumberFormatException e) {
                                sendResponse("-ERR Invalid message number");
                            }
                            break;

                        case "RSET":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            deletedIndexes.clear();
                            sendResponse("+OK Maildrop has " + emails.size() + " messages");
                            break;

                        case "NOOP":
                            sendResponse("+OK");
                            break;

                        case "TOP":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            try {
                                String[] topArgs = arg.trim().split("\\s+");
                                int tIdx = Integer.parseInt(topArgs[0]) - 1;
                                int numLines = topArgs.length > 1 ? Integer.parseInt(topArgs[1]) : 0;
                                if (tIdx >= 0 && tIdx < emails.size() && !deletedIndexes.contains(tIdx)) {
                                    Email email = emails.get(tIdx);
                                    String content = formatEmail(email);
                                    String[] contentLines = content.split("\n");
                                    sendResponse("+OK");
                                    boolean bodyStarted = false;
                                    int bodyLines = 0;
                                    for (String cl : contentLines) {
                                        if (!bodyStarted) {
                                            writer.println(cl);
                                            if (cl.isEmpty()) bodyStarted = true;
                                        } else {
                                            if (bodyLines < numLines) {
                                                writer.println(cl);
                                                bodyLines++;
                                            }
                                        }
                                    }
                                    writer.println(".");
                                } else {
                                    sendResponse("-ERR No such message");
                                }
                            } catch (Exception e) {
                                sendResponse("-ERR Invalid arguments");
                            }
                            break;

                        case "UIDL":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            if (arg.isEmpty()) {
                                sendResponse("+OK");
                                for (int i = 0; i < emails.size(); i++) {
                                    if (!deletedIndexes.contains(i)) {
                                        writer.println((i + 1) + " " + emails.get(i).getId());
                                    }
                                }
                                writer.println(".");
                            } else {
                                int uIdx = Integer.parseInt(arg.trim()) - 1;
                                if (uIdx >= 0 && uIdx < emails.size() && !deletedIndexes.contains(uIdx)) {
                                    sendResponse("+OK " + (uIdx + 1) + " " + emails.get(uIdx).getId());
                                } else {
                                    sendResponse("-ERR No such message");
                                }
                            }
                            break;

                        case "FOLD":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            String folder = arg.trim().toUpperCase();
                            if (folder.isEmpty()) folder = "INBOX";
                            if (folder.equals("INBOX")) {
                                emails = dbManager.getEmailsByRecipientAndFolder(username, "INBOX");
                            } else if (folder.equals("SENT")) {
                                emails = dbManager.getSentEmails(username);
                            } else if (folder.equals("STARRED")) {
                                emails = dbManager.getStarredEmails(username);
                            } else if (folder.equals("TRASH")) {
                                emails = dbManager.getEmailsByRecipientAndFolder(username, "TRASH");
                            } else if (folder.equals("DRAFTS")) {
                                emails = dbManager.getEmailsByRecipientAndFolder(username, "DRAFTS");
                            } else {
                                emails = dbManager.getEmailsByRecipient(username);
                            }
                            deletedIndexes.clear();
                            sendResponse("+OK Folder " + folder + " has " + emails.size() + " messages");
                            break;

                        case "STAR":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            try {
                                int sIdx = Integer.parseInt(arg.trim()) - 1;
                                if (sIdx >= 0 && sIdx < emails.size()) {
                                    Email em = emails.get(sIdx);
                                    dbManager.toggleStarred(em.getId());
                                    em.setStarred(!em.isStarred());
                                    sendResponse("+OK Star toggled: " + em.isStarred());
                                } else {
                                    sendResponse("-ERR No such message");
                                }
                            } catch (Exception e) {
                                sendResponse("-ERR Invalid message number");
                            }
                            break;

                        case "READ":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            try {
                                int rdIdx = Integer.parseInt(arg.trim()) - 1;
                                if (rdIdx >= 0 && rdIdx < emails.size()) {
                                    Email em = emails.get(rdIdx);
                                    dbManager.markAsRead(em.getId());
                                    em.setRead(true);
                                    sendResponse("+OK Marked as read");
                                } else {
                                    sendResponse("-ERR No such message");
                                }
                            } catch (Exception e) {
                                sendResponse("-ERR Invalid message number");
                            }
                            break;

                        case "UNREAD":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            int unreadCount = dbManager.getUnreadCount(username);
                            sendResponse("+OK " + unreadCount);
                            break;

                        case "REG":
                            String[] regParts = arg.trim().split("\\s+", 3);
                            if (regParts.length >= 2) {
                                String rEmail = regParts[0];
                                String rPass = regParts[1];
                                String rName = regParts.length > 2 ? regParts[2] : rEmail.split("@")[0];
                                if (dbManager.registerUser(rEmail, rPass, rName)) {
                                    sendResponse("+OK User registered");
                                    addLog("Đăng ký tài khoản thành công: " + rEmail);
                                } else {
                                    sendResponse("-ERR Email already exists");
                                }
                            } else {
                                sendResponse("-ERR Format: REG email password [displayName]");
                            }
                            break;

                        case "SAVE_SENT":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            String[] sParts = arg.split("\\|", 3);
                            if (sParts.length >= 3) {
                                Email sentEm = new Email(username, sParts[0], sParts[1], sParts[2]);
                                sentEm.setFolder("SENT");
                                sentEm.setRead(true);
                                dbManager.saveEmail(sentEm);
                                sendResponse("+OK Sent email saved");
                            } else {
                                sendResponse("-ERR Invalid SAVE_SENT format");
                            }
                            break;

                        case "SEARCH":
                            if (!authenticated) { sendResponse("-ERR Not authenticated"); break; }
                            emails = dbManager.searchEmails(username, arg.trim());
                            deletedIndexes.clear();
                            sendResponse("+OK Found " + emails.size() + " messages");
                            break;

                        case "QUIT":
                            // Thực hiện xóa hoặc chuyển vào thùng rác
                            for (int idx : deletedIndexes) {
                                Email toDel = emails.get(idx);
                                if ("TRASH".equalsIgnoreCase(toDel.getFolder())) {
                                    dbManager.deleteEmail(toDel.getId());
                                } else {
                                    dbManager.moveToFolder(toDel.getId(), "TRASH");
                                }
                            }
                            sendResponse("+OK Bye");
                            addLog("User " + username + " đã ngắt kết nối");
                            socket.close();
                            return;

                        default:
                            sendResponse("-ERR Unknown command");
                    }
                }
            } catch (IOException e) {
                addLog("Lỗi xử lý POP3: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }

        private void sendResponse(String response) {
            writer.println(response);
            addLog("S: " + response);
        }

        private long getEmailSize(Email email) {
            return formatEmail(email).length();
        }

        private String formatEmail(Email email) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
            StringBuilder sb = new StringBuilder();
            sb.append("From: ").append(email.getSender()).append("\r\n");
            sb.append("To: ").append(email.getRecipient()).append("\r\n");
            sb.append("Subject: ").append(email.getSubject()).append("\r\n");
            sb.append("Date: ").append(sdf.format(email.getDate())).append("\r\n");
            sb.append("Message-ID: <").append(email.getId()).append("@localhost>\r\n");
            sb.append("X-Folder: ").append(email.getFolder() != null ? email.getFolder() : "INBOX").append("\r\n");
            sb.append("X-Read: ").append(email.isRead() ? "1" : "0").append("\r\n");
            sb.append("X-Starred: ").append(email.isStarred() ? "1" : "0").append("\r\n");
            sb.append("\r\n");
            sb.append(email.getBody());
            return sb.toString();
        }
    }
}
