package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.guidance.dto.GuidanceChunkRequest;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.repository.GuidanceChunkRepository;
import br.com.fiap.PaperTrail.guidance.service.GuidanceChunkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testes unitários com repositório mockado (não comprovam Oracle). */
@ExtendWith(MockitoExtension.class)
class GuidanceChunkServiceTest {

    @Mock
    private GuidanceChunkRepository chunkRepository;

    @InjectMocks
    private GuidanceChunkService chunkService;

    private GuidanceChunkRequest request() {
        return new GuidanceChunkRequest("  Texto fictício.  ", "https://example.invalid/papertrail/1",
                "Fonte fictícia", OffsetDateTime.parse("2026-11-01T10:00:00Z"), 2L);
    }

    @Test
    void criaTrechoVinculadoAoServicoDaRota() {
        when(chunkRepository.saveAndFlush(any(GuidanceChunk.class))).thenAnswer(inv -> inv.getArgument(0));

        GuidanceChunk saved = chunkService.createChunk(7L, request());

        assertEquals(7L, saved.getServiceId());
        assertEquals("Texto fictício.", saved.getContent());
        assertEquals(2L, saved.getVersion());
    }

    @Test
    void servicoInexistenteViolaFkEDevolve404() {
        when(chunkRepository.saveAndFlush(any(GuidanceChunk.class)))
                .thenThrow(new DataIntegrityViolationException("FK_PT_CHUNK_SERVICE"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> chunkService.createChunk(99L, request()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        // a mensagem ao cliente não vaza detalhe de SQL/constraint
        assertEquals("Serviço 99 não encontrado", e.getReason());
    }

    @Test
    void trechoDeOutroServicoNaoEEncontrado() {
        when(chunkRepository.findByIdAndServiceId(1000L, 2L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> chunkService.findChunk(2L, 1000L));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void atualizaCamposMantendoIdEServico() {
        GuidanceChunk existing = GuidanceChunk.builder().id(1000L).serviceId(1L).content("antigo")
                .sourceUrl("https://example.invalid/x").sourceTitle("Antigo")
                .reviewedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z")).version(1L).build();
        when(chunkRepository.findByIdAndServiceId(1000L, 1L)).thenReturn(Optional.of(existing));
        when(chunkRepository.saveAndFlush(existing)).thenReturn(existing);

        GuidanceChunk updated = chunkService.updateChunk(1L, 1000L, request());

        assertEquals(1000L, updated.getId());
        assertEquals(1L, updated.getServiceId());
        assertEquals("Fonte fictícia", updated.getSourceTitle());
        assertEquals(2L, updated.getVersion());
    }

    @Test
    void removerTrechoInexistenteNaoChamaDelete() {
        when(chunkRepository.findByIdAndServiceId(5L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> chunkService.deleteChunk(1L, 5L));
        verify(chunkRepository, never()).delete(any(GuidanceChunk.class));
    }
}
