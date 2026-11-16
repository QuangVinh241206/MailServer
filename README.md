# Hệ Thống Mail Server & Mail Client Độc Lập (JavaFX + SQLite)

Hệ thống được thiết kế theo đúng chuẩn **mô hình Client-Server phân tán**:
- **Mail Server độc lập**: Lắng nghe và quản lý các cổng mạng (SMTP `2525`, POP3 `1110`), quản lý cơ sở dữ liệu SQLite, hiển thị nhật ký thời gian thực và quản trị người dùng.
- **Mail Client độc lập**: Có thể khởi chạy **nhiều cửa sổ client cùng một lúc** trên cùng một máy (hoặc nhiều máy khác nhau trong mạng LAN) mà **hoàn toàn không bị xung đột cổng mạng hay khóa database**.

---

## 🚀 Các tập tin khởi chạy nhanh

| Windows (.bat) | Linux / macOS (.sh) | Chức năng | Mô tả |
|---|---|---|---|
| **`run.bat`** | **`./run.sh`** | **Menu tổng hợp** | Menu điều khiển đầy đủ chức năng (Server, Client, Demo 2 máy, CSDL). |
| **`run_server.bat`** | **`./run_server.sh`** | **Khởi chạy Mail Server** | Mở bảng điều khiển Server (lắng nghe cổng SMTP 2525 & POP3 1110, live log, bảng database). |
| **`run_client.bat`** | **`./run_client.sh`** | **Mở 1 Mail Client** | Mở cửa sổ giao diện Gmail Client. **Có thể chạy nhiều lần để mở 2, 3, 4... Client cùng lúc**. |
| **`run_demo_multiclient.bat`** | **`./run_demo_multiclient.sh`** | **Demo đa Client tự động** | Tự động mở 1 Server Dashboard và 2 cửa sổ Client cạnh nhau để kiểm tra trao đổi thư. |
| **`view_db.bat`** | **`./view_db.sh`** | **Xem nhanh Database** | In toàn bộ các bảng Users và Emails ra màn hình Console. |
| **`compile.bat`** | **`./compile.sh`** | **Biên dịch dự án** | Biên dịch toàn bộ mã nguồn Java với JavaFX và SQLite. |
| **`test.bat`** | **`./test.sh`** | **Kiểm tra tự động** | Chạy kiểm tra tự động toàn diện các giao thức SMTP, POP3 và Database. |

> 🐧 **Hướng dẫn chạy trên Linux / Ubuntu**:
> 1. Cấp quyền thực thi: `chmod +x *.sh`
> 2. Biên dịch: `./compile.sh`
> 3. Khởi chạy: `./run.sh` (hoặc `./run_server.sh`, `./run_client.sh`)
> *(Lưu ý: Nếu máy tính Linux chưa có OpenJFX, cài đặt nhanh bằng: `sudo apt install openjfx`)*

---

## 👥 Kịch bản kiểm tra gửi nhận nhiều Client cùng lúc

1. **Khởi động Server**:
   - Nhấp đúp vào **`run_server.bat`**. Cửa sổ **Mail Server Dashboard** xuất hiện:
     * SMTP Server: Cổng `2525` (🟢 ĐANG CHẠY)
     * POP3 Server: Cổng `1110` (🟢 ĐANG CHẠY)
     * Log tương tác thời gian thực sẽ hiển thị tại đây khi các Client hoạt động.

2. **Mở Client 1 (Người dùng 1)**:
   - Nhấp đúp vào **`run_client.bat`**.
   - Đăng nhập tài khoản: `user1@localhost` / Mật khẩu: `123456`.

3. **Mở Client 2 (Người dùng 2)**:
   - Nhấp đúp vào **`run_client.bat`** một lần nữa (hoặc bấm nút **`➕ Mở Client mới`** trên thanh công cụ của Client 1).
   - Đăng nhập tài khoản: `user2@localhost` / Mật khẩu: `123456`.

4. **Thực hiện gửi nhận giữa 2 Client**:
   - Tại **Client 1** (`user1@localhost`): Bấm nút **`✏ Soạn thư`** -> Gửi tới `user2@localhost` -> Tiêu đề: "Chào User 2" -> Bấm **Gửi**.
   - **Quan sát Server**: Bảng điều khiển Server lập tức ghi nhận log tương tác SMTP:
     ```
     [SMTP] Kết nối SMTP mới từ: /127.0.0.1
     [SMTP] C: HELO localhost
     [SMTP] C: MAIL FROM:<user1@localhost>
     [SMTP] C: RCPT TO:<user2@localhost>
     [SMTP] C: DATA ...
     [SMTP] Email đã lưu: user1@localhost -> user2@localhost
     ```
   - **Quan sát Client 2**: Trong vòng 3-4 giây (nhờ tính năng tự động làm mới thời gian thực), thư mới từ `user1@localhost` sẽ lập tức xuất hiện trong **Hộp thư đến** của Client 2 mà không cần bấm nút nào!
   - Tại **Client 2**, bấm **Trả lời (Reply)** để gửi lại cho Client 1.

