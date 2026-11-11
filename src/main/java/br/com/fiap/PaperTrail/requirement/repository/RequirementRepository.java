package br.com.fiap.PaperTrail.requirement.repository;

import br.com.fiap.PaperTrail.requirement.entity.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {}
