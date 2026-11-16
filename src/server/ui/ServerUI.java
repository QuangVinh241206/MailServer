package server.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.File;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

import database.DatabaseManager;
import model.Email;
import model.User;
import server.MailServerApp;

/**
 * Giao diện điều khiển Máy chủ Mail Server độc lập (SMTP & POP3)
 * Hiển thị trạng thái, nhật ký tương tác mạng và cơ sở dữ liệu
 */
public class ServerUI extends Application {

    private static final int SMTP_PORT = 2525;
    private static final int POP3_PORT = 1110;

    private MailServerApp mailServer;
    private DatabaseManager dbManager;

    private Label smtpStatusBadge;
    private Label pop3StatusBadge;
    private Label clientCountLabel;
    private TextArea logArea;
    private TableView<Email> emailTable;
    private TableView<User> userTable;
    private Tab tabEmails;
    private Tab tabUsers;
    private Button toggleServerBtn;

    private Thread logPollerThread;
    private volatile boolean running = true;
    private int lastLogSize = 0;

    @Override
    public void start(Stage primaryStage) {
        dbManager = new DatabaseManager();
        mailServer = new MailServerApp(SMTP_PORT, POP3_PORT, dbManager);

        // Khởi động server
        mailServer.startAll();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f2f5;");

        // Top Header
        root.setTop(createHeader());

        // Center TabPane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-font-size: 13px;");

        tabPane.getTabs().addAll(
                createLogTab(),
                createEmailTableTab(),
                createUserTableTab()
        );

        root.setCenter(tabPane);

        // Bottom Footer
        root.setBottom(createFooter());

        // Poller cập nhật log thời gian thực
        startLogPoller();

        Scene scene = new Scene(root, 1000, 680);
        primaryStage.setTitle("Hệ Thống Mail Server (SMTP: " + SMTP_PORT + " | POP3: " + POP3_PORT + ")");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            running = false;
            if (mailServer != null) mailServer.stopAll();
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }

