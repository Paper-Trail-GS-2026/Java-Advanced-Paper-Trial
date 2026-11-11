package br.com.fiap.PaperTrail.catalog;

import br.com.fiap.PaperTrail.category.entity.Category;
import br.com.fiap.PaperTrail.category.repository.CategoryRepository;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.repository.GuidanceChunkRepository;
import br.com.fiap.PaperTrail.requirement.entity.Requirement;
import br.com.fiap.PaperTrail.requirement.repository.RequirementRepository;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import br.com.fiap.PaperTrail.service.service.CatalogVersionService;
import br.com.fiap.PaperTrail.service.service.PublicServiceService;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(roles = "MANAGER")
// Hibernate does not generate this existing Oracle FK from the chunk's plain serviceId field.
@Sql(statements = "ALTER TABLE PT_GUIDANCE_CHUNK ADD CONSTRAINT IF NOT EXISTS FK_PT_CHUNK_SERVICE FOREIGN KEY (SERVICE_ID) REFERENCES PT_SERVICE (ID)")
class CatalogIntegrationTest extends JwtTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private CategoryRepository categories;
    @Autowired private PublicServiceRepository services;
    @Autowired private RequirementRepository requirements;
    @Autowired private GuidanceChunkRepository chunks;
    @Autowired private CatalogVersionService versions;
    @Autowired private PublicServiceService catalog;
    @Autowired private EntityManager entityManager;

    private Category category;
    private Category otherCategory;
    private PublicService service;

    @BeforeEach
    void fixtures() {
        category = category("Cadastro");
        otherCategory = category("Mobilidade");
        service = service(category, "Cadastro municipal");
        service(otherCategory, "Credencial de mobilidade");
        entityManager.clear();
    }

    @Test
    void managerCanCreateUpdateReadAndDeleteACategory() throws Exception {
        var created = mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Saúde  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Saúde"))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);
        mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Saúde e atendimento\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Saúde e atendimento"));
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Saúde e atendimento"));
        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    void managerCanCreateUpdateReadAndDeleteAService() throws Exception {
        var created = mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content(serviceBody("  Atendimento novo  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Atendimento novo"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertNotNull(location);
        mvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(serviceBody("Atendimento atualizado")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Atendimento atualizado"));
        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCanReadBothCatalogsAndTheSnapshot() throws Exception {
        mvc.perform(get("/categories")).andExpect(status().isOk());
        mvc.perform(get("/categories/" + category.getId())).andExpect(status().isOk());
        mvc.perform(get("/services")).andExpect(status().isOk());
        mvc.perform(get("/services/" + service.getId())).andExpect(status().isOk());
        mvc.perform(get("/services/" + service.getId() + "/requirements-snapshot")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotWriteEitherCatalog() throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/categories/" + category.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/categories/" + category.getId())).andExpect(status().isForbidden());
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content(serviceBody("New")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/services/" + service.getId()).contentType(MediaType.APPLICATION_JSON).content(serviceBody("New")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/services/" + service.getId())).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void unauthenticatedRequestsReturn401() throws Exception {
        mvc.perform(get("/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/services")).andExpect(status().isUnauthorized());
        mvc.perform(get("/services/" + service.getId() + "/requirements-snapshot")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsArePaginatedAndSorted() throws Exception {
        mvc.perform(get("/categories?page=1&size=1&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Mobilidade"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/services?page=0&size=1&sort=title,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Credencial de mobilidade"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void invalidSortAndIdsReturn400() throws Exception {
        mvc.perform(get("/categories?sort=missing,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/services?sort=missing,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/categories/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/services/-1")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":\" \"}", "{}"})
    void blankCategoriesReturn400(String body) throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void categoryNameMustFitTheDatabaseColumn() throws Exception {
        String body = json.writeValueAsString(Map.of("name", "x".repeat(101)));
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCategoryNamesReturn409() throws Exception {
        mvc.perform(post("/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cadastro\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/categories/" + otherCategory.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cadastro\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void categoriesWithServicesCannotBeDeleted() throws Exception {
        var result = mvc.perform(delete("/categories/" + category.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Catalog data conflicts with existing records."))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("FK_PT_"));
    }

    @Test
    void servicesWithRequirementsCannotBeDeleted() throws Exception {
        requirement("Documento", true);
        entityManager.clear();
        mvc.perform(delete("/services/" + service.getId())).andExpect(status().isConflict());
    }

    @Test
    void servicesWithGuidanceSourcesCannotBeDeleted() throws Exception {
        chunks.saveAndFlush(GuidanceChunk.builder().serviceId(service.getId()).content("Fonte fictícia")
                .sourceTitle("Exemplo").sourceUrl("https://example.invalid/source")
                .reviewedAt(OffsetDateTime.now()).version(1L).build());
        mvc.perform(delete("/services/" + service.getId())).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "description", "cityName", "categoryId"})
    void missingServiceFieldsReturn400(String missing) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("categoryId", category.getId(), "title", "Title",
                "description", "Description", "cityName", "City"));
        body.remove(missing);
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCategoryAndOversizedServiceFieldsAreRejected() throws Exception {
        String body = json.writeValueAsString(Map.of("categoryId", 0, "title", "x".repeat(161),
                "description", "x".repeat(2001), "cityName", "x".repeat(121)));
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        String missingCategory = json.writeValueAsString(Map.of("categoryId", 999999999L, "title", "Title",
                "description", "Description", "cityName", "City"));
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content(missingCategory))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingResourcesReturn404() throws Exception {
        mvc.perform(get("/categories/999999999")).andExpect(status().isNotFound());
        mvc.perform(put("/categories/999999999").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/categories/999999999")).andExpect(status().isNotFound());
        mvc.perform(get("/services/999999999")).andExpect(status().isNotFound());
        mvc.perform(put("/services/999999999").contentType(MediaType.APPLICATION_JSON).content(serviceBody("New")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/services/999999999")).andExpect(status().isNotFound());
        mvc.perform(get("/services/999999999/requirements-snapshot")).andExpect(status().isNotFound());
    }

    @Test
    void snapshotIncludesRequiredAndOptionalItemsInIdOrder() throws Exception {
        Requirement required = requirement("Identificação", true);
        Requirement optional = requirement("Anotação opcional", false);
        mvc.perform(get("/services/" + service.getId() + "/requirements-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(service.getId()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("Cadastro municipal"))
                .andExpect(jsonPath("$.requirements.length()").value(2))
                .andExpect(jsonPath("$.requirements[0].id").value(required.getId()))
                .andExpect(jsonPath("$.requirements[0].required").value(true))
                .andExpect(jsonPath("$.requirements[0].sourceUrl").value("https://example.invalid/source"))
                .andExpect(jsonPath("$.requirements[1].id").value(optional.getId()))
                .andExpect(jsonPath("$.requirements[1].required").value(false));
    }

    @Test
    void serviceWithoutRequirementsHasAnEmptySnapshot() throws Exception {
        mvc.perform(get("/services/" + service.getId() + "/requirements-snapshot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(service.getId()))
                .andExpect(jsonPath("$.requirements").isEmpty());
    }

    @Test
    void updatingRequirementsAndVersionKeepsPreviouslyReturnedSnapshotsStable() {
        var requirement = requirement("Original", true);
        var before = catalog.snapshot(service.getId());
        versions.incrementVersion(service.getId());
        requirement.setTitle("Updated");
        requirements.saveAndFlush(requirement);
        var after = catalog.snapshot(service.getId());
        assertEquals(1L, before.version());
        assertEquals("Original", before.requirements().get(0).title());
        assertEquals(2L, after.version());
        assertEquals("Updated", after.requirements().get(0).title());
    }

    private Category category(String name) {
        var category = new Category();
        category.setName(name);
        return categories.saveAndFlush(category);
    }

    private PublicService service(Category category, String title) {
        var service = new PublicService();
        service.setCategory(category);
        service.setTitle(title);
        service.setDescription("Exemplo fictício");
        service.setCityName("Cidade de demonstração");
        return services.saveAndFlush(service);
    }

    private Requirement requirement(String title, boolean required) {
        var requirement = new Requirement();
        requirement.setService(service);
        requirement.setTitle(title);
        requirement.setRequired(required);
        requirement.setSourceUrl("https://example.invalid/source");
        return requirements.saveAndFlush(requirement);
    }

    private String serviceBody(String title) throws Exception {
        return json.writeValueAsString(Map.of("categoryId", category.getId(), "title", title,
                "description", "Exemplo fictício", "cityName", "Cidade de demonstração"));
    }
}
