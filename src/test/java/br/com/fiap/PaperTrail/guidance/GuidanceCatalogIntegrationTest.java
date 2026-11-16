package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.category.entity.Category;
import br.com.fiap.PaperTrail.category.repository.CategoryRepository;
import br.com.fiap.PaperTrail.guidance.ai.GuidanceModel;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.repository.GuidanceChunkRepository;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import br.com.fiap.PaperTrail.service.service.CatalogVersionService;
import br.com.fiap.PaperTrail.support.JwtTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guidance-catalog;MODE=Oracle;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "CITIZEN")
class GuidanceCatalogIntegrationTest extends JwtTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private CategoryRepository categories;
    @Autowired private PublicServiceRepository services;
    @Autowired private GuidanceChunkRepository chunks;
    @Autowired private CatalogVersionService versions;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private GuidanceModel model;

    private Long categoryId;
    private Long serviceId;
    private final List<Long> serviceIds = new ArrayList<>();
    private final List<Long> chunkIds = new ArrayList<>();

    @BeforeEach
    void fixtures() {
        var category = new Category();
        category.setName("Guidance " + UUID.randomUUID());
        categoryId = categories.saveAndFlush(category).getId();
        serviceId = service();
    }

    @AfterEach
    void cleanup() {
        chunks.deleteAllByIdInBatch(chunkIds);
        services.deleteAllByIdInBatch(serviceIds);
        categories.deleteById(categoryId);
    }

    @Test
    void onlyCurrentVersionOfRequestedServiceReachesModel() throws Exception {
        chunk(serviceId, 1, "old");
        var current = chunk(serviceId, 2, "current");
        chunk(serviceId, 3, "future");
        chunk(service(), 2, "other-service");
        when(model.answer(anyString(), anyList())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return "Resposta de teste";
        });

        ask(serviceId).andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence").value(false))
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].sourceUrl").value(current.getSourceUrl()));

        verify(model).answer(anyString(), argThat(sources ->
                sources.size() == 1 && sources.get(0).getId().equals(current.getId())));
    }

    @Test
    void oldAndFutureSourcesDoNotReplaceMissingCurrentSources() throws Exception {
        chunk(serviceId, 1, "old");
        chunk(serviceId, 3, "future");

        expectInsufficientEvidence(serviceId);

        verifyNoInteractions(model);
    }

    @Test
    void sourcesFromAnotherServiceDoNotProvideEvidence() throws Exception {
        chunk(service(), 2, "other-service");

        expectInsufficientEvidence(serviceId);

        verifyNoInteractions(model);
    }

    @Test
    void versionIncrementRequiresSourcesForTheNewVersion() throws Exception {
        chunk(serviceId, 2, "current");
        when(model.answer(anyString(), anyList())).thenReturn("Resposta de teste");
        ask(serviceId).andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence").value(false));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> versions.incrementVersion(serviceId));

        expectInsufficientEvidence(serviceId);
        verify(model, times(1)).answer(anyString(), anyList());

        var updated = chunk(serviceId, 3, "updated");
        ask(serviceId).andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence").value(false))
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].sourceUrl").value(updated.getSourceUrl()));
        verify(model, times(2)).answer(anyString(), anyList());
    }

    @Test
    void missingServiceReturns404() throws Exception {
        ask(999999999L).andExpect(status().isNotFound());

        verifyNoInteractions(model);
    }

    private ResultActions ask(Long id) throws Exception {
        return mvc.perform(post("/services/" + id + "/guidance").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Qual documento de identificação preciso?\"}"));
    }

    private void expectInsufficientEvidence(Long id) throws Exception {
        ask(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.insufficientEvidence").value(true))
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    private Long service() {
        var service = new PublicService();
        service.setCategory(categories.getReferenceById(categoryId));
        service.setTitle("Guidance test");
        service.setDescription("Fictitious test data");
        service.setCityName("Test city");
        service.setVersion(2L);
        Long id = services.saveAndFlush(service).getId();
        serviceIds.add(id);
        return id;
    }

    private GuidanceChunk chunk(Long id, long version, String source) {
        var chunk = chunks.saveAndFlush(GuidanceChunk.builder().serviceId(id).version(version)
                .content("Documento de identificação para o cadastro fictício.")
                .sourceTitle(source).sourceUrl("https://example.invalid/" + source)
                .reviewedAt(OffsetDateTime.parse("2026-11-01T00:00:00Z")).build());
        chunkIds.add(chunk.getId());
        return chunk;
    }
}

