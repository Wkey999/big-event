package com.itheima.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.itheima.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 阿里云 OSS 上传工具类
 * 配置项来自 application-oss.yml（endpoint / bucket-name / access-key-id / access-key-secret）
 *
 * OSS 客户端是线程安全的，之前每次上传都 new 一个再 shutdown，
 * 白白多出一次客户端初始化开销；改为懒加载单例，应用关闭时统一释放。
 */
@Component
public class OssUtils {

    @Value("${oss.endpoint:}")
    private String endpoint;

    // ECS uploads may use a private endpoint; browsers still need a public URL.
    @Value("${oss.public-base-url:}")
    private String publicBaseUrl;

    @Value("${oss.bucket-name:}")
    private String bucketName;

    @Value("${oss.access-key-id:}")
    private String accessKeyId;

    @Value("${oss.access-key-secret:}")
    private String accessKeySecret;

    /** 懒加载单例客户端（双重检查锁） */
    private volatile OSS client;

    private OSS client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
                }
            }
        }
        return client;
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    /**
     * 上传文件到 OSS
     * @param in         文件输入流
     * @param objectName OSS 中的对象名（含路径，如 images/xxx.jpg）
     * @return 可直接在浏览器访问的公网 URL
     */
    public String upload(InputStream in, String objectName) {
        if (endpoint.isBlank() || bucketName.isBlank() || accessKeyId.isBlank() || accessKeySecret.isBlank()) {
            throw new BusinessException("对象存储尚未配置");
        }
        String url = publicUrl(objectName);
        client().putObject(bucketName, objectName, in);
        return url;
    }

    String publicUrl(String objectName) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.strip().replaceAll("/+$", "") + "/" + objectName;
        }
        // endpoint 允许带 https:// 前缀配置，拼 URL 时统一剥掉，避免出现 https://bucket.https://... 的畸形地址
        String host = endpoint.strip().replaceFirst("^https?://", "").replaceAll("/+$", "");
        host = host.replaceFirst("-internal\\.aliyuncs\\.com$", ".aliyuncs.com");
        return "https://" + bucketName + "." + host + "/" + objectName;
    }
}
