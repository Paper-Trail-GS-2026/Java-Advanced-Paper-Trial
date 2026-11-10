package br.com.fiap.PaperTrail.guidance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Trecho de orientação usado como fonte do RAG. Mapeia PT_GUIDANCE_CHUNK.
 * serviceId é coluna simples (sem @ManyToOne) para não depender das classes do catálogo.
 */
@Entity
@Data
@Builder
@Table(name = "PT_GUIDANCE_CHUNK")
@AllArgsConstructor
@NoArgsConstructor
public class GuidanceChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "SERVICE_ID", nullable = false)
    private Long serviceId;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    @Column(name = "SOURCE_URL", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "SOURCE_TITLE", nullable = false, length = 200)
    private String sourceTitle;

    @Column(name = "REVIEWED_AT", nullable = false)
    private OffsetDateTime reviewedAt;

    @Column(name = "VERSION", nullable = false)
    private Long version;
}
