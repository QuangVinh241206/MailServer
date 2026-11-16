package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import database.DatabaseManager;
import model.Email;

/**
 * SMTP Server - Xử lý giao thức SMTP để nhận và gửi email
 * Hỗ trợ các lệnh: HELO, MAIL FROM, RCPT TO, DATA, QUIT, RSET, NOOP
 */
public class SMTPServer {
    private int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private DatabaseManager dbManager;
    private List<String> serverLog = Collections.synchronizedList(new ArrayList<>());
    private String serverDomain = "localhost";

    public SMTPServer(int port, DatabaseManager dbManager) {
        this.port = port;
        this.dbManager = dbManager;
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                addLog("SMTP Server đã khởi động trên cổng " + port);
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        addLog("Kết nối SMTP mới từ: " + clientSocket.getInetAddress());
                        new Thread(new SMTPHandler(clientSocket)).start();
                    } catch (SocketException e) {
                        if (running) addLog("Lỗi SMTP: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                addLog("Không thể khởi động SMTP Server: " + e.getMessage());
            }
        }, "SMTP-Server-Thread").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            addLog("SMTP Server đã dừng");
        } catch (IOException e) {
            addLog("Lỗi khi dừng SMTP Server: " + e.getMessage());
        }
    }

    public boolean isRunning() { return running; }
    public List<String> getServerLog() { return serverLog; }

    private void addLog(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        serverLog.add("[" + timestamp + "] " + message);
        System.out.println("[SMTP] " + message);
    }

    /**
     * Handler xử lý kết nối SMTP từ client
     */
    private class SMTPHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private String senderEmail = "";
        private List<String> recipientEmails = new ArrayList<>();
        private String clientDomain = "";

        public SMTPHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

                // Gửi banner chào mừng
                sendResponse("220 " + serverDomain + " SMTP Server Ready");

                String line;
                boolean inDataMode = false;
                StringBuilder emailData = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    addLog("C: " + line);

                    if (inDataMode) {
                        if (line.equals(".")) {
                            // Kết thúc dữ liệu email
                            inDataMode = false;
                            processEmail(emailData.toString());
                            emailData = new StringBuilder();
                            sendResponse("250 OK: Message accepted for delivery");
                        } else {
                            // Xử lý byte-stuffing
                            if (line.startsWith("..")) {
                                emailData.append(line.substring(1));
                            } else {
                                emailData.append(line);
                            }
                            emailData.append("\r\n");
                        }
                        continue;
                    }

                    String command = line.toUpperCase();

                    if (command.startsWith("HELO") || command.startsWith("EHLO")) {
                        clientDomain = line.length() > 5 ? line.substring(5).trim() : "unknown";
                        sendResponse("250 Hello " + clientDomain + ", pleased to meet you");

                    } else if (command.startsWith("MAIL FROM:")) {
                        senderEmail = DatabaseManager.normalizeEmail(extractEmail(line.substring(10)));
                        sendResponse("250 OK: Sender <" + senderEmail + "> accepted");

                    } else if (command.startsWith("RCPT TO:")) {
                        String recipient = DatabaseManager.normalizeEmail(extractEmail(line.substring(8)));
                        recipientEmails.add(recipient);
                        sendResponse("250 OK: Recipient <" + recipient + "> accepted");

                    } else if (command.startsWith("DATA")) {
                        if (senderEmail.isEmpty() || recipientEmails.isEmpty()) {
                            sendResponse("503 Bad sequence of commands");
                        } else {
                            sendResponse("354 Start mail input; end with <CRLF>.<CRLF>");
                            inDataMode = true;
                        }

                    } else if (command.startsWith("RSET")) {
                        senderEmail = "";
                        recipientEmails.clear();
                        sendResponse("250 OK: Reset state");

                    } else if (command.startsWith("NOOP")) {
                        sendResponse("250 OK");

                    } else if (command.startsWith("QUIT")) {
                        sendResponse("221 " + serverDomain + " closing connection");
                        break;

                    } else {
                        sendResponse("502 Command not implemented");
                    }
                }
            } catch (IOException e) {
                addLog("Lỗi xử lý SMTP: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException e) {}
                addLog("Đóng kết nối SMTP");
            }
        }

        private void sendResponse(String response) {
            writer.println(response);
            addLog("S: " + response);
        }

        private String extractEmail(String input) {
            input = input.trim();
            if (input.startsWith("<") && input.endsWith(">")) {
                return input.substring(1, input.length() - 1);
            }
            return input;
        }

        private void processEmail(String rawData) {
            // Parse email headers và body
            String subject = "";
            String body = "";
            String from = senderEmail;
            Date emailDate = new Date();
            boolean headersDone = false;
            StringBuilder bodyBuilder = new StringBuilder();
            String[] lines = rawData.split("\r?\n");

            for (String l : lines) {
                if (!headersDone) {
                    if (l.trim().isEmpty()) {
                        headersDone = true;
                    } else if (l.toLowerCase().startsWith("subject:")) {
                        subject = l.substring(8).trim();
                    } else if (l.toLowerCase().startsWith("from:")) {
                        String parsedFrom = extractEmail(l.substring(5).trim());
                        if (!parsedFrom.isEmpty()) from = parsedFrom;
                    } else if (l.toLowerCase().startsWith("date:")) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
                            emailDate = sdf.parse(l.substring(5).trim());
                        } catch (Exception ignored) {}
                    }
                } else {
                    bodyBuilder.append(l).append("\n");
                }
            }
            body = bodyBuilder.toString().trim();

            // Lưu email vào database cho mỗi người nhận
            for (String recipient : recipientEmails) {
                Email email = new Email();
                email.setSender(from);
                email.setRecipient(recipient);
                email.setSubject(subject);
                email.setBody(body);
                email.setDate(emailDate);
                email.setRead(false);
                email.setFolder("INBOX");
                dbManager.saveEmail(email);
                addLog("Email đã lưu: " + from + " -> " + recipient + " [" + subject + "]");
            }

            // Lưu bản sao vào thư mục SENT của người gửi
            if (from != null && !from.trim().isEmpty()) {
                Email sentEmail = new Email();
                sentEmail.setSender(from);
                sentEmail.setRecipient(String.join(", ", recipientEmails));
                sentEmail.setSubject(subject);
                sentEmail.setBody(body);
                sentEmail.setDate(emailDate);
                sentEmail.setRead(true);
                sentEmail.setFolder("SENT");
                dbManager.saveEmail(sentEmail);
                addLog("Email đã lưu vào SENT: " + from + " [" + subject + "]");
            }

            // Reset trạng thái
            senderEmail = "";
            recipientEmails.clear();
        }
    }
}
