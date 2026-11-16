package br.com.fiap.PaperTrail.requirement;

import br.com.fiap.PaperTrail.requirement.dto.RequirementRequest;
import br.com.fiap.PaperTrail.requirement.entity.Requirement;
import br.com.fiap.PaperTrail.requirement.repository.RequirementRepository;
import br.com.fiap.PaperTrail.requirement.service.RequirementService;
import br.com.fiap.PaperTrail.service.CatalogReader;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.service.CatalogVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Teste unitário com mocks: verifica a regra de versão, não o banco. */
class RequirementServiceTest {
    private RequirementRepository repository;
    private CatalogVersionService versions;
    private RequirementService service;
    private final RequirementRequest request =
            new RequirementRequest(" Título ", true, " https://example.invalid/a ");

    @BeforeEach
    void setUp() {
        repository = mock(RequirementRepository.class);
        versions = mock(CatalogVersionService.class);
        service = new RequirementService(repository, mock(CatalogReader.class), versions);
        when(repository.saveAndFlush(any(Requirement.class))).thenAnswer(inv -> {
            Requirement saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });
    }

    @Test
    void createIncrementsTheVersionBeforeSavingAndTrimsText() {
        var owner = new PublicService();
        owner.setId(5L);
        when(versions.incrementVersion(5L)).thenReturn(owner);

        var response = service.create(5L, request);

        InOrder order = inOrder(versions, repository);
        order.verify(versions).incrementVersion(5L);
        order.verify(repository).saveAndFlush(any(Requirement.class));
        assertEquals("Título", response.title());
        assertEquals("https://example.invalid/a", response.sourceUrl());
        assertEquals(5L, response.serviceId());
    }

    @Test
    void createDoesNotSaveWhenTheServiceDoesNotExist() {
        when(versions.incrementVersion(5L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThrows(ResponseStatusException.class, () -> service.create(5L, request));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateAndDeleteWithoutAMatchingRequirementReturn404AndKeepTheVersion() {
        when(repository.findByIdAndServiceId(anyLong(), anyLong())).thenReturn(Optional.empty());

        var update = assertThrows(ResponseStatusException.class, () -> service.update(5L, 9L, request));
        var delete = assertThrows(ResponseStatusException.class, () -> service.delete(5L, 9L));
        assertEquals(HttpStatus.NOT_FOUND, update.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, delete.getStatusCode());
        verifyNoInteractions(versions);
    }

    @Test
    void deleteIncrementsTheVersionAndRemovesTheRequirement() {
        var owner = new PublicService();
        owner.setId(5L);
        var requirement = new Requirement();
        requirement.setService(owner);
        when(repository.findByIdAndServiceId(9L, 5L)).thenReturn(Optional.of(requirement));

        service.delete(5L, 9L);

        verify(versions).incrementVersion(5L);
        verify(repository).delete(requirement);
    }
}