    private VBox createHeader() {
        VBox header = new VBox(12);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setStyle("-fx-background-color: #1e293b;");

        // Title Row
        HBox titleRow = new HBox(15);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("📡 MAIL SERVER DASHBOARD");
        logo.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 20px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openClientBtn = new Button("🚀 Mở Mail Client mới");
        openClientBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        openClientBtn.setOnAction(e -> launchNewClient());

        toggleServerBtn = new Button("⏹ Dừng Server");
        toggleServerBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        toggleServerBtn.setOnAction(e -> toggleServer());

        titleRow.getChildren().addAll(logo, spacer, openClientBtn, toggleServerBtn);

        // Status Cards Row
        HBox cardsRow = new HBox(15);

        cardsRow.getChildren().addAll(
                createStatusCard("SMTP Server", "Cổng " + SMTP_PORT + " (Gửi thư)", "🟢 ĐANG CHẠY", "#10b981", true),
                createStatusCard("POP3 Server", "Cổng " + POP3_PORT + " (Nhận thư)", "🟢 ĐANG CHẠY", "#10b981", false),
                createDatabaseCard()
        );

        // LAN IP Banner cho kết nối Demo 2 máy
        String lanIp = getLocalLanIp();
        HBox ipBanner = new HBox(12);
        ipBanner.setAlignment(Pos.CENTER_LEFT);
        ipBanner.setPadding(new Insets(8, 14, 8, 14));
        ipBanner.setStyle("-fx-background-color: #0f172a; -fx-background-radius: 6; -fx-border-color: #38bdf8; -fx-border-radius: 6; -fx-border-width: 1;");

        Label ipInfo = new Label("🌐 IP Máy Chủ (LAN / Wi-Fi): " + lanIp + "  |  SMTP: " + SMTP_PORT + "  |  POP3: " + POP3_PORT);
        ipInfo.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 13px; -fx-font-weight: bold;");

        Button copyIpBtn = new Button("📋 Copy IP");
        copyIpBtn.setStyle("-fx-background-color: #0284c7; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-cursor: hand; -fx-background-radius: 4;");
        copyIpBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(lanIp);
            clipboard.setContent(content);
            copyIpBtn.setText("✓ Đã copy IP!");
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (Exception ignored) {}
                Platform.runLater(() -> copyIpBtn.setText("📋 Copy IP"));
            }).start();
        });

        Label guideHint = new Label("💡 Demo 2 máy: Trên Máy 2, mở Mail Client và nhập Server IP: " + lanIp);
        guideHint.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Region bannerSpacer = new Region();
        HBox.setHgrow(bannerSpacer, Priority.ALWAYS);

        ipBanner.getChildren().addAll(ipInfo, copyIpBtn, bannerSpacer, guideHint);

        header.getChildren().addAll(titleRow, cardsRow, ipBanner);
        return header;
    }

    public static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.getHostAddress().startsWith("169.254")) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private VBox createStatusCard(String title, String subtitle, String status, String color, boolean isSmtp) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 15, 10, 15));
        card.setStyle("-fx-background-color: #334155; -fx-background-radius: 8;");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label badge = new Label(status);
        badge.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        if (isSmtp) smtpStatusBadge = badge;
        else pop3StatusBadge = badge;

        card.getChildren().addAll(titleLbl, subLbl, badge);
        return card;
    }

    private VBox createDatabaseCard() {
        VBox card = new VBox(4);
        card.setPadding(new Insets(10, 15, 10, 15));
        card.setStyle("-fx-background-color: #334155; -fx-background-radius: 8;");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label titleLbl = new Label("Cơ sở dữ liệu");
        titleLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label subLbl = new Label("SQLite: mailserver.db");
        subLbl.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        clientCountLabel = new Label("🟢 WAL Mode - Sẵn sàng đa kết nối");
        clientCountLabel.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 12px; -fx-font-weight: bold;");

        card.getChildren().addAll(titleLbl, subLbl, clientCountLabel);
        return card;
    }

    private Tab createLogTab() {
        Tab tab = new Tab("📋 Nhật ký hoạt động (Logs)");

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("Nhật ký tương tác thời gian thực từ các Mail Client kết nối:");
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("🗑 Xóa Log");
        clearBtn.setStyle("-fx-cursor: hand;");
        clearBtn.setOnAction(e -> {
            if (mailServer.getSmtpServer() != null) mailServer.getSmtpServer().getServerLog().clear();
            if (mailServer.getPop3Server() != null) mailServer.getPop3Server().getServerLog().clear();
            logArea.clear();
            lastLogSize = 0;
        });

        toolbar.getChildren().addAll(label, spacer, clearBtn);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px; -fx-control-inner-background: #0f172a; -fx-text-fill: #38bdf8;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        content.getChildren().addAll(toolbar, logArea);
        tab.setContent(content);
        return tab;
    }

    private Tab createEmailTableTab() {
        tabEmails = new Tab("📨 Bảng Emails trong Database");

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label("Toàn bộ thư lưu trữ trong Database máy chủ:");
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Làm mới bảng Email");
        refreshBtn.setStyle("-fx-cursor: hand; -fx-font-weight: bold;");
        refreshBtn.setOnAction(e -> refreshEmailTable());

        toolbar.getChildren().addAll(lbl, spacer, refreshBtn);

        emailTable = new TableView<>();
        VBox.setVgrow(emailTable, Priority.ALWAYS);

        TableColumn<Email, String> colFrom = new TableColumn<>("Người gửi");
        colFrom.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSender()));
        colFrom.setPrefWidth(200);

        TableColumn<Email, String> colTo = new TableColumn<>("Người nhận");
        colTo.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getRecipient()));
        colTo.setPrefWidth(200);

        TableColumn<Email, String> colSubject = new TableColumn<>("Tiêu đề thư");
        colSubject.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSubject()));
        colSubject.setPrefWidth(250);

        TableColumn<Email, String> colFolder = new TableColumn<>("Thư mục");
        colFolder.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getFolder()));
        colFolder.setPrefWidth(90);

        TableColumn<Email, String> colDate = new TableColumn<>("Thời gian");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        colDate.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getDate() != null ? sdf.format(data.getValue().getDate()) : ""));
        colDate.setPrefWidth(150);

        TableColumn<Email, String> colRead = new TableColumn<>("Đã đọc");
        colRead.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().isRead() ? "Đã đọc" : "Chưa"));
        colRead.setPrefWidth(70);

        emailTable.getColumns().addAll(colFrom, colTo, colSubject, colFolder, colDate, colRead);
        refreshEmailTable();

        content.getChildren().addAll(toolbar, emailTable);
        tabEmails.setContent(content);
        return tabEmails;
    }

    private Tab createUserTableTab() {
        tabUsers = new Tab("👥 Bảng Users trong Database");

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label("Danh sách tài khoản người dùng đăng ký trên hệ thống:");
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 Làm mới bảng User");
        refreshBtn.setStyle("-fx-cursor: hand; -fx-font-weight: bold;");
        refreshBtn.setOnAction(e -> refreshUserTable());

        toolbar.getChildren().addAll(lbl, spacer, refreshBtn);

        userTable = new TableView<>();
        VBox.setVgrow(userTable, Priority.ALWAYS);

        TableColumn<User, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()));
        colId.setPrefWidth(60);

        TableColumn<User, String> colEmail = new TableColumn<>("Địa chỉ Email");
        colEmail.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getEmail()));
        colEmail.setPrefWidth(280);

        TableColumn<User, String> colPass = new TableColumn<>("Mật khẩu");
        colPass.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getPassword()));
        colPass.setPrefWidth(180);

        TableColumn<User, String> colName = new TableColumn<>("Tên hiển thị");
        colName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDisplayName()));
        colName.setPrefWidth(220);

        userTable.getColumns().addAll(colId, colEmail, colPass, colName);
        refreshUserTable();

        content.getChildren().addAll(toolbar, userTable);
        tabUsers.setContent(content);
        return tabUsers;
    }

    private HBox createFooter() {
        HBox footer = new HBox(15);
        footer.setPadding(new Insets(8, 20, 8, 20));
        footer.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");
        footer.setAlignment(Pos.CENTER_LEFT);

        Label note = new Label("💡 Gợi ý: Chạy file 'run_client.bat' nhiều lần để mở nhiều cửa sổ Mail Client độc lập thử nghiệm gửi nhận.");
        note.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label dbPath = new Label("DB: " + new File("mailserver.db").getAbsolutePath());
        dbPath.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        footer.getChildren().addAll(note, spacer, dbPath);
        return footer;
    }

    private void refreshEmailTable() {
        List<Email> emails = dbManager.getAllEmails();
        emailTable.setItems(FXCollections.observableArrayList(emails));
        if (tabEmails != null) tabEmails.setText("📨 Bảng Emails trong Database (" + emails.size() + ")");
    }

    private void refreshUserTable() {
        List<User> users = dbManager.getAllUsers();
        userTable.setItems(FXCollections.observableArrayList(users));
        if (tabUsers != null) tabUsers.setText("👥 Bảng Users trong Database (" + users.size() + ")");
    }

    private void toggleServer() {
        if (mailServer.isRunning()) {
            mailServer.stopAll();
            toggleServerBtn.setText("▶ Khởi động Server");
            toggleServerBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
            smtpStatusBadge.setText("🔴 ĐÃ DỪNG");
            smtpStatusBadge.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px; -fx-font-weight: bold;");
            pop3StatusBadge.setText("🔴 ĐÃ DỪNG");
            pop3StatusBadge.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12px; -fx-font-weight: bold;");
            appendLog("[SERVER] Đã dừng toàn bộ dịch vụ SMTP và POP3.");
        } else {
            mailServer.startAll();
            toggleServerBtn.setText("⏹ Dừng Server");
            toggleServerBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
            smtpStatusBadge.setText("🟢 ĐANG CHẠY");
            smtpStatusBadge.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12px; -fx-font-weight: bold;");
            pop3StatusBadge.setText("🟢 ĐANG CHẠY");
            pop3StatusBadge.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12px; -fx-font-weight: bold;");
            appendLog("[SERVER] Đã khởi động lại dịch vụ SMTP (port " + SMTP_PORT + ") và POP3 (port " + POP3_PORT + ").");
        }
    }

    private void launchNewClient() {
        try {
            new ProcessBuilder("cmd.exe", "/c", "start run_client.bat").start();
        } catch (Exception e) {
            appendLog("[SERVER] Lỗi mở client: " + e.getMessage());
        }
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
        });
    }

    private void startLogPoller() {
        logPollerThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    break;
                }

                if (mailServer == null) continue;

                int smtpCount = mailServer.getSmtpServer() != null ? mailServer.getSmtpServer().getServerLog().size() : 0;
                int pop3Count = mailServer.getPop3Server() != null ? mailServer.getPop3Server().getServerLog().size() : 0;
                int currentTotal = smtpCount + pop3Count;

                if (currentTotal != lastLogSize) {
                    lastLogSize = currentTotal;
                    Platform.runLater(() -> {
                        StringBuilder sb = new StringBuilder();
                        if (mailServer.getSmtpServer() != null) {
                            for (String l : mailServer.getSmtpServer().getServerLog()) {
                                sb.append(l).append("\n");
                            }
                        }
                        if (mailServer.getPop3Server() != null) {
                            for (String l : mailServer.getPop3Server().getServerLog()) {
                                sb.append(l).append("\n");
                            }
                        }
                        logArea.setText(sb.toString());
                        logArea.positionCaret(logArea.getText().length());
                    });
                }
            }
        }, "Server-Log-Poller");
        logPollerThread.setDaemon(true);
        logPollerThread.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
