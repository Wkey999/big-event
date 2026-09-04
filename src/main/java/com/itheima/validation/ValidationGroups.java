package com.itheima.validation;

import jakarta.validation.groups.Default;

/**
 * 校验分组定义
 * 同一个 DTO 在"新增"和"更新"时校验规则不同（比如更新必须有ID），
 * 用分组区分：@Validated(ValidationGroups.Add.class) 只校验 Add 组的注解。
 *
 * 注意：Add/Update 必须 extends Default，
 * 否则未指定 groups 的校验注解（默认属于 Default 分组）会被跳过，
 * 导致分类名称等字段在新增/更新时完全不校验。
 */
public interface ValidationGroups {

    /** 新增操作分组（含默认分组校验） */
    interface Add extends Default {}

    /** 更新操作分组（含默认分组校验） */
    interface Update extends Default {}
}
