package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.guidance.controller.GuidanceChunkController;
import br.com.fiap.PaperTrail.guidance.dto.GuidanceChunkRequest;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.service.GuidanceChunkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de camada web com serviço mockado (teste unitário; não comprova Oracle).
 * A segurança base (JWT) é do Orlando: aqui só se valida que a regra @PreAuthorize do gestor é respeitada.
 */
@WebMvcTest(GuidanceChunkController.class)
@Import(GuidanceChunkControllerTest.MethodSecurityConfig.class)
class GuidanceChunkControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private static final String BASE = "/services/1/guidance-chunks";

    private static final String VALID_BODY = """
            {
              "content": "Texto fictício de teste.",
              "sourceUrl": "https://example.invalid/papertrail/1",
              "sourceTitle": "Fonte fictícia",
              "reviewedAt": "2026-11-01T10:00:00Z",
              "version": 2
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GuidanceChunkService chunkService;

    private GuidanceChunk chunk() {
        return GuidanceChunk.builder()
                .id(1000L).serviceId(1L)
                .content("Texto fictício de teste.")
                .sourceUrl("https://example.invalid/papertrail/1")
                .sourceTitle("Fonte fictícia")
                .reviewedAt(OffsetDateTime.parse("2026-11-01T10:00:00Z"))
                .version(2L)
                .build();
    }

    @Test
    void semAutenticacaoDevolve401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(chunkService);
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void cidadaoNaoListaTrechos() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
        verifyNoInteractions(chunkService);
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void cidadaoNaoCriaTrecho() throws Exception {
        mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(chunkService);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void gestorListaTrechosPaginados() throws Exception {
        when(chunkService.findChunks(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(chunk()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1000))
                .andExpect(jsonPath("$.content[0].serviceId").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void gestorCriaTrecho() throws Exception {
        when(chunkService.createChunk(eq(1L), any(GuidanceChunkRequest.class))).thenReturn(chunk());

        mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1000))
                .andExpect(jsonPath("$.sourceTitle").value("Fonte fictícia"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void corpoInvalidoDevolve400SemChamarServico() throws Exception {
        String invalid = """
                {
                  "content": " ",
                  "sourceUrl": "ftp://invalido",
                  "sourceTitle": "",
                  "reviewedAt": "2999-01-01T00:00:00Z",
                  "version": 0
                }
                """;

        mockMvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        verify(chunkService, never()).createChunk(any(), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void serviceIdNegativoDevolve400() throws Exception {
        mockMvc.perform(get("/services/-1/guidance-chunks")).andExpect(status().isBadRequest());
        verifyNoInteractions(chunkService);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void trechoInexistenteDevolve404() throws Exception {
        when(chunkService.findChunk(1L, 5L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trecho 5 não encontrado para o serviço 1"));

        mockMvc.perform(get(BASE + "/5")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void gestorAtualizaERemove() throws Exception {
        when(chunkService.updateChunk(eq(1L), eq(1000L), any(GuidanceChunkRequest.class))).thenReturn(chunk());

        mockMvc.perform(put(BASE + "/1000").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/1000").with(csrf())).andExpect(status().isNoContent());
        verify(chunkService).deleteChunk(1L, 1000L);
    }
}
