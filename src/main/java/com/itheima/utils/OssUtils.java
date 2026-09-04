package com.itheima.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 阿里云 OSS 上传工具类
 * 配置项来自 application-oss.yml（endpoint / bucket-name / access-key-id / access-key-secret）
 */
@Component
public class OssUtils {

    @Value("${oss.endpoint}")
    private String endpoint;

    @Value("${oss.bucket-name}")
    private String bucketName;

    @Value("${oss.access-key-id}")
    private String accessKeyId;

    @Value("${oss.access-key-secret}")
    private String accessKeySecret;

    /**
     * 上传文件到 OSS
     * @param in         文件输入流
     * @param objectName OSS 中的对象名（含路径，如 images/xxx.jpg）
     * @return 可直接在浏览器访问的公网 URL
     */
    public String upload(InputStream in, String objectName) {
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            client.putObject(bucketName, objectName, in);
        } finally {
            client.shutdown();
        }
        return "https://" + bucketName + "." + endpoint + "/" + objectName;
    }
}
