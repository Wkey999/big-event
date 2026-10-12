package com.itheima.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AskLogMapper {

    @Insert("INSERT INTO ask_log(user_id, question, answer_len, reference_count, elapsed_ms, ttft_ms, model, success) " +
            "VALUES(#{userId}, #{question}, #{answerLen}, #{referenceCount}, #{elapsedMs}, #{ttftMs}, #{model}, #{success})")
    int insert(@Param("userId") Long userId,
               @Param("question") String question,
               @Param("answerLen") int answerLen,
               @Param("referenceCount") int referenceCount,
               @Param("elapsedMs") int elapsedMs,
               @Param("ttftMs") int ttftMs,
               @Param("model") String model,
               @Param("success") int success);
}
