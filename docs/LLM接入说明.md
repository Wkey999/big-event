# 大模型本地接入说明

## 版本与兼容性

项目使用 Spring AI **1.0.9**。Spring AI 1.0.x 官方文档列出的兼容范围包含 Spring Boot 3.4.x；本项目保持 Spring Boot 3.4.1，通过 Spring AI BOM 管理 AI 依赖版本。

## 配置步骤

1. 将仓库根目录的 `application-llm.example.yml` 复制为同目录的 `application-llm.yml`。后者已加入 `.gitignore`，不要提交。
2. 在本机环境变量中设置 `LLM_BASE_URL`、`LLM_API_KEY` 和 `LLM_MODEL`。服务商需提供 OpenAI 兼容接口；URL、模型名称按服务商文档填写。
3. 启动开发环境并登录后，请求 `GET /dev/echo?q=你好`。该接口仅在 `dev` profile 且 `app.llm.enabled=true` 时注册，并继续经过现有 JWT 拦截器。
4. 配置生效后，`POST /article/ask` 接收 `{"question":"帮我找一些 Spring 文章"}`，返回 `answer` 和 `references`。

未配置 `application-llm.yml` 时，`spring.ai.model.chat` 默认是 `none`，LLM Bean 与开发探针均不会启用，原有社区功能不依赖模型服务即可启动。配置文件放在项目根目录而不是 `src/main/resources`，避免被 Maven 复制进 JAR。

## 安全边界

- API Key 仅从本地忽略配置引用的环境变量读取，不写入源码、日志、响应体或 Git。
- `/dev/echo` 限制输入长度为 200 个字符；端点只用于联通性学习，不应在生产 profile 开放。
- Agent 仅暴露两个只读工具：查询已发布文章（最多 20 条）和读取当前登录用户自己的偏好画像；工具不接收 userId，也没有写操作工具。
- 引用列表由服务端从工具实际返回的文章中收集，不信任模型生成的文章 ID。工具查询强制 `state=1 AND deleted=0`。
- 外部模型调用失败时，问答接口返回通用错误，不回传服务商原始响应或异常正文。真实调用仍需本机配置有效模型凭证。

参考：Spring AI Getting Started（1.0）<https://docs.spring.io/spring-ai/reference/1.0/getting-started.html>；OpenAI Chat 配置 <https://docs.spring.io/spring-ai/reference/1.0/api/chat/openai-chat.html>。