---

## ✉ Gửi thư qua Gmail thật

1. Tại cửa sổ Mail Client bất kỳ, bấm **Soạn thư**.
2. Chọn mục **Qua: Gmail (SMTP SSL)**.
3. Nhập tài khoản Gmail và **Google App Password (Mật khẩu ứng dụng 16 ký tự)**.
4. Bấm **Gửi**. Thư sẽ gửi trực tiếp đến hộp thư Gmail người nhận qua SSL cổng 465, đồng thời lưu vào mục "Đã gửi" trên Database Server.

---

## 🌐 Hướng dẫn Demo trên 2 máy tính (Mạng LAN / Wi-Fi)

Hệ thống hỗ trợ demo thực tế trên 2 máy tính khác nhau (Laptop/PC) kết nối cùng mạng Wi-Fi hoặc mạng LAN:

### Bước 1: Kết nối cùng mạng
- Đảm bảo cả **Máy 1** và **Máy 2** cùng kết nối vào một mạng Wi-Fi (hoặc phát điểm truy cập Wi-Fi / Hotspot từ điện thoại di động).

### Bước 2: Khởi động Server trên Máy 1
- Trên **Máy 1**: Nhấp đúp chạy **`run_server.bat`**.
- Màn hình Server Dashboard sẽ hiển thị ngay địa chỉ IP mạng LAN của máy, ví dụ:
  `🌐 IP Máy Chủ (LAN / Wi-Fi): 192.168.1.88 | SMTP: 2525 | POP3: 1110`
- Bấm nút **📋 Copy IP** để sao chép địa chỉ IP này.
- *Lưu ý Windows Firewall*: Nếu lần đầu chạy, Windows Defender Firewall hỏi thì nhấn **Allow access (Cho phép truy cập)** cho Java.

### Bước 3: Chạy Client trên Máy 1 (Người gửi)
- Trên **Máy 1**: Chạy `run_client.bat` và đăng nhập `user1@localhost` / pass `123456`.

### Bước 4: Chạy Client trên Máy 2 (Người nhận)
- Chép thư mục dự án `MailServer` sang **Máy 2** (chỉ cần có JDK/Java).
- Trên **Máy 2**: 
  - Cách 1: Chạy **`run.bat`** -> chọn **`[7]`** -> Nhập địa chỉ IP của Máy 1 (`192.168.1.88`).
  - Cách 2: Chạy **`run_client.bat`** -> Trên hộp thoại Đăng nhập, tại ô **Máy chủ IP**, sửa `localhost` thành IP của Máy 1 (`192.168.1.88`).
- Đăng nhập tài khoản: `user2@localhost` / pass `123456` (hoặc bấm **Đăng ký** tài khoản mới trực tiếp qua mạng).

### Bước 5: Thử nghiệm gửi nhận thư thực tế
- Tại **Máy 1**: Soạn thư gửi tới `user2@localhost` -> Bấm **Gửi**.
- Trên **Máy 2**: Trong 1.5 - 2 giây, thư mới sẽ lập tức xuất hiện trong Hộp thư đến!
- Tại màn hình **Server Dashboard** (Máy 1), bạn sẽ thấy log socket thực tế:
  `[SMTP] Kết nối SMTP mới từ: /192.168.1.x`
  `[POP3] Kết nối POP3 mới từ: /192.168.1.x`
- Tại **Máy 2**: Bấm Trả lời (Reply) để gửi thư ngược lại về cho Máy 1.

---

## 🛠 Kiến trúc hệ thống

```
+-------------------------------------------------------+
|                 MAIL SERVER DASHBOARD                 |
|  - SMTPServer (Port 2525)   - POP3Server (Port 1110)   |
|  - SQLite Database Manager (WAL Mode, mailserver.db)  |
|  - Live Logs & Interactive DB Table Viewer            |
+-------------------------------------------------------+
                 ▲                      ▲
       SMTP:2525 | POP3:1110  SMTP:2525 | POP3:1110
                 ▼                      ▼
+-------------------------+    +-------------------------+
|   MAIL CLIENT 1 (GUI)   |    |   MAIL CLIENT 2 (GUI)   |
| User: user1@localhost   |    | User: user2@localhost   |
| (Chạy tiến trình riêng) |    | (Chạy tiến trình riêng) |
+-------------------------+    +-------------------------+
```
