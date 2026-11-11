package br.com.fiap.PaperTrail.service.repository;

import br.com.fiap.PaperTrail.service.entity.PublicService;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PublicServiceRepository extends JpaRepository<PublicService, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select service from PublicService service where service.id = :id")
    Optional<PublicService> findForUpdate(Long id);

    @Query("select service.version from PublicService service where service.id = :id")
    Optional<Long> findCurrentVersion(Long id);

    // One query keeps the version and requirements from the same database snapshot.
    @Query("""
            select service.id as serviceId, service.version as version, service.title as serviceTitle,
                   requirement.id as requirementId, requirement.title as requirementTitle,
                   requirement.required as required, requirement.sourceUrl as sourceUrl
            from PublicService service
            left join Requirement requirement on requirement.service.id = service.id
            where service.id = :id
            order by requirement.id
            """)
    List<SnapshotRow> findSnapshot(Long id);

    interface SnapshotRow {
        Long getServiceId();
        Long getVersion();
        String getServiceTitle();
        Long getRequirementId();
        String getRequirementTitle();
        Boolean getRequired();
        String getSourceUrl();
    }
}
