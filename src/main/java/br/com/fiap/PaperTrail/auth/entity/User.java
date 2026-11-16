package br.com.fiap.PaperTrail.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "PT_USER", uniqueConstraints = @UniqueConstraint(name = "UQ_PT_USER_EMAIL", columnNames = "EMAIL"))
@Getter
@Setter
@NoArgsConstructor
public class User {
    public enum Role { CITIZEN, MANAGER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NAME", nullable = false, length = 120)
    private String name;

    @Column(name = "EMAIL", nullable = false, length = 254)
    private String email;

    @Column(name = "PASSWORD_HASH", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE_NAME", nullable = false, length = 10)
    private Role roleName;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void setCreationTime() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
