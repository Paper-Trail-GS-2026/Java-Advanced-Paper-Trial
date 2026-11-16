package br.com.fiap.PaperTrail.requirement.service;

import br.com.fiap.PaperTrail.requirement.dto.RequirementRequest;
import br.com.fiap.PaperTrail.requirement.dto.RequirementResponse;
import br.com.fiap.PaperTrail.requirement.entity.Requirement;
import br.com.fiap.PaperTrail.requirement.repository.RequirementRepository;
import br.com.fiap.PaperTrail.service.CatalogReader;
import br.com.fiap.PaperTrail.service.service.CatalogVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Requisitos de um serviço. Toda escrita chama a operação central de versão do catálogo
 * (CatalogVersionService.incrementVersion) na mesma transação; este service nunca altera a versão por conta própria.
 */
@Service
@RequiredArgsConstructor
public class RequirementService {
    private final RequirementRepository requirements;
    private final CatalogReader catalog;
    private final CatalogVersionService versions;

    @Transactional(readOnly = true)
    public Page<RequirementResponse> findAll(Long serviceId, Pageable pageable) {
        catalog.currentVersion(serviceId); // 404 se o serviço não existe
        return requirements.findByServiceId(serviceId, pageable).map(RequirementResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public RequirementResponse findById(Long serviceId, Long id) {
        return RequirementResponse.fromEntity(find(serviceId, id));
    }

    @Transactional
    public RequirementResponse create(Long serviceId, RequirementRequest request) {
        var service = versions.incrementVersion(serviceId); // 404 se o serviço não existe
        var requirement = new Requirement();
        requirement.setService(service);
        apply(requirement, request);
        return RequirementResponse.fromEntity(requirements.saveAndFlush(requirement));
    }

    @Transactional
    public RequirementResponse update(Long serviceId, Long id, RequirementRequest request) {
        var requirement = find(serviceId, id);
        versions.incrementVersion(serviceId);
        apply(requirement, request);
        return RequirementResponse.fromEntity(requirements.saveAndFlush(requirement));
    }

    @Transactional
    public void delete(Long serviceId, Long id) {
        var requirement = find(serviceId, id);
        versions.incrementVersion(serviceId);
        requirements.delete(requirement);
        requirements.flush();
    }

    private Requirement find(Long serviceId, Long id) {
        return requirements.findByIdAndServiceId(id, serviceId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requisito não encontrado para o serviço."));
    }

    private void apply(Requirement requirement, RequirementRequest request) {
        requirement.setTitle(request.title().strip());
        requirement.setRequired(request.required());
        requirement.setSourceUrl(request.sourceUrl().strip());
    }
}
