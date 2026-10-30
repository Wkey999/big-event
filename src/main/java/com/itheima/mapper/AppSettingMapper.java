package com.itheima.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppSettingMapper {

    /** 不存在时返回 null。 */
    @Select("SELECT setting_value FROM app_setting WHERE setting_key = #{key}")
    String findValue(@Param("key") String key);

    @Update("INSERT INTO app_setting (setting_key, setting_value, updated_by) "
            + "VALUES (#{key}, #{value}, #{updatedBy}) "
            + "ON DUPLICATE KEY UPDATE setting_value = #{value}, updated_by = #{updatedBy}, update_time = NOW()")
    int upsert(@Param("key") String key, @Param("value") String value, @Param("updatedBy") Long updatedBy);
}
