package br.com.fiap.PaperTrail.category.controller;

import br.com.fiap.PaperTrail.category.dto.CategoryRequest;
import br.com.fiap.PaperTrail.category.dto.CategoryResponse;
import br.com.fiap.PaperTrail.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/categories")
@PreAuthorize("hasAnyRole('CITIZEN','MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Categories")
public class CategoryController {
    private final CategoryService categories;

    @GetMapping
    @Operation(summary = "List categories")
    public PagedModel<CategoryResponse> findAll(@PageableDefault(sort = "id") Pageable pageable) {
        return new PagedModel<>(categories.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a category")
    public CategoryResponse findById(@PathVariable @Positive Long id) {
        return CategoryResponse.fromEntity(categories.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(@RequestBody @Valid CategoryRequest request) {
        var response = categories.create(request);
        return ResponseEntity.created(URI.create("/categories/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a category")
    public CategoryResponse update(@PathVariable @Positive Long id, @RequestBody @Valid CategoryRequest request) {
        return categories.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a category")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        categories.delete(id);
        return ResponseEntity.noContent().build();
    }
}
