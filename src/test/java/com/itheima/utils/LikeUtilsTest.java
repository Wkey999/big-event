package com.itheima.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeUtilsTest {

    @Test
    void escapesBackslashPercentAndUnderscoreAsLikeLiterals() {
        assertThat(LikeUtils.escape("50%_off\\sale")).isEqualTo("50\\%\\_off\\\\sale");
        assertThat(LikeUtils.escape(null)).isNull();
    }
}
