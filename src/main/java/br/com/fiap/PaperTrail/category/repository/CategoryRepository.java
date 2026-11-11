package br.com.fiap.PaperTrail.category.repository;

import br.com.fiap.PaperTrail.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByNameAndIdNot(String name, Long id);
    boolean existsByName(String name);
}
