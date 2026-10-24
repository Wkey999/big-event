package com.itheima.utils;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OssUtilsTest {
    private OssUtils configured(String endpoint) {
        var oss = new OssUtils();
        ReflectionTestUtils.setField(oss, "endpoint", endpoint);
        ReflectionTestUtils.setField(oss, "bucketName", "boom-images");
        return oss;
    }

    @Test void publicEndpointRemainsCompatibleWithExistingImages() {
        assertThat(configured("https://oss-cn-hangzhou.aliyuncs.com/").publicUrl("images/a.png"))
                .isEqualTo("https://boom-images.oss-cn-hangzhou.aliyuncs.com/images/a.png");
    }

    @Test void internalUploadClientStillReturnsAPublicUrl() {
        var oss = configured("https://oss-cn-hangzhou-internal.aliyuncs.com");
        var client = mock(OSS.class);
        ReflectionTestUtils.setField(oss, "accessKeyId", "test-id");
        ReflectionTestUtils.setField(oss, "accessKeySecret", "test-secret");
        ReflectionTestUtils.setField(oss, "client", client);
        var input = new ByteArrayInputStream(new byte[]{1});
        assertThat(oss.upload(input, "images/a.png"))
                .isEqualTo("https://boom-images.oss-cn-hangzhou.aliyuncs.com/images/a.png");
        verify(client).putObject("boom-images", "images/a.png", input);
    }

    @Test void explicitPublicDomainOverridesSdkEndpoint() {
        var oss = configured("https://oss-cn-hangzhou-internal.aliyuncs.com");
        ReflectionTestUtils.setField(oss, "publicBaseUrl", "https://images.example.com/assets/");
        assertThat(oss.publicUrl("images/a.webp"))
                .isEqualTo("https://images.example.com/assets/images/a.webp");
    }
}
