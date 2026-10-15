package dev.prathamesh.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.prathamesh.types.UserRole;
import jakarta.persistence.*;

@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email")
    }
)
public class UserModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(
        name = "name",
        nullable = false,
        length = 100
    )
    private String name;

    @Column(
        name = "email",
        nullable = false,
        unique = true,
        length = 255
    )
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(
        name = "password",
        nullable = true,
        length = 255
    )
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "role",
        nullable = false,
        length = 20
    )
    private UserRole role = UserRole.USER;

    protected UserModel() {
        // Required by JPA
    }

    public UserModel(String name, String email) {
        this(name, email, null, UserRole.USER);
    }

    public UserModel(String name, String email, String password) {
        this(name, email, password, UserRole.USER);
    }

    public UserModel(String name, String email, String password, UserRole role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = (role != null ? role : UserRole.USER);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public UserRole getRole() {
        return role;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
