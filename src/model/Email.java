package model;

import java.util.Date;
import java.util.UUID;

/**
 * Model đại diện cho một email
 */
public class Email {
    private String id;
    private String sender;
    private String recipient;
    private String subject;
    private String body;
    private Date date;
    private boolean read;
    private String folder; // INBOX, SENT, DRAFTS, TRASH, STARRED
    private boolean starred;

    public Email() {
        this.id = UUID.randomUUID().toString();
        this.date = new Date();
        this.read = false;
        this.starred = false;
        this.folder = "INBOX";
    }

    public Email(String sender, String recipient, String subject, String body) {
        this();
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }

    @Override
    public String toString() {
        return "Email{" +
                "id='" + id + '\'' +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", subject='" + subject + '\'' +
                ", date=" + date +
                ", read=" + read +
                ", folder='" + folder + '\'' +
                '}';
    }
}
