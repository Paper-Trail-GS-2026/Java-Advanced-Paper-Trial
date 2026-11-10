package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.guidance.controller.GuidanceController;
import br.com.fiap.PaperTrail.guidance.dto.GuidanceResponse;
import br.com.fiap.PaperTrail.guidance.service.GuidanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada web com serviço mockado (teste unitário). */
@WebMvcTest(GuidanceController.class)
@Import(GuidanceControllerTest.MethodSecurityConfig.class)
class GuidanceControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    private static final String URL = "/services/1/guidance";
    private static final String BODY = "{\"question\": \"Quais documentos preciso?\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GuidanceService guidanceService;

    @Test
    void semAutenticacaoDevolve401() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(guidanceService);
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void cidadaoRecebeRespostaComFontes() throws Exception {
        when(guidanceService.ask(1L, "Quais documentos preciso?")).thenReturn(new GuidanceResponse(
                "Segundo a fonte [1]...",
                List.of(new GuidanceResponse.Source("https://example.invalid/1", "Fonte", OffsetDateTime.parse("2026-11-01T00:00:00Z"))),
                false));

        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Segundo a fonte [1]..."))
                .andExpect(jsonPath("$.sources[0].sourceUrl").value("https://example.invalid/1"))
                .andExpect(jsonPath("$.insufficientEvidence").value(false));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void perguntaEmBrancoDevolve400() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"question\": \"  \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(guidanceService);
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void falhaDoModeloDevolve503() throws Exception {
        when(guidanceService.ask(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "indisponível"));

        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
