package model;

/**
 * Model đại diện cho một người dùng
 */
public class User {
    private int id;
    private String email;
    private String password;
    private String displayName;

    public User() {}

    public User(String email, String password, String displayName) {
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}
