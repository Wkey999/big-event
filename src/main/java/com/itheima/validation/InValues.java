package com.itheima.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定义校验注解：限制字段值必须在指定的整数集合内
 * 用法：@InValues(values = {0, 1}, message = "状态只能是0或1")
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = InValuesValidator.class)
public @interface InValues {

    /** 允许的取值集合 */
    int[] values();

    String message() default "字段值不在允许范围内";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
