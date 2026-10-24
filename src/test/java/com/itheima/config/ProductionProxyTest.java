package com.itheima.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Tomcat request: validate the prod proxy settings without DB or model services. */
class ProductionProxyTest {
    @Test void sameHostProxyRestoresClientAddressAndHttpsScheme() throws Exception {
        var app = new SpringApplication(ProxyApp.class);
        app.setDefaultProperties(Map.of("spring.config.location", "classpath:application-prod.yml",
                "server.port", "0", "spring.main.banner-mode", "off"));
        try(var context=(ServletWebServerApplicationContext)app.run("--server.port=0")) {
            int port=context.getWebServer().getPort();
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/probe"))
                    .header("X-Forwarded-For","198.51.100.23")
                    .header("X-Forwarded-Proto","https").build();
            assertThat(HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).body())
                    .isEqualTo("198.51.100.23|https");
        }
    }

    @Configuration(proxyBeanMethods=false)
    @Import({ServletWebServerFactoryAutoConfiguration.class, EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class})
    static class ProxyApp {
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController
    static class Probe {
        @GetMapping("/probe") String probe(HttpServletRequest request) {
            return request.getRemoteAddr()+"|"+request.getScheme();
        }
    }
}
