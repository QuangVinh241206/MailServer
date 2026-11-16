package server;

import database.DatabaseManager;

/**
 * Lớp quản lý tổng thể Mail Server
 * Khởi động và quản lý cả SMTP Server và POP3 Server
 */
public class MailServerApp {
    private SMTPServer smtpServer;
    private POP3Server pop3Server;
    private DatabaseManager dbManager;
    private int smtpPort;
    private int pop3Port;

    public MailServerApp(int smtpPort, int pop3Port) {
        this(smtpPort, pop3Port, new DatabaseManager());
    }

    public MailServerApp(int smtpPort, int pop3Port, DatabaseManager dbManager) {
        this.smtpPort = smtpPort;
        this.pop3Port = pop3Port;
        this.dbManager = dbManager;
    }

    public void startAll() {
        smtpServer = new SMTPServer(smtpPort, dbManager);
        pop3Server = new POP3Server(pop3Port, dbManager);
        smtpServer.start();
        pop3Server.start();
    }

    public void stopAll() {
        if (smtpServer != null) smtpServer.stop();
        if (pop3Server != null) pop3Server.stop();
        dbManager.close();
    }

    public SMTPServer getSmtpServer() { return smtpServer; }
    public POP3Server getPop3Server() { return pop3Server; }
    public DatabaseManager getDbManager() { return dbManager; }
    public boolean isRunning() {
        return (smtpServer != null && smtpServer.isRunning()) ||
               (pop3Server != null && pop3Server.isRunning());
    }
}
