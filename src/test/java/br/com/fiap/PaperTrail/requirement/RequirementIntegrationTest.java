package br.com.fiap.PaperTrail.requirement;

import br.com.fiap.PaperTrail.category.entity.Category;
import br.com.fiap.PaperTrail.category.repository.CategoryRepository;
import br.com.fiap.PaperTrail.requirement.entity.Requirement;
import br.com.fiap.PaperTrail.requirement.repository.RequirementRepository;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import br.com.fiap.PaperTrail.support.JwtTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Testa o CRUD de requisitos contra H2 (modo Oracle). Não comprova Oracle nem SQL Server. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "MANAGER")
class RequirementIntegrationTest extends JwtTestSupport {
    private static final long MISSING = 999_999_999L;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CategoryRepository categories;
    @Autowired private PublicServiceRepository services;
    @Autowired private RequirementRepository requirements;
    @Autowired private EntityManager entityManager;

    private PublicService service;
    private PublicService otherService;

    @BeforeEach
    void fixtures() {
        var category = new Category();
        category.setName("Cadastro");
        category = categories.saveAndFlush(category);
        service = service(category, "Cadastro municipal");
        otherService = service(category, "Credencial de mobilidade");
        entityManager.clear();
    }

    @Test
    void managerCanCreateReadUpdateAndDeleteARequirement() throws Exception {
        var created = mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Documento de identidade  ", true, "https://example.invalid/identidade")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Documento de identidade"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.serviceId").value(service.getId()))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith(path(service) + "/"));

        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").value("https://example.invalid/identidade"));
        mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Documento revisado", false, "https://example.invalid/revisado")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Documento revisado"))
                .andExpect(jsonPath("$.required").value(false));
        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    void everyWriteIncrementsTheServiceVersionAndTheSnapshotReflectsIt() throws Exception {
        assertEquals(1L, version(service));
        var created = mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Item", true, "https://example.invalid/a")))
                .andExpect(status().isCreated()).andReturn();
        assertEquals(2L, version(service));
        String location = created.getResponse().getHeader("Location");

        mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                .content(body("Item 2", true, "https://example.invalid/b"))).andExpect(status().isOk());
        assertEquals(3L, version(service));

        mvc.perform(get("/services/" + service.getId() + "/requirements-snapshot"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.requirements.length()").value(1))
                .andExpect(jsonPath("$.requirements[0].title").value("Item 2"));

        mvc.perform(delete(location)).andExpect(status().isNoContent());
        assertEquals(4L, version(service));
        assertEquals(1L, version(otherService));
    }

    @Test
    void readsDoNotChangeTheVersion() throws Exception {
        var requirement = requirement(service, "Item");
        mvc.perform(get(path(service))).andExpect(status().isOk());
        mvc.perform(get(path(service) + "/" + requirement.getId())).andExpect(status().isOk());
        assertEquals(1L, version(service));
    }

    @Test
    void listIsPaginatedSortedAndScopedToTheService() throws Exception {
        requirement(service, "Beta");
        requirement(service, "Alfa");
        requirement(otherService, "De outro serviço");
        entityManager.clear();
        mvc.perform(get(path(service) + "?page=0&size=1&sort=title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Alfa"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get(path(service) + "?sort=missing,asc")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCanReadButCannotWrite() throws Exception {
        var requirement = requirement(service, "Item");
        mvc.perform(get(path(service))).andExpect(status().isOk());
        mvc.perform(get(path(service) + "/" + requirement.getId())).andExpect(status().isOk());
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Novo", true, "https://example.invalid/a"))).andExpect(status().isForbidden());
        mvc.perform(put(path(service) + "/" + requirement.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Novo", true, "https://example.invalid/a"))).andExpect(status().isForbidden());
        mvc.perform(delete(path(service) + "/" + requirement.getId())).andExpect(status().isForbidden());
        assertEquals(1L, version(service));
    }

    @Test
    @WithAnonymousUser
    void unauthenticatedRequestsReturn401() throws Exception {
        mvc.perform(get(path(service))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Novo", true, "https://example.invalid/a"))).andExpect(status().isUnauthorized());
    }

    @Test
    void missingServiceReturns404WithoutCreatingAnything() throws Exception {
        String missing = "/services/" + MISSING + "/requirements";
        mvc.perform(get(missing)).andExpect(status().isNotFound());
        mvc.perform(post(missing).contentType(MediaType.APPLICATION_JSON)
                .content(body("Novo", true, "https://example.invalid/a"))).andExpect(status().isNotFound());
        assertEquals(0L, requirements.count());
    }

    @Test
    void requirementOfAnotherServiceReturns404AndKeepsBothVersions() throws Exception {
        var requirement = requirement(otherService, "Do outro serviço");
        String wrongPath = path(service) + "/" + requirement.getId();
        mvc.perform(get(wrongPath)).andExpect(status().isNotFound());
        mvc.perform(put(wrongPath).contentType(MediaType.APPLICATION_JSON)
                .content(body("Novo", true, "https://example.invalid/a"))).andExpect(status().isNotFound());
        mvc.perform(delete(wrongPath)).andExpect(status().isNotFound());
        mvc.perform(get(path(otherService) + "/" + requirement.getId()))
                .andExpect(jsonPath("$.title").value("Do outro serviço"));
        assertEquals(1L, version(service));
        assertEquals(1L, version(otherService));
    }

    @Test
    void invalidIdsReturn400() throws Exception {
        mvc.perform(get("/services/0/requirements")).andExpect(status().isBadRequest());
        mvc.perform(get(path(service) + "/0")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "required", "sourceUrl"})
    void missingFieldsReturn400AndDoNotChangeTheVersion(String missing) throws Exception {
        var body = new HashMap<String, Object>(Map.of("title", "Item", "required", true,
                "sourceUrl", "https://example.invalid/a"));
        body.remove(missing);
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        assertEquals(1L, version(service));
    }

    @Test
    void invalidValuesReturn400() throws Exception {
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                .content(body(" ", true, "https://example.invalid/a"))).andExpect(status().isBadRequest());
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                .content(body("x".repeat(201), true, "https://example.invalid/a"))).andExpect(status().isBadRequest());
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Item", true, "ftp://example.invalid/a"))).andExpect(status().isBadRequest());
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Item", true, "https://example.invalid/" + "a".repeat(2048)))).andExpect(status().isBadRequest());
        mvc.perform(post(path(service)).contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest());
        assertEquals(0L, requirements.count());
        assertEquals(1L, version(service));
    }

    private String path(PublicService target) {
        return "/services/" + target.getId() + "/requirements";
    }

    private long version(PublicService target) {
        entityManager.flush();
        entityManager.clear();
        return services.findCurrentVersion(target.getId()).orElseThrow();
    }

    private String body(String title, boolean required, String sourceUrl) throws Exception {
        return json.writeValueAsString(Map.of("title", title, "required", required, "sourceUrl", sourceUrl));
    }

    private PublicService service(Category category, String title) {
        var service = new PublicService();
        service.setCategory(category);
        service.setTitle(title);
        service.setDescription("Exemplo fictício");
        service.setCityName("Cidade de demonstração");
        return services.saveAndFlush(service);
    }

    private Requirement requirement(PublicService owner, String title) {
        var requirement = new Requirement();
        requirement.setService(owner);
        requirement.setTitle(title);
        requirement.setRequired(true);
        requirement.setSourceUrl("https://example.invalid/source");
        return requirements.saveAndFlush(requirement);
    }
}
