package ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;

import java.text.SimpleDateFormat;
import java.util.*;

import model.Email;
import client.SMTPClient;
import client.POP3Client;
import database.DatabaseManager;

/**
 * Giao diện Mail Client tương tự Gmail (Chạy độc lập, hỗ trợ mở nhiều Client cùng lúc)
 * Sử dụng JavaFX
 */
public class MailClientUI extends Application {

    // Database (kết nối WAL mode an toàn cho nhiều tiến trình)
    private DatabaseManager dbManager;
    private server.MailServerApp embeddedServer = null;
    private volatile boolean autoRefreshRunning = true;

    // UI Components
    private BorderPane mainLayout;
    private VBox sideBar;
    private VBox emailListPane;
    private VBox emailDetailPane;
    private TextField searchField;
    private Label statusLabel;
    private Label userLabel;
    private Label folderTitle;

    // State
    private String serverHost = "localhost";
    private String currentUser = "";
    private String currentPassword = "";
    private String currentFolder = "INBOX";
    private ObservableList<Email> currentEmails = FXCollections.observableArrayList();
    private Email selectedEmail = null;

    public boolean isRemoteServer() {
        return serverHost != null &&
               !serverHost.equalsIgnoreCase("localhost") &&
               !serverHost.equals("127.0.0.1") &&
               !serverHost.trim().isEmpty();
    }

    // Server ports
    private static final int SMTP_PORT = 2525;
    private static final int POP3_PORT = 1110;

    // Colors (Gmail-like)
    private static final String PRIMARY_COLOR = "#1a73e8";
    private static final String PRIMARY_HOVER = "#1557b0";
    private static final String BG_COLOR = "#f6f8fc";
    private static final String SIDEBAR_BG = "#f6f8fc";
    private static final String WHITE = "#ffffff";
    private static final String TEXT_PRIMARY = "#202124";
    private static final String TEXT_SECONDARY = "#5f6368";
    private static final String BORDER_COLOR = "#e0e0e0";
    private static final String SELECTED_BG = "#e8f0fe";
    private static final String HOVER_BG = "#edf2fc";
    private static final String UNREAD_BG = "#ffffff";
    private static final String READ_BG = "#f2f2f2";
    private static final String RED_COLOR = "#d93025";
    private static final String STAR_COLOR = "#f4b400";

