package br.com.fiap.PaperTrail.requirement.entity;

import br.com.fiap.PaperTrail.service.entity.PublicService;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "PT_REQUIREMENT")
@Getter
@Setter
@NoArgsConstructor
public class Requirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "SERVICE_ID", nullable = false, foreignKey = @ForeignKey(name = "FK_PT_REQUIREMENT_SERVICE"))
    private PublicService service;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Convert(converter = NumericBooleanConverter.class)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "REQUIRED", nullable = false, columnDefinition = "NUMBER(1)")
    private boolean required = true;

    @Column(name = "SOURCE_URL", nullable = false, length = 2048)
    private String sourceUrl;
}
