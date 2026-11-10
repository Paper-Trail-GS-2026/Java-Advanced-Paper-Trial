package br.com.fiap.PaperTrail.guidance.repository;

import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuidanceChunkRepository extends JpaRepository<GuidanceChunk, Long> {
    Page<GuidanceChunk> findByServiceId(Long serviceId, Pageable pageable);

    Optional<GuidanceChunk> findByIdAndServiceId(Long id, Long serviceId);

    List<GuidanceChunk> findByServiceId(Long serviceId);
}
