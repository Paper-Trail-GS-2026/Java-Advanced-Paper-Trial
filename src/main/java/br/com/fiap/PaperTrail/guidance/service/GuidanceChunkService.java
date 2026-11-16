package br.com.fiap.PaperTrail.guidance.service;

import br.com.fiap.PaperTrail.guidance.dto.GuidanceChunkRequest;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.repository.GuidanceChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GuidanceChunkService {
    private final GuidanceChunkRepository chunkRepository;

    @Transactional(readOnly = true)
    public Page<GuidanceChunk> findChunks(Long serviceId, Pageable pageable) {
        return chunkRepository.findByServiceId(serviceId, pageable);
    }

    @Transactional(readOnly = true)
    public GuidanceChunk findChunk(Long serviceId, Long id) {
        return chunkRepository.findByIdAndServiceId(id, serviceId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        String.format("Trecho %d não encontrado para o serviço %d", id, serviceId))
        );
    }

    @Transactional
    public GuidanceChunk createChunk(Long serviceId, GuidanceChunkRequest request) {
        return saveOrServiceNotFound(request.toEntity(serviceId), serviceId);
    }

    @Transactional
    public GuidanceChunk updateChunk(Long serviceId, Long id, GuidanceChunkRequest request) {
        GuidanceChunk existing = findChunk(serviceId, id);
        GuidanceChunk updated = request.toEntity(serviceId);
        existing.setContent(updated.getContent());
        existing.setSourceUrl(updated.getSourceUrl());
        existing.setSourceTitle(updated.getSourceTitle());
        existing.setReviewedAt(updated.getReviewedAt());
        existing.setVersion(updated.getVersion());
        return saveOrServiceNotFound(existing, serviceId);
    }

    @Transactional
    public void deleteChunk(Long serviceId, Long id) {
        chunkRepository.delete(findChunk(serviceId, id));
    }

    // Enquanto a porta de leitura do catálogo (Orlando) não existe, a FK do banco
    // (FK_PT_CHUNK_SERVICE) é a única verificação de que o serviço existe.
    private GuidanceChunk saveOrServiceNotFound(GuidanceChunk chunk, Long serviceId) {
        try {
            return chunkRepository.saveAndFlush(chunk);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    String.format("Serviço %d não encontrado", serviceId));
        }
    }
}
