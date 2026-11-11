package br.com.fiap.PaperTrail.service.entity;

import br.com.fiap.PaperTrail.category.entity.Category;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PT_SERVICE")
@Getter
@Setter
@NoArgsConstructor
public class PublicService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CATEGORY_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_PT_SERVICE_CATEGORY"))
    private Category category;

    @Column(name = "TITLE", nullable = false, length = 160)
    private String title;

    @Column(name = "DESCRIPTION", nullable = false, length = 2000)
    private String description;

    @Column(name = "CITY_NAME", nullable = false, length = 120)
    private String cityName;

    @Column(name = "VERSION", nullable = false, precision = 18)
    private Long version = 1L;
}
