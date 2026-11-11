package br.com.fiap.PaperTrail.category.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PT_CATEGORY", uniqueConstraints = @UniqueConstraint(name = "UQ_PT_CATEGORY_NAME", columnNames = "NAME"))
@Getter
@Setter
@NoArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;
}
