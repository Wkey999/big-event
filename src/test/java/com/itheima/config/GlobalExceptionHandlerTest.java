package com.itheima.config;

import com.itheima.pojo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownResourceReturns404Envelope() {
        var response = handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/no/such/path"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new Result<>(404, "接口不存在", null));
    }

    @Test
    void unknownHandlerReturns404Envelope() {
        var exception = new NoHandlerFoundException("GET", "/no/such/path", HttpHeaders.EMPTY);

        var response = handler.handleNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new Result<>(404, "接口不存在", null));
    }
}
