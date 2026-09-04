package com.itheima.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @InValues 注解的校验逻辑实现
 * 框架遇到 @InValues 时会自动调用这个类做校验
 */
public class InValuesValidator implements ConstraintValidator<InValues, Integer> {

    private Set<Integer> allowedValues;

    @Override
    public void initialize(InValues annotation) {
        allowedValues = Arrays.stream(annotation.values()).boxed().collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        // null 交给 @NotNull 管，这里只判断值是否在集合内
        return value == null || allowedValues.contains(value);
    }
}
