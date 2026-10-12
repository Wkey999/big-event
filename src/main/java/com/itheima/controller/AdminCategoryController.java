package com.itheima.controller;

import com.itheima.pojo.Category;
import com.itheima.pojo.CategoryDTO;
import com.itheima.pojo.Result;
import com.itheima.service.CategoryService;
import com.itheima.validation.ValidationGroups;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Additive admin namespace; the legacy /category/** contract remains unchanged. */
@RestController
@RequestMapping("/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.list());
    }

    @GetMapping("/{id}")
    public Result<Category> detail(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@Validated(ValidationGroups.Add.class) @RequestBody CategoryDTO dto) {
        categoryService.add(toCategory(dto, null));
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(ValidationGroups.Add.class) @RequestBody CategoryDTO dto) {
        // The new admin contract takes the resource ID from the path, not the request body.
        categoryService.update(toCategory(dto, id));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }

    private static Category toCategory(CategoryDTO dto, Long id) {
        Category category = new Category();
        category.setId(id);
        category.setCategoryName(dto.getCategoryName());
        category.setSortOrder(dto.getSortOrder());
        category.setStatus(dto.getStatus());
        return category;
    }
}