    @Override
    public void start(Stage primaryStage) {
        // Nhận tham số dòng lệnh nếu có (ví dụ: java ui.MailClientUI 192.168.1.88)
        try {
            List<String> rawArgs = getParameters() != null ? getParameters().getRaw() : Collections.emptyList();
            if (rawArgs != null && !rawArgs.isEmpty() && !rawArgs.get(0).trim().isEmpty()) {
                serverHost = rawArgs.get(0).trim();
            }
        } catch (Exception ignored) {}

        // Khởi động database (WAL mode an toàn cho nhiều client cùng chạy)
        dbManager = new DatabaseManager();

        // Hiện dialog đăng nhập trước (người dùng có thể nhập Server IP cho Máy 2)
        if (!showLoginDialog(primaryStage)) {
            if (embeddedServer != null) embeddedServer.stopAll();
            if (dbManager != null) dbManager.close();
            Platform.exit();
            return;
        }

        // Đảm bảo máy chủ Mail Server đang chạy nếu người dùng chọn localhost và chưa có server chạy ngoài
        if (!isRemoteServer()) {
            ensureServerRunning();
        }

        // Tạo giao diện chính
        mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: " + BG_COLOR + ";");

        // Top bar
        mainLayout.setTop(createTopBar());

        // Sidebar
        sideBar = createSideBar();
        mainLayout.setLeft(sideBar);

        // Center - Email list + Detail
        SplitPane centerSplit = new SplitPane();
        emailListPane = createEmailListPane();
        emailDetailPane = createEmailDetailPane(null);

        ScrollPane listScroll = new ScrollPane(emailListPane);
        listScroll.setFitToWidth(true);
        listScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        ScrollPane detailScroll = new ScrollPane(emailDetailPane);
        detailScroll.setFitToWidth(true);
        detailScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        centerSplit.getItems().addAll(listScroll, detailScroll);
        centerSplit.setDividerPositions(0.4);
        centerSplit.setStyle("-fx-background-color: " + BG_COLOR + ";");

        mainLayout.setCenter(centerSplit);

        // Bottom status bar
        mainLayout.setBottom(createStatusBar());

        // Load emails
        refreshEmailList();

        Scene scene = new Scene(mainLayout, 1200, 750);
        scene.getStylesheets().add("data:text/css," + getCSS());

        // Phím tắt Esc để tắt mail đang xem
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && selectedEmail != null) {
                closeEmailDetail();
            }
        });

        String serverTag = isRemoteServer() ? " [Máy chủ: " + serverHost + "]" : " [Localhost]";
        primaryStage.setTitle("Gmail Client - " + currentUser + serverTag);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            autoRefreshRunning = false;
            if (embeddedServer != null) {
                embeddedServer.stopAll();
            }
            if (dbManager != null) dbManager.close();
            Platform.exit();
        });
        primaryStage.show();

        // Tự động làm mới kiểm tra email mới đến theo thời gian thực (mỗi 1.5s)
        startAutoRefresh();
    }

    private void ensureServerRunning() {
        if (!isPortOpen(SMTP_PORT) || !isPortOpen(POP3_PORT)) {
            try {
                embeddedServer = new server.MailServerApp(SMTP_PORT, POP3_PORT, dbManager);
                embeddedServer.startAll();
                System.out.println("[Client] Server chưa chạy bên ngoài, đã tự động khởi động máy chủ nền (port " + SMTP_PORT + " & " + POP3_PORT + ")");
            } catch (Exception e) {
                System.err.println("[Client] Không thể tự khởi động server: " + e.getMessage());
            }
        }
    }

    private void startAutoRefresh() {
        Thread t = new Thread(() -> {
            while (autoRefreshRunning) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                }

                // Nếu kết nối qua máy chủ từ xa (Demo 2 máy), đồng bộ POP3 nền
                if (isRemoteServer()) {
                    try {
                        POP3Client pop3 = new POP3Client();
                        if (pop3.connect(serverHost, POP3_PORT)) {
                            if (pop3.login(currentUser, currentPassword)) {
                                List<Email> remoteEmails = pop3.retrieveAllMessages();
                                for (Email rem : remoteEmails) {
                                    dbManager.saveEmail(rem);
                                }
                            }
                            pop3.disconnect();
                        }
                    } catch (Exception ignored) {}
                }

                Platform.runLater(() -> {
                    // Nếu người dùng không đang tìm kiếm và cửa sổ đang hoạt động
                    if (searchField == null || searchField.getText().trim().isEmpty()) {
                        refreshEmailListSilently();
                    }
                });
            }
        }, "Client-Auto-Refresh");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Dialog đăng nhập
     */
    private boolean showLoginDialog(Stage owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Đăng nhập Mail");
        dialog.setResizable(false);

        VBox root = new VBox(15);
        root.setPadding(new Insets(30, 40, 30, 40));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: white;");

        // Logo / Title
        Label logo = new Label("\u2709 Mail Server");
        logo.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_COLOR + ";");

        Label subtitle = new Label("Đăng nhập để tiếp tục");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        // Server Host field (Hỗ trợ demo 2 máy qua mạng LAN/Wi-Fi)
        HBox serverBox = new HBox(8);
        serverBox.setAlignment(Pos.CENTER_LEFT);
        serverBox.setPrefWidth(300);
        Label serverLabel = new Label("Máy chủ IP:");
        serverLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-min-width: 80;");
        TextField serverField = new TextField(serverHost != null ? serverHost : "localhost");
        serverField.setPromptText("localhost hoặc 192.168.x.x");
        serverField.setStyle("-fx-font-size: 13px; -fx-padding: 8; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-background-radius: 5;");
        HBox.setHgrow(serverField, Priority.ALWAYS);
        serverBox.getChildren().addAll(serverLabel, serverField);

        // Email field
        TextField emailField = new TextField();
        emailField.setPromptText("Email (ví dụ: user1@localhost)");
        emailField.setPrefWidth(300);
        emailField.setStyle("-fx-font-size: 14px; -fx-padding: 10; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-background-radius: 5;");

        // Password field
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mật khẩu");
        passwordField.setPrefWidth(300);
        passwordField.setStyle("-fx-font-size: 14px; -fx-padding: 10; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-background-radius: 5;");

        // Error label
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: " + RED_COLOR + "; -fx-font-size: 12px;");

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button loginBtn = new Button("Đăng nhập");
        loginBtn.setStyle("-fx-background-color: " + PRIMARY_COLOR + "; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-padding: 10 30; -fx-background-radius: 5; -fx-cursor: hand;");

        Button registerBtn = new Button("Đăng ký");
        registerBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-size: 14px; " +
                "-fx-padding: 10 20; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;");

        buttonBox.getChildren().addAll(loginBtn, registerBtn);

        // Default accounts info
        Label infoLabel = new Label("Tài khoản mặc định:\nuser1@localhost / 123456\nuser2@localhost / 123456\nadmin@localhost / admin\n💡 Demo 2 máy: Nhập IP Máy chủ (ví dụ 192.168.1.88)");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-padding: 10 0 0 0;");
        infoLabel.setTextAlignment(TextAlignment.CENTER);

        root.getChildren().addAll(logo, subtitle, serverBox, emailField, passwordField, errorLabel, buttonBox, infoLabel);

        final boolean[] success = {false};

        loginBtn.setOnAction(e -> {
            String sHost = serverField.getText().trim();
            serverHost = sHost.isEmpty() ? "localhost" : sHost;
            String email = emailField.getText().trim();
            String pass = passwordField.getText().trim();
            if (email.isEmpty() || pass.isEmpty()) {
                errorLabel.setText("Vui lòng nhập email và mật khẩu");
                return;
            }

            if (isRemoteServer()) {
                loginBtn.setDisable(true);
                errorLabel.setStyle("-fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-size: 12px;");
                errorLabel.setText("Đang kết nối Máy chủ " + serverHost + "...");
                new Thread(() -> {
                    POP3Client pop3 = new POP3Client();
                    if (!pop3.connect(serverHost, POP3_PORT)) {
                        Platform.runLater(() -> {
                            loginBtn.setDisable(false);
                            errorLabel.setStyle("-fx-text-fill: " + RED_COLOR + "; -fx-font-size: 12px;");
                            errorLabel.setText("Không thể kết nối Máy chủ " + serverHost + ":" + POP3_PORT);
                        });
                        return;
                    }
                    boolean ok = pop3.login(email, pass);
                    if (ok) {
                        List<Email> remoteEmails = pop3.retrieveAllMessages();
                        for (Email rem : remoteEmails) {
                            dbManager.saveEmail(rem);
                        }
                        pop3.disconnect();
                        Platform.runLater(() -> {
                            currentUser = email.contains("@") ? email : email + "@localhost";
                            currentPassword = pass;
                            success[0] = true;
                            dialog.close();
                        });
                    } else {
                        pop3.disconnect();
                        Platform.runLater(() -> {
                            loginBtn.setDisable(false);
                            errorLabel.setStyle("-fx-text-fill: " + RED_COLOR + "; -fx-font-size: 12px;");
                            errorLabel.setText("Email hoặc mật khẩu không đúng trên máy chủ");
                        });
                    }
                }).start();
            } else {
                if (dbManager.authenticateUser(email, pass)) {
                    currentUser = email.contains("@") ? email : email + "@localhost";
                    currentPassword = pass;
                    success[0] = true;
                    dialog.close();
                } else {
                    errorLabel.setText("Email hoặc mật khẩu không đúng");
                }
            }
        });

        registerBtn.setOnAction(e -> {
            String sHost = serverField.getText().trim();
            serverHost = sHost.isEmpty() ? "localhost" : sHost;
            String email = emailField.getText().trim();
            String pass = passwordField.getText().trim();
            if (email.isEmpty() || pass.isEmpty()) {
                errorLabel.setText("Vui lòng nhập email và mật khẩu");
                return;
            }
            final String regEmail = email.contains("@") ? email : email + "@localhost";
            final String displayName = regEmail.split("@")[0];

            if (isRemoteServer()) {
                registerBtn.setDisable(true);
                errorLabel.setStyle("-fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-size: 12px;");
                errorLabel.setText("Đang đăng ký trên Máy chủ " + serverHost + "...");
                new Thread(() -> {
                    POP3Client pop3 = new POP3Client();
                    if (!pop3.connect(serverHost, POP3_PORT)) {
                        Platform.runLater(() -> {
                            registerBtn.setDisable(false);
                            errorLabel.setStyle("-fx-text-fill: " + RED_COLOR + "; -fx-font-size: 12px;");
                            errorLabel.setText("Không thể kết nối Máy chủ " + serverHost + ":" + POP3_PORT);
                        });
                        return;
                    }
                    boolean regOk = pop3.register(regEmail, pass, displayName);
                    pop3.disconnect();
                    Platform.runLater(() -> {
                        registerBtn.setDisable(false);
                        if (regOk) {
                            currentUser = regEmail;
                            currentPassword = pass;
                            success[0] = true;
                            dialog.close();
                        } else {
                            errorLabel.setStyle("-fx-text-fill: " + RED_COLOR + "; -fx-font-size: 12px;");
                            errorLabel.setText("Email đã tồn tại trên máy chủ hoặc lỗi đăng ký");
                        }
                    });
                }).start();
            } else {
                if (dbManager.registerUser(regEmail, pass, displayName)) {
                    currentUser = regEmail;
                    currentPassword = pass;
                    success[0] = true;
                    dialog.close();
                } else {
                    errorLabel.setText("Email đã tồn tại");
                }
            }
        });

        passwordField.setOnAction(e -> loginBtn.fire());

        dialog.setScene(new Scene(root, 420, 500));
        dialog.showAndWait();

        return success[0];
    }

    /**
     * Tạo thanh trên cùng (Top bar)
     */
    private HBox createTopBar() {
        HBox topBar = new HBox(15);
        topBar.setPadding(new Insets(8, 20, 8, 20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0;");

        // Logo
        Label logo = new Label("\u2709 Mail");
        logo.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_COLOR + ";");

        // Search bar
        searchField = new TextField();
        searchField.setPromptText("Tìm kiếm email...");
        searchField.setPrefWidth(500);
        searchField.setStyle("-fx-font-size: 14px; -fx-padding: 8 15; -fx-background-color: #eaf1fb; " +
                "-fx-border-color: transparent; -fx-background-radius: 8;");
        searchField.setOnAction(e -> searchEmails());

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Refresh button
        Button refreshBtn = new Button("🔄 Làm mới");
        refreshBtn.setStyle("-fx-font-size: 12px; -fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 5 10; -fx-text-fill: " + TEXT_SECONDARY + ";");
        refreshBtn.setOnAction(e -> refreshEmailList());
        refreshBtn.setTooltip(new Tooltip("Làm mới danh sách"));

        // POP3 Sync button
        Button pop3SyncBtn = new Button("📥 POP3 Sync");
        pop3SyncBtn.setStyle("-fx-font-size: 12px; -fx-background-color: #e8f0fe; -fx-cursor: hand; -fx-padding: 5 10; -fx-text-fill: " + PRIMARY_COLOR + "; -fx-border-color: " + PRIMARY_COLOR + "; -fx-border-radius: 4; -fx-background-radius: 4;");
        pop3SyncBtn.setTooltip(new Tooltip("Đồng bộ thư từ POP3 Server"));
        pop3SyncBtn.setOnAction(e -> syncViaPop3());

        // Server status
        Button serverBtn = new Button("⚙ Server Status");
        serverBtn.setStyle("-fx-font-size: 12px; -fx-background-color: transparent; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-cursor: hand;");
        serverBtn.setOnAction(e -> showServerDialog());

        // Nút mở thêm Client mới (chạy nhiều client cùng lúc)
        Button newClientBtn = new Button("➕ Mở Client mới");
        newClientBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 4; -fx-padding: 4 10;");
        newClientBtn.setTooltip(new Tooltip("Mở thêm một cửa sổ Client độc lập để kiểm tra gửi nhận đa tài khoản"));
        newClientBtn.setOnAction(e -> {
            try {
                new ProcessBuilder("cmd.exe", "/c", "start run_client.bat").start();
            } catch (Exception ex) {
                System.err.println("Lỗi mở client: " + ex.getMessage());
            }
        });

        // Switch user button
        Button switchUserBtn = new Button("Đổi tài khoản");
        switchUserBtn.setStyle("-fx-font-size: 11px; -fx-background-color: transparent; -fx-text-fill: " + PRIMARY_COLOR + "; -fx-cursor: hand; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 4; -fx-padding: 4 8;");
        switchUserBtn.setOnAction(e -> {
            Stage stage = (Stage) mainLayout.getScene().getWindow();
            if (showLoginDialog(stage)) {
                stage.setTitle("Gmail Client - " + currentUser);
                mainLayout.setTop(createTopBar());
                currentFolder = "INBOX";
                selectedEmail = null;
                emailDetailPane = createEmailDetailPane(null);
                SplitPane cp = (SplitPane) mainLayout.getCenter();
                if (cp != null && cp.getItems().size() > 1) {
                    ScrollPane ds = new ScrollPane(emailDetailPane);
                    ds.setFitToWidth(true);
                    ds.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
                    cp.getItems().set(1, ds);
                }
                refreshEmailList();
            }
        });

        // User info
        String initial = currentUser.substring(0, 1).toUpperCase();
        Label avatar = new Label(initial);
        avatar.setStyle("-fx-background-color: " + PRIMARY_COLOR + "; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-font-weight: bold; -fx-min-width: 35; -fx-min-height: 35; -fx-max-width: 35; -fx-max-height: 35; " +
                "-fx-alignment: center; -fx-background-radius: 50;");

        userLabel = new Label(currentUser);
        userLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-weight: bold;");

        topBar.getChildren().addAll(logo, searchField, spacer, refreshBtn, pop3SyncBtn, serverBtn, newClientBtn, avatar, userLabel, switchUserBtn);
        return topBar;
    }

    /**
     * Tạo sidebar bên trái
     */
    private VBox createSideBar() {
        VBox sidebar = new VBox(2);
        sidebar.setPrefWidth(220);
        sidebar.setPadding(new Insets(10, 8, 10, 8));
        sidebar.setStyle("-fx-background-color: " + SIDEBAR_BG + ";");

        // Nút Soạn thư
        Button composeBtn = new Button("   + Soạn thư");
        composeBtn.setMaxWidth(Double.MAX_VALUE);
        composeBtn.setStyle("-fx-background-color: " + PRIMARY_COLOR + "; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-font-weight: bold; -fx-padding: 12 20; -fx-background-radius: 16; -fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);");
        composeBtn.setOnAction(e -> showComposeDialog());

        sidebar.getChildren().add(composeBtn);
        sidebar.getChildren().add(new Label("")); // spacer

        // Menu items
        int unread = dbManager.getUnreadCount(currentUser);
        String[][] menuItems = {
            {"INBOX", "Hộp thư đến", unread > 0 ? String.valueOf(unread) : ""},
            {"STARRED", "Có gắn dấu sao", ""},
            {"SENT", "Đã gửi", ""},
            {"DRAFTS", "Thư nháp", ""},
            {"TRASH", "Thùng rác", ""}
        };

        for (String[] item : menuItems) {
            HBox menuItem = createMenuItem(item[0], item[1], item[2]);
            sidebar.getChildren().add(menuItem);
        }

        // Separator
        Separator sep = new Separator();
        sep.setStyle("-fx-padding: 10 0;");
        sidebar.getChildren().add(sep);

        // Labels section
        Label labelsTitle = new Label("  Nhãn");
        labelsTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-weight: bold; -fx-padding: 5 0;");
        sidebar.getChildren().add(labelsTitle);

        return sidebar;
    }

    /**
     * Tạo một mục menu sidebar
     */
    private HBox createMenuItem(String folder, String text, String badge) {
        HBox item = new HBox(10);
        item.setPadding(new Insets(8, 12, 8, 12));
        item.setAlignment(Pos.CENTER_LEFT);
        item.setStyle("-fx-background-radius: 0 16 16 0; -fx-cursor: hand;" +
                (folder.equals(currentFolder) ? "-fx-background-color: " + SELECTED_BG + ";" : ""));

        // Icon text based on folder
        String iconText;
        switch(folder) {
            case "INBOX": iconText = "[IN]"; break;
            case "STARRED": iconText = "[*]"; break;
            case "SENT": iconText = "[>>]"; break;
            case "DRAFTS": iconText = "[D]"; break;
            case "TRASH": iconText = "[X]"; break;
            default: iconText = "[-]";
        }

        Label iconLabel = new Label(iconText);
        iconLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        Label textLabel = new Label(text);
        textLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_PRIMARY + ";" +
                (folder.equals(currentFolder) ? "-fx-font-weight: bold; -fx-text-fill: " + PRIMARY_COLOR + ";" : ""));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        item.getChildren().addAll(iconLabel, textLabel, spacer);

        if (!badge.isEmpty()) {
            Label badgeLabel = new Label(badge);
            badgeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + PRIMARY_COLOR + "; -fx-font-weight: bold;");
            item.getChildren().add(badgeLabel);
        }

        item.setOnMouseEntered(e -> {
            if (!folder.equals(currentFolder))
                item.setStyle("-fx-background-color: " + HOVER_BG + "; -fx-background-radius: 0 16 16 0; -fx-cursor: hand;");
        });
        item.setOnMouseExited(e -> {
            if (!folder.equals(currentFolder))
                item.setStyle("-fx-background-radius: 0 16 16 0; -fx-cursor: hand;");
        });
        item.setOnMouseClicked(e -> {
            currentFolder = folder;
            refreshEmailList();
            // Refresh sidebar
            mainLayout.setLeft(createSideBar());
        });

        return item;
    }

    /**
     * Tạo danh sách email
     */
    private VBox createEmailListPane() {
        VBox listPane = new VBox(0);
        listPane.setStyle("-fx-background-color: " + WHITE + ";");

        // Header
        folderTitle = new Label(getFolderDisplayName(currentFolder));
        folderTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-padding: 12 16;");
        listPane.getChildren().add(folderTitle);

        // Separator
        Separator sep = new Separator();
        listPane.getChildren().add(sep);

        if (currentEmails.isEmpty()) {
            Label emptyLabel = new Label("Không có email nào");
            emptyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-padding: 50;");
            emptyLabel.setMaxWidth(Double.MAX_VALUE);
            emptyLabel.setAlignment(Pos.CENTER);
            listPane.getChildren().add(emptyLabel);
        } else {
            for (Email email : currentEmails) {
                HBox emailItem = createEmailListItem(email);
                listPane.getChildren().add(emailItem);
            }
        }

        return listPane;
    }

    /**
     * Tạo một dòng email trong danh sách
     */
    private HBox createEmailListItem(Email email) {
        HBox item = new HBox(10);
        item.setPadding(new Insets(10, 16, 10, 16));
        item.setAlignment(Pos.CENTER_LEFT);
        String bgColor = email.isRead() ? READ_BG : UNREAD_BG;
        item.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");

        // Checkbox
        CheckBox cb = new CheckBox();

        // Star
        Label star = new Label(email.isStarred() ? "(*)" : "( )");
        star.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (email.isStarred() ? STAR_COLOR : TEXT_SECONDARY) + "; -fx-cursor: hand;");
        star.setOnMouseClicked(e -> {
            e.consume();
            dbManager.toggleStarred(email.getId());
            refreshEmailList();
        });

        // Sender
        String senderDisplay = email.getSender();
        if (currentFolder.equals("SENT")) {
            senderDisplay = "Tới: " + email.getRecipient();
        }
        Label senderLabel = new Label(senderDisplay);
        senderLabel.setPrefWidth(150);
        senderLabel.setMinWidth(150);
        senderLabel.setStyle("-fx-font-size: 13px; " + (email.isRead() ? "-fx-text-fill: " + TEXT_SECONDARY + ";" : "-fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";"));

        // Subject + preview
        String subjectText = email.getSubject() != null && !email.getSubject().isEmpty() ? email.getSubject() : "(Không có tiêu đề)";
        String preview = email.getBody() != null ? email.getBody().replace("\n", " ") : "";
        if (preview.length() > 60) preview = preview.substring(0, 60) + "...";

        Label subjectLabel = new Label(subjectText);
        subjectLabel.setStyle("-fx-font-size: 13px; " + (email.isRead() ? "-fx-text-fill: " + TEXT_SECONDARY + ";" : "-fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";"));

        Label previewLabel = new Label(" - " + preview);
        previewLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        HBox subjectBox = new HBox(0);
        subjectBox.getChildren().addAll(subjectLabel, previewLabel);
        HBox.setHgrow(subjectBox, Priority.ALWAYS);

        // Date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM");
        SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm");
        Calendar today = Calendar.getInstance();
        Calendar emailDate = Calendar.getInstance();
        emailDate.setTime(email.getDate());
        String dateStr;
        if (today.get(Calendar.YEAR) == emailDate.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == emailDate.get(Calendar.DAY_OF_YEAR)) {
            dateStr = sdfTime.format(email.getDate());
        } else {
            dateStr = sdf.format(email.getDate());
        }

        Label dateLabel = new Label(dateStr);
        dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        // Delete button
        Label deleteBtn = new Label("[Xoa]");
        deleteBtn.setStyle("-fx-font-size: 11px; -fx-cursor: hand; -fx-text-fill: " + RED_COLOR + "; -fx-opacity: 0;");
        deleteBtn.setOnMouseClicked(e -> {
            e.consume();
            if (currentFolder.equals("TRASH")) {
                dbManager.deleteEmail(email.getId());
            } else {
                dbManager.moveToFolder(email.getId(), "TRASH");
            }
            refreshEmailList();
        });

        item.getChildren().addAll(cb, star, senderLabel, subjectBox, dateLabel, deleteBtn);

        // Hover effects
        item.setOnMouseEntered(e -> {
            if (selectedEmail == null || !selectedEmail.getId().equals(email.getId()))
                item.setStyle("-fx-background-color: " + HOVER_BG + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
            deleteBtn.setStyle("-fx-font-size: 11px; -fx-cursor: hand; -fx-text-fill: " + RED_COLOR + "; -fx-opacity: 1;");
        });
        item.setOnMouseExited(e -> {
            if (selectedEmail == null || !selectedEmail.getId().equals(email.getId()))
                item.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
            deleteBtn.setStyle("-fx-font-size: 11px; -fx-cursor: hand; -fx-text-fill: " + RED_COLOR + "; -fx-opacity: 0;");
        });

        // Click to view
        item.setOnMouseClicked(e -> {
            selectedEmail = email;
            dbManager.markAsRead(email.getId());
            email.setRead(true);

            // Update detail pane
            emailDetailPane = createEmailDetailPane(email);
            ScrollPane detailScroll = new ScrollPane(emailDetailPane);
            detailScroll.setFitToWidth(true);
            detailScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

            SplitPane center = (SplitPane) mainLayout.getCenter();
            center.getItems().set(1, detailScroll);

            // Update list item style
            item.setStyle("-fx-background-color: " + SELECTED_BG + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");
        });

        return item;
    }

    /**
     * Tạo panel hiển thị chi tiết email
     */
    private VBox createEmailDetailPane(Email email) {
        VBox detailPane = new VBox(10);
        detailPane.setPadding(new Insets(20));
        detailPane.setStyle("-fx-background-color: " + WHITE + ";");

        if (email == null) {
            Label placeholder = new Label("Chọn một email để xem nội dung");
            placeholder.setStyle("-fx-font-size: 16px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-padding: 100;");
            placeholder.setTextAlignment(TextAlignment.CENTER);
            placeholder.setMaxWidth(Double.MAX_VALUE);
            placeholder.setAlignment(Pos.CENTER);
            detailPane.getChildren().add(placeholder);
            return detailPane;
        }

        // Subject
        Label subjectLabel = new Label(email.getSubject() != null && !email.getSubject().isEmpty() ? email.getSubject() : "(Không có tiêu đề)");
        subjectLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        subjectLabel.setWrapText(true);

        // Folder label
        Label folderLabel = new Label(email.getFolder());
        folderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-padding: 2 8; " +
                "-fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 4; -fx-background-radius: 4;");

        // Close button (Tắt thư đang xem)
        Button closeBtn = new Button("✕ Đóng");
        closeBtn.setTooltip(new Tooltip("Đóng thư đang xem (Esc)"));
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 13px; -fx-cursor: hand; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-padding: 4 12;");
        closeBtn.setOnMouseEntered(ev -> closeBtn.setStyle("-fx-background-color: #fce8e6; -fx-text-fill: " + RED_COLOR + "; -fx-font-size: 13px; -fx-cursor: hand; -fx-border-color: " + RED_COLOR + "; -fx-border-radius: 5; -fx-padding: 4 12;"));
        closeBtn.setOnMouseExited(ev -> closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 13px; -fx-cursor: hand; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-padding: 4 12;"));
        closeBtn.setOnAction(ev -> closeEmailDetail());

        HBox subjectBox = new HBox(10);
        subjectBox.setAlignment(Pos.CENTER_LEFT);
        Region subSpacer = new Region();
        HBox.setHgrow(subSpacer, Priority.ALWAYS);
        subjectBox.getChildren().addAll(subjectLabel, folderLabel, subSpacer, closeBtn);

        // Separator
        Separator sep1 = new Separator();

        // Sender info
        String initial = email.getSender().substring(0, 1).toUpperCase();
        Label avatar = new Label(initial);
        avatar.setStyle("-fx-background-color: #ea4335; -fx-text-fill: white; -fx-font-size: 16px; " +
                "-fx-font-weight: bold; -fx-min-width: 40; -fx-min-height: 40; -fx-max-width: 40; -fx-max-height: 40; " +
                "-fx-alignment: center; -fx-background-radius: 50;");

        VBox senderInfo = new VBox(2);
        Label senderName = new Label(email.getSender());
        senderName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Label dateLine = new Label("tới tôi - " + sdf.format(email.getDate()));
        dateLine.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + ";");
        senderInfo.getChildren().addAll(senderName, dateLine);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Action buttons
        Button replyBtn = new Button("Trả lời");
        replyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-cursor: hand; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-padding: 5 12;");
        replyBtn.setOnAction(e -> showReplyDialog(email, false));

        Button forwardBtn = new Button("Chuyển tiếp");
        forwardBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-cursor: hand; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-padding: 5 12;");
        forwardBtn.setOnAction(e -> showReplyDialog(email, true));

        Label starBtn = new Label(email.isStarred() ? "(*)" : "( )");
        starBtn.setStyle("-fx-font-size: 16px; -fx-text-fill: " + (email.isStarred() ? STAR_COLOR : TEXT_SECONDARY) + "; -fx-cursor: hand;");
        starBtn.setOnMouseClicked(e -> {
            dbManager.toggleStarred(email.getId());
            refreshEmailList();
        });

        HBox senderRow = new HBox(10);
        senderRow.setAlignment(Pos.CENTER_LEFT);
        senderRow.getChildren().addAll(avatar, senderInfo, spacer, starBtn, replyBtn, forwardBtn);

        // To field
        Label toLabel = new Label("Tới: " + email.getRecipient());
        toLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-padding: 0 0 0 50;");

        Separator sep2 = new Separator();

        // Body
        Label bodyLabel = new Label(email.getBody());
        bodyLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + TEXT_PRIMARY + "; -fx-padding: 10 0;");
        bodyLabel.setWrapText(true);

        // Bottom actions
        Separator sep3 = new Separator();

        HBox bottomActions = new HBox(10);
        Button replyBtn2 = new Button("Trả lời");
        replyBtn2.setStyle("-fx-background-color: " + PRIMARY_COLOR + "; -fx-text-fill: white; -fx-padding: 8 20; -fx-background-radius: 5; -fx-cursor: hand;");
        replyBtn2.setOnAction(e -> showReplyDialog(email, false));

        Button forwardBtn2 = new Button("Chuyển tiếp");
        forwardBtn2.setStyle("-fx-background-color: transparent; -fx-text-fill: " + PRIMARY_COLOR + "; -fx-border-color: " + PRIMARY_COLOR + "; -fx-border-radius: 5; -fx-padding: 8 20; -fx-cursor: hand;");
        forwardBtn2.setOnAction(e -> showReplyDialog(email, true));

        Button closeBtn2 = new Button("✕ Đóng thư");
        closeBtn2.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-radius: 5; -fx-padding: 8 20; -fx-cursor: hand;");
        closeBtn2.setOnAction(e -> closeEmailDetail());

        bottomActions.getChildren().addAll(replyBtn2, forwardBtn2, closeBtn2);

        detailPane.getChildren().addAll(subjectBox, sep1, senderRow, toLabel, sep2, bodyLabel, sep3, bottomActions);
        return detailPane;
    }

    /**
     * Tắt thư đang xem và trở về màn hình chờ
     */
    private void closeEmailDetail() {
        selectedEmail = null;
        emailDetailPane = createEmailDetailPane(null);
        ScrollPane detailScroll = new ScrollPane(emailDetailPane);
        detailScroll.setFitToWidth(true);
        detailScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        SplitPane center = (SplitPane) mainLayout.getCenter();
        if (center != null && center.getItems().size() > 1) {
            double[] divPositions = center.getDividerPositions();
            center.getItems().set(1, detailScroll);
            if (divPositions != null && divPositions.length > 0) {
                center.setDividerPositions(divPositions);
            }
        }
        refreshEmailList();
    }

    /**
     * Dialog soạn email mới
     */
    private void showComposeDialog() {
        showComposeDialogWithDefaults("", "", "", false);
    }

    private void showReplyDialog(Email original, boolean isForward) {
        String to = isForward ? "" : original.getSender();
        String subject = (isForward ? "Fwd: " : "Re: ") + (original.getSubject() != null ? original.getSubject() : "");
        String body = "\n\n--- " + (isForward ? "Forwarded message" : "Original message") + " ---\n" +
                "Từ: " + original.getSender() + "\n" +
                "Ngày: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(original.getDate()) + "\n" +
                "Tiêu đề: " + original.getSubject() + "\n\n" +
                original.getBody();
        showComposeDialogWithDefaults(to, subject, body, false);
    }

    private void showComposeDialogWithDefaults(String defaultTo, String defaultSubject, String defaultBody, boolean isGmail) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Soạn thư mới");

        VBox root = new VBox(10);
        root.setPadding(new Insets(0));
        root.setStyle("-fx-background-color: white;");

        // Title bar
        HBox titleBar = new HBox(10);
        titleBar.setPadding(new Insets(10, 15, 10, 15));
        titleBar.setStyle("-fx-background-color: #404040;");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("Thư mới");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
        titleBar.getChildren().add(titleLabel);

        // Form
        VBox form = new VBox(8);
        form.setPadding(new Insets(10, 15, 10, 15));

        // To
        HBox toRow = new HBox(10);
        toRow.setAlignment(Pos.CENTER_LEFT);
        Label toLabel = new Label("Tới:");
        toLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-min-width: 60;");
        TextField toField = new TextField(defaultTo);
        toField.setStyle("-fx-font-size: 13px; -fx-border-color: transparent; -fx-border-width: 0 0 1 0; -fx-border-color: " + BORDER_COLOR + ";");
        HBox.setHgrow(toField, Priority.ALWAYS);
        toRow.getChildren().addAll(toLabel, toField);

        // Subject
        HBox subjectRow = new HBox(10);
        subjectRow.setAlignment(Pos.CENTER_LEFT);
        Label subLabel = new Label("Tiêu đề:");
        subLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-min-width: 60;");
        TextField subjectField = new TextField(defaultSubject);
        subjectField.setStyle("-fx-font-size: 13px; -fx-border-color: transparent; -fx-border-width: 0 0 1 0; -fx-border-color: " + BORDER_COLOR + ";");
        HBox.setHgrow(subjectField, Priority.ALWAYS);
        subjectRow.getChildren().addAll(subLabel, subjectField);

        // Send method
        HBox methodRow = new HBox(10);
        methodRow.setAlignment(Pos.CENTER_LEFT);
        Label methodLabel = new Label("Qua:");
        methodLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-min-width: 60;");
        ToggleGroup sendGroup = new ToggleGroup();
        RadioButton localRadio = new RadioButton("Mail Server (" + serverHost + ")");
        localRadio.setToggleGroup(sendGroup);
        localRadio.setSelected(true);
        RadioButton gmailRadio = new RadioButton("Gmail (SMTP SSL)");
        gmailRadio.setToggleGroup(sendGroup);
        methodRow.getChildren().addAll(methodLabel, localRadio, gmailRadio);

        // Gmail credentials (hidden by default)
        VBox gmailBox = new VBox(5);
        gmailBox.setPadding(new Insets(5, 0, 5, 70));
        gmailBox.setVisible(false);
        gmailBox.setManaged(false);
        TextField gmailUser = new TextField("vinhnguyen241206@gmail.com");
        gmailUser.setPromptText("Gmail address");
        gmailUser.setStyle("-fx-font-size: 12px;");
        TextField gmailPass = new TextField("vhag qjmo qzkj uyyd");
        gmailPass.setPromptText("Gmail App Password");
        gmailPass.setStyle("-fx-font-size: 12px;");
        Label gmailNote = new Label("Dùng App Password từ Google Account");
        gmailNote.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");
        gmailBox.getChildren().addAll(gmailUser, gmailPass, gmailNote);

        gmailRadio.setOnAction(e -> { gmailBox.setVisible(true); gmailBox.setManaged(true); });
        localRadio.setOnAction(e -> { gmailBox.setVisible(false); gmailBox.setManaged(false); });

        // Body
        TextArea bodyArea = new TextArea(defaultBody);
        bodyArea.setPrefHeight(250);
        bodyArea.setWrapText(true);
        bodyArea.setStyle("-fx-font-size: 13px; -fx-border-color: transparent;");
        VBox.setVgrow(bodyArea, Priority.ALWAYS);

        // Status
        Label sendStatus = new Label();
        sendStatus.setStyle("-fx-font-size: 12px; -fx-padding: 5 0;");

        // Buttons
        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(5, 15, 10, 15));
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        Button sendBtn = new Button("Gửi");
        sendBtn.setStyle("-fx-background-color: " + PRIMARY_COLOR + "; -fx-text-fill: white; -fx-font-size: 14px; " +
                "-fx-padding: 8 25; -fx-background-radius: 5; -fx-cursor: hand;");

        Button discardBtn = new Button("Hủy");
        discardBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 13px; -fx-cursor: hand;");
        discardBtn.setOnAction(e -> dialog.close());

        buttonBar.getChildren().addAll(sendBtn, discardBtn, sendStatus);

        sendBtn.setOnAction(e -> {
            String to = DatabaseManager.normalizeEmail(toField.getText().trim());
            String subject = subjectField.getText().trim();
            String body = bodyArea.getText();

            if (to.isEmpty()) {
                sendStatus.setText("Vui lòng nhập địa chỉ email người nhận");
                sendStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + RED_COLOR + ";");
                return;
            }

            sendBtn.setDisable(true);
            sendStatus.setText("Đang gửi...");
            sendStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + PRIMARY_COLOR + ";");

            new Thread(() -> {
                boolean success;
                if (gmailRadio.isSelected()) {
                    String gUser = gmailUser.getText().trim();
                    String gPass = gmailPass.getText().trim();
                    success = SMTPClient.sendViaGmail(gUser, gPass, to, subject, body);
                    if (success) {
                        Email sentEmail = new Email(gUser.isEmpty() ? currentUser : gUser, to, subject, body);
                        sentEmail.setFolder("SENT");
                        sentEmail.setRead(true);
                        dbManager.saveEmail(sentEmail);
                    }
                } else {
                    // Đảm bảo server đang chạy nếu là client đơn lẻ
                    if (!isRemoteServer()) {
                        ensureServerRunning();
                    }
                    // Gửi qua SMTP server (localhost hoặc IP máy chủ LAN khi demo 2 máy)
                    success = SMTPClient.sendEmail(serverHost, SMTP_PORT, currentUser, to, subject, body);
                    if (success && isRemoteServer()) {
                        Email sentEmail = new Email(currentUser, to, subject, body);
                        sentEmail.setFolder("SENT");
                        sentEmail.setRead(true);
                        dbManager.saveEmail(sentEmail);
                    }
                }

                final boolean finalSuccess = success;
                Platform.runLater(() -> {
                    sendBtn.setDisable(false);
                    if (finalSuccess) {
                        sendStatus.setText("Gửi thành công!");
                        sendStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: green;");
                        refreshEmailList();
                        // Đóng sau 1 giây
                        new Thread(() -> {
                            try { Thread.sleep(1000); } catch (Exception ex) {}
                            Platform.runLater(dialog::close);
                        }).start();
                    } else {
                        sendStatus.setText("Gửi thất bại! Kiểm tra kết nối máy chủ.");
                        sendStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + RED_COLOR + ";");
                    }
                });
            }).start();
        });

        form.getChildren().addAll(toRow, subjectRow, methodRow, gmailBox, bodyArea);
        root.getChildren().addAll(titleBar, form, buttonBar);

        dialog.setScene(new Scene(root, 550, 480));
        dialog.show();
    }

    /**
     * Dialog quản lý server & xem Database
     */
    private void showServerDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Quản lý Server & Cơ sở dữ liệu");

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: white;");

        Label title = new Label("⚙ Quản lý Mail Server & Cơ sở dữ liệu (SQLite)");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + PRIMARY_COLOR + ";");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // TAB 1: Server Status
        Tab tabLogs = new Tab("Trạng thái Kết Nối Máy Chủ");
        VBox logBox = new VBox(15);
        logBox.setPadding(new Insets(15));

        boolean smtpOk = isPortOpen(SMTP_PORT);
        boolean pop3Ok = isPortOpen(POP3_PORT);

        Label smtpStatus = new Label("SMTP Server (Cổng " + SMTP_PORT + " - Gửi thư): " +
                (smtpOk ? "🟢 Đang hoạt động (Sẵn sàng)" : "🔴 Chưa kết nối (Hãy chạy run_server.bat)"));
        smtpStatus.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label pop3Status = new Label("POP3 Server (Cổng " + POP3_PORT + " - Nhận thư): " +
                (pop3Ok ? "🟢 Đang hoạt động (Sẵn sàng)" : "🔴 Chưa kết nối (Hãy chạy run_server.bat)"));
        pop3Status.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label guideLbl = new Label("💡 Mail Server chạy độc lập để hỗ trợ mở nhiều Client cùng lúc.\n" +
                "Nếu Server chưa chạy, bạn có thể bấm nút bên dưới để khởi động.");
        guideLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        HBox btnRow = new HBox(10);
        Button openServerBtn = new Button("🚀 Mở Mail Server (run_server.bat)");
        openServerBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;");
        openServerBtn.setOnAction(e -> {
            try {
                new ProcessBuilder("cmd.exe", "/c", "start run_server.bat").start();
            } catch (Exception ex) {
                System.err.println("Lỗi mở server: " + ex.getMessage());
            }
        });

        Button recheckBtn = new Button("🔄 Kiểm tra lại kết nối");
        recheckBtn.setStyle("-fx-padding: 8 16; -fx-cursor: hand;");
        recheckBtn.setOnAction(e -> {
            boolean sOk = isPortOpen(SMTP_PORT);
            boolean pOk = isPortOpen(POP3_PORT);
            smtpStatus.setText("SMTP Server (Cổng " + SMTP_PORT + " - Gửi thư): " +
                    (sOk ? "🟢 Đang hoạt động (Sẵn sàng)" : "🔴 Chưa kết nối (Hãy chạy run_server.bat)"));
            pop3Status.setText("POP3 Server (Cổng " + POP3_PORT + " - Nhận thư): " +
                    (pOk ? "🟢 Đang hoạt động (Sẵn sàng)" : "🔴 Chưa kết nối (Hãy chạy run_server.bat)"));
        });

        btnRow.getChildren().addAll(openServerBtn, recheckBtn);

        logBox.getChildren().addAll(smtpStatus, pop3Status, new Separator(), guideLbl, btnRow);
        tabLogs.setContent(logBox);

        // TAB 2: Emails Table in DB
        Tab tabEmails = new Tab("Dữ liệu bảng Emails (" + dbManager.getAllEmails().size() + ")");
        VBox emailBox = new VBox(10);
        emailBox.setPadding(new Insets(10));

        TableView<Email> emailTable = new TableView<>();
        emailTable.setPrefHeight(400);

        TableColumn<Email, String> colFrom = new TableColumn<>("Người gửi");
        colFrom.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSender()));
        colFrom.setPrefWidth(180);

        TableColumn<Email, String> colTo = new TableColumn<>("Người nhận");
        colTo.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getRecipient()));
        colTo.setPrefWidth(180);

        TableColumn<Email, String> colSub = new TableColumn<>("Tiêu đề");
        colSub.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSubject()));
        colSub.setPrefWidth(220);

        TableColumn<Email, String> colFolder = new TableColumn<>("Folder");
        colFolder.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getFolder()));
        colFolder.setPrefWidth(80);

        TableColumn<Email, String> colDate = new TableColumn<>("Ngày gửi");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        colDate.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getDate() != null ? sdf.format(data.getValue().getDate()) : ""));
        colDate.setPrefWidth(140);

        TableColumn<Email, String> colRead = new TableColumn<>("Đã đọc");
        colRead.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().isRead() ? "Có" : "Chưa"));
        colRead.setPrefWidth(60);

        emailTable.getColumns().addAll(colFrom, colTo, colSub, colFolder, colDate, colRead);
        emailTable.setItems(FXCollections.observableArrayList(dbManager.getAllEmails()));

        Button refreshEmailsBtn = new Button("🔄 Làm mới bảng Email");
        refreshEmailsBtn.setOnAction(e -> {
            List<Email> list = dbManager.getAllEmails();
            emailTable.setItems(FXCollections.observableArrayList(list));
            tabEmails.setText("Dữ liệu bảng Emails (" + list.size() + ")");
        });

        emailBox.getChildren().addAll(emailTable, refreshEmailsBtn);
        tabEmails.setContent(emailBox);

        // TAB 3: Users Table in DB
        Tab tabUsers = new Tab("Dữ liệu bảng Users (" + dbManager.getAllUsers().size() + ")");
        VBox userBox = new VBox(10);
        userBox.setPadding(new Insets(10));

        TableView<model.User> userTable = new TableView<>();
        userTable.setPrefHeight(400);

        TableColumn<model.User, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()));
        colId.setPrefWidth(50);

        TableColumn<model.User, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getEmail()));
        colEmail.setPrefWidth(240);

        TableColumn<model.User, String> colPass = new TableColumn<>("Mật khẩu");
        colPass.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getPassword()));
        colPass.setPrefWidth(150);

        TableColumn<model.User, String> colName = new TableColumn<>("Tên hiển thị");
        colName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDisplayName()));
        colName.setPrefWidth(180);

        userTable.getColumns().addAll(colId, colEmail, colPass, colName);
        userTable.setItems(FXCollections.observableArrayList(dbManager.getAllUsers()));

        Button refreshUsersBtn = new Button("🔄 Làm mới bảng User");
        refreshUsersBtn.setOnAction(e -> {
            List<model.User> uList = dbManager.getAllUsers();
            userTable.setItems(FXCollections.observableArrayList(uList));
            tabUsers.setText("Dữ liệu bảng Users (" + uList.size() + ")");
        });

        userBox.getChildren().addAll(userTable, refreshUsersBtn);
        tabUsers.setContent(userBox);

        tabPane.getTabs().addAll(tabLogs, tabEmails, tabUsers);

        root.getChildren().addAll(title, tabPane);

        dialog.setScene(new Scene(root, 900, 600));
        dialog.show();
    }

    /**
     * Thanh trạng thái dưới cùng
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(5, 15, 5, 15));
        statusBar.setStyle("-fx-background-color: " + WHITE + "; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1 0 0 0;");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Máy chủ: " + serverHost + " | SMTP: Port " + SMTP_PORT + " | POP3: Port " + POP3_PORT + " | Sẵn sàng");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label versionLabel = new Label("Mail Server v1.0 - JavaFX");
        versionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        statusBar.getChildren().addAll(statusLabel, spacer, versionLabel);
        return statusBar;
    }

    /**
     * Đồng bộ email bằng POP3 Client thực thụ
     */
    private void syncViaPop3() {
        if (statusLabel != null) {
            statusLabel.setText("Đang kết nối POP3 Server (" + serverHost + ":" + POP3_PORT + ") để đồng bộ...");
        }

        new Thread(() -> {
            POP3Client pop3Client = new POP3Client();
            boolean ok = pop3Client.connect(serverHost, POP3_PORT);
            if (ok) {
                boolean loggedIn = pop3Client.login(currentUser, currentPassword);
                if (loggedIn) {
                    List<Email> remoteEmails = pop3Client.retrieveAllMessages();
                    for (Email rem : remoteEmails) {
                        dbManager.saveEmail(rem);
                    }
                    Platform.runLater(() -> {
                        refreshEmailList();
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("POP3 Sync");
                        alert.setHeaderText("Đồng bộ POP3 thành công");
                        alert.setContentText("Đã tải " + remoteEmails.size() + " thư từ POP3 Server " + serverHost + ":" + POP3_PORT);
                        alert.show();
                    });
                } else {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("POP3 Sync");
                        alert.setHeaderText("Đăng nhập POP3 thất bại");
                        alert.setContentText("Không thể đăng nhập tài khoản " + currentUser);
                        alert.show();
                    });
                }
                pop3Client.disconnect();
            } else {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("POP3 Sync");
                    alert.setHeaderText("Kết nối POP3 thất bại");
                    alert.setContentText("Không thể kết nối đến POP3 Server " + serverHost + ":" + POP3_PORT);
                    alert.show();
                });
            }
        }).start();
    }

    /**
     * Làm mới danh sách email
     */
    private void refreshEmailList() {
        currentEmails.clear();

        switch (currentFolder) {
            case "INBOX":
                currentEmails.addAll(dbManager.getEmailsByRecipientAndFolder(currentUser, "INBOX"));
                break;
            case "SENT":
                currentEmails.addAll(dbManager.getSentEmails(currentUser));
                break;
            case "STARRED":
                currentEmails.addAll(dbManager.getStarredEmails(currentUser));
                break;
            case "DRAFTS":
                currentEmails.addAll(dbManager.getEmailsByRecipientAndFolder(currentUser, "DRAFTS"));
                break;
            case "TRASH":
                currentEmails.addAll(dbManager.getEmailsByRecipientAndFolder(currentUser, "TRASH"));
                break;
            default:
                currentEmails.addAll(dbManager.getEmailsByRecipient(currentUser));
        }

        // Update UI
        emailListPane = createEmailListPane();
        ScrollPane listScroll = new ScrollPane(emailListPane);
        listScroll.setFitToWidth(true);
        listScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        SplitPane center = (SplitPane) mainLayout.getCenter();
        if (center != null) {
            double[] divPositions = center.getDividerPositions();
            center.getItems().set(0, listScroll);
            if (divPositions != null && divPositions.length > 0) {
                center.setDividerPositions(divPositions);
            }
        }

        // Cập nhật sidebar để cập nhật số lượng chưa đọc
        mainLayout.setLeft(createSideBar());

        // Update status bar
        if (statusLabel != null) {
            int unread = dbManager.getUnreadCount(currentUser);
            statusLabel.setText("Máy chủ: " + serverHost + " | SMTP: Port " + SMTP_PORT + " | POP3: Port " + POP3_PORT + " | " +
                    currentEmails.size() + " email | " + unread + " chưa đọc");
        }
    }

    private int lastUnreadCount = -1;
    private void refreshEmailListSilently() {
        int currentUnread = dbManager.getUnreadCount(currentUser);
        List<Email> newList;
        switch (currentFolder) {
            case "INBOX":
                newList = dbManager.getEmailsByRecipientAndFolder(currentUser, "INBOX");
                break;
            case "SENT":
                newList = dbManager.getSentEmails(currentUser);
                break;
            case "STARRED":
                newList = dbManager.getStarredEmails(currentUser);
                break;
            case "DRAFTS":
                newList = dbManager.getEmailsByRecipientAndFolder(currentUser, "DRAFTS");
                break;
            case "TRASH":
                newList = dbManager.getEmailsByRecipientAndFolder(currentUser, "TRASH");
                break;
            default:
                newList = dbManager.getEmailsByRecipient(currentUser);
        }

        boolean hasChanged = (newList.size() != currentEmails.size()) || (currentUnread != lastUnreadCount);
        if (!hasChanged) {
            for (int i = 0; i < newList.size(); i++) {
                Email n = newList.get(i);
                Email o = currentEmails.get(i);
                if (!n.getId().equals(o.getId()) || n.isRead() != o.isRead() || n.isStarred() != o.isStarred()) {
                    hasChanged = true;
                    break;
                }
            }
        }

        if (hasChanged) {
            lastUnreadCount = currentUnread;
            currentEmails.setAll(newList);
            emailListPane = createEmailListPane();
            ScrollPane listScroll = new ScrollPane(emailListPane);
            listScroll.setFitToWidth(true);
            listScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

            SplitPane center = (SplitPane) mainLayout.getCenter();
            if (center != null) {
                double[] divPositions = center.getDividerPositions();
                center.getItems().set(0, listScroll);
                if (divPositions != null && divPositions.length > 0) {
                    center.setDividerPositions(divPositions);
                }
            }
            mainLayout.setLeft(createSideBar());
            if (statusLabel != null) {
                statusLabel.setText("Máy chủ: " + serverHost + " | SMTP: Port " + SMTP_PORT + " | POP3: Port " + POP3_PORT + " | " +
                        currentEmails.size() + " email | " + currentUnread + " chưa đọc");
            }
        }
    }

    private boolean isPortOpen(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("localhost", port), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tìm kiếm email
     */
    private void searchEmails() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            refreshEmailList();
            return;
        }

        currentEmails.clear();
        currentEmails.addAll(dbManager.searchEmails(currentUser, keyword));

        emailListPane = createEmailListPane();
        ScrollPane listScroll = new ScrollPane(emailListPane);
        listScroll.setFitToWidth(true);
        listScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        SplitPane center = (SplitPane) mainLayout.getCenter();
        center.getItems().set(0, listScroll);

        if (folderTitle != null)
            folderTitle.setText("Kết quả tìm kiếm: \"" + keyword + "\" (" + currentEmails.size() + ")");
    }

    /**
     * Lấy tên hiển thị của folder
     */
    private String getFolderDisplayName(String folder) {
        switch (folder) {
            case "INBOX": return "Hộp thư đến";
            case "SENT": return "Đã gửi";
            case "STARRED": return "Có gắn dấu sao";
            case "DRAFTS": return "Thư nháp";
            case "TRASH": return "Thùng rác";
            default: return folder;
        }
    }

    /**
     * CSS styles
     */
    private String getCSS() {
        return String.join("",
            ".split-pane > .split-pane-divider { -fx-background-color: %23e0e0e0; -fx-padding: 0 1 0 0; }",
            ".scroll-pane { -fx-background-color: transparent; }",
            ".scroll-pane > .viewport { -fx-background-color: transparent; }",
            ".scroll-bar { -fx-background-color: transparent; }",
            ".scroll-bar .thumb { -fx-background-color: %23c1c1c1; -fx-background-radius: 5; }",
            ".scroll-bar .track { -fx-background-color: transparent; }",
            ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-padding: 0; }",
            ".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-shape: \"\"; -fx-padding: 0; }",
            ".check-box { -fx-cursor: hand; }",
            ".titled-pane > .title { -fx-background-color: transparent; }",
            ".titled-pane > .content { -fx-background-color: transparent; }",
            ".separator { -fx-padding: 5 0 5 0; }"
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}
