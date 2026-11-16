package br.com.fiap.PaperTrail.category.service;

import br.com.fiap.PaperTrail.category.dto.CategoryRequest;
import br.com.fiap.PaperTrail.category.dto.CategoryResponse;
import br.com.fiap.PaperTrail.category.entity.Category;
import br.com.fiap.PaperTrail.category.repository.CategoryRepository;
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
public class CategoryService {
    private final CategoryRepository categories;

    public Page<CategoryResponse> findAll(Pageable pageable) {
        return categories.findAll(pageable).map(CategoryResponse::fromEntity);
    }

    public Category findById(Long id) {
        return categories.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found."));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categories.existsByName(request.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name is already in use.");
        }
        var category = new Category();
        category.setName(request.name());
        return CategoryResponse.fromEntity(categories.saveAndFlush(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findById(id);
        if (categories.existsByNameAndIdNot(request.name(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category name is already in use.");
        }
        category.setName(request.name());
        return CategoryResponse.fromEntity(categories.saveAndFlush(category));
    }

    @Transactional
    public void delete(Long id) {
        categories.delete(findById(id));
        categories.flush();
    }
}
