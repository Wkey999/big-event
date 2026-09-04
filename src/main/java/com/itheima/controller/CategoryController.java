package com.itheima.controller;

import com.itheima.pojo.Category;
import com.itheima.pojo.CategoryDTO;
import com.itheima.pojo.Result;
import com.itheima.service.CategoryService;
import com.itheima.validation.ValidationGroups;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.list());
    }

    /**
     * 新增文章分类（Add分组校验：名称必填，不需要id）
     */
    @PostMapping("/add")
    public Result<Void> add(@Validated(ValidationGroups.Add.class) @RequestBody CategoryDTO dto) {
        categoryService.add(toCategory(dto));
        return Result.success();
    }

    @GetMapping("/detail/{id}")
    public Result<Category> detail(@PathVariable Long id) {
        return Result.success(categoryService.getById(id));
    }

    /**
     * 更新文章分类（Update分组校验：id和名称都必填）
     */
    @PutMapping("/update")
    public Result<Void> update(@Validated(ValidationGroups.Update.class) @RequestBody CategoryDTO dto) {
        categoryService.update(toCategory(dto));
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }

    private Category toCategory(CategoryDTO dto) {
        Category category = new Category();
        category.setId(dto.getId());
        category.setCategoryName(dto.getCategoryName());
        category.setSortOrder(dto.getSortOrder());
        category.setStatus(dto.getStatus());
        return category;
    }
}
