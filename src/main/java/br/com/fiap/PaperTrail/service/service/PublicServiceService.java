package br.com.fiap.PaperTrail.service.service;

import br.com.fiap.PaperTrail.category.service.CategoryService;
import br.com.fiap.PaperTrail.service.dto.ServiceRequest;
import br.com.fiap.PaperTrail.service.dto.ServiceResponse;
import br.com.fiap.PaperTrail.service.dto.ServiceSnapshot;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicServiceService {
    private final PublicServiceRepository services;
    private final CategoryService categories;
    private final CatalogVersionService versions;

    public Page<ServiceResponse> findAll(Pageable pageable) {
        return services.findAll(pageable).map(ServiceResponse::fromEntity);
    }

    public ServiceResponse findById(Long id) {
        return services.findById(id).map(ServiceResponse::fromEntity).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        var service = new PublicService();
        apply(service, request);
        return ServiceResponse.fromEntity(services.saveAndFlush(service));
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        var service = versions.incrementVersion(id);
        apply(service, request);
        return ServiceResponse.fromEntity(services.saveAndFlush(service));
    }

    private void apply(PublicService service, ServiceRequest request) {
        service.setCategory(categories.findById(request.categoryId()));
        service.setTitle(request.title());
        service.setDescription(request.description());
        service.setCityName(request.cityName());
    }

    @Transactional
    public void delete(Long id) {
        var service = services.findForUpdate(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
        services.delete(service);
        services.flush();
    }

    public ServiceSnapshot snapshot(Long id) {
        var rows = services.findSnapshot(id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found.");
        }
        var first = rows.get(0);
        var requirements = rows.stream().filter(row -> row.getRequirementId() != null)
                .map(row -> new ServiceSnapshot.RequirementInfo(row.getRequirementId(), row.getRequirementTitle(),
                        row.getRequired(), row.getSourceUrl())).toList();
        return new ServiceSnapshot(first.getServiceId(), first.getVersion(), first.getServiceTitle(), requirements);
    }
}
