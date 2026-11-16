package br.com.fiap.PaperTrail.requirement.repository;

import br.com.fiap.PaperTrail.requirement.entity.Requirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {
    Page<Requirement> findByServiceId(Long serviceId, Pageable pageable);

    Optional<Requirement> findByIdAndServiceId(Long id, Long serviceId);
}
