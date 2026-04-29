# 企业微信接入文档

`codebot` 支持两类企业微信接入：

1. **本地网页提问 + 企微群机器人推送**：不需要企业微信管理员权限，只需要群机器人 webhook。
2. **企微机器人回调**：需要企业微信后台权限，用于在群里 `@机器人` 提问。

两种方式可以同时配置。回调模式负责接收群消息，群机器人 webhook 负责把答案发回群。

## 接入方式对比

| 能力               | 网页提问 + webhook 推送 | 企微机器人回调        |
|------------------|-------------------|----------------|
| 是否需要企微管理员        | 否                 | 是              |
| 是否能读取群消息         | 否                 | 是              |
| 是否能群里 `@机器人` 提问  | 否                 | 是              |
| 是否需要群机器人 webhook | 需要，用于推送答案         | 建议配置，用于回调后推送答案 |
| 适合场景             | 快速落地、无回调权限        | 正式群内交互         |

## 方式一：网页提问 + 群机器人推送

### 流程

```text
成员打开 codebot 页面
  -> 输入问题
  -> codebot 检索目标代码仓库
  -> 调用大模型生成答案
  -> 通过群机器人 webhook 推送到企微群
```

### 配置步骤

1. 打开目标企业微信群。
2. 添加群机器人。
3. 复制机器人 webhook。
4. 配置 `WECOM_ROBOT_WEBHOOK_URL`。
5. 启动 `codebot`。
6. 打开 `http://服务地址:18080/`。
7. 输入问题并勾选“同步发送答案到企微群”。

PowerShell 示例：

```powershell
$env:CODEBOT_REPOSITORY_PATH="D:\path\to\target-repository"
$env:OPENAI_BASE_URL="https://api.openai.com/v1"
$env:OPENAI_API_KEY="sk-xxx"
$env:OPENAI_MODEL="gpt-4.1-mini"
$env:WECOM_ROBOT_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"

mvn spring-boot:run
```

如果希望由 `codebot` 直接拉取远程仓库，把 `CODEBOT_REPOSITORY_PATH` 换成远程仓库配置：

私有仓库建议使用 SSH key 或 Git credential helper，不建议把 token 直接写进 URL。

```powershell
$env:CODEBOT_REPOSITORY_URL="https://github.com/your-org/your-repo.git"
$env:CODEBOT_REPOSITORY_CACHE_PATH=".codebot/repositories"
$env:CODEBOT_BRANCH="main"
```

Linux / macOS 示例：

```bash
export CODEBOT_REPOSITORY_PATH="/opt/code/target-repository"
export OPENAI_BASE_URL="https://api.openai.com/v1"
export OPENAI_API_KEY="sk-xxx"
export OPENAI_MODEL="gpt-4.1-mini"
export WECOM_ROBOT_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"

mvn spring-boot:run
```

远程仓库模式：

```bash
export CODEBOT_REPOSITORY_URL="https://github.com/your-org/your-repo.git"
export CODEBOT_REPOSITORY_CACHE_PATH=".codebot/repositories"
export CODEBOT_BRANCH="main"
```

远程仓库模式下，`CODEBOT_BRANCH` 就是要拉取并索引的分支。首次运行会克隆该分支；后续重新索引会从远端更新并切换到 `origin/<CODEBOT_BRANCH>` 对应的代码。没有配置时默认使用 `main`。

也可以写入 `application-local.yml`：

```yaml
codebot:
  # repository-path 和 repository-url 二选一；配置 repository-url 时优先使用远程仓库。
  repository-path: /path/to/your-target-repository
  repository-url: https://github.com/your-org/your-repo.git
  repository-cache-path: .codebot/repositories
  branch: main
  wecom:
    webhook-url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
  llm:
    base-url: https://api.openai.com/v1
    api-key: sk-xxx
    model: gpt-4.1-mini
```

建议从项目根目录的 `application-local.example.yml` 复制生成本地配置：

```powershell
Copy-Item .\application-local.example.yml .\application-local.yml
```

Linux / macOS:

```bash
cp application-local.example.yml application-local.yml
```

然后启动时显式加载：

```powershell
java -jar target\codebot-1.0.0-SNAPSHOT.jar --spring.config.additional-location=file:./application-local.yml
```

Linux / macOS:

```bash
java -jar target/codebot-1.0.0-SNAPSHOT.jar --spring.config.additional-location=file:./application-local.yml
```

### 验证

打开页面：

```text
http://127.0.0.1:18080/
```

或直接调用接口：

```powershell
curl -X POST http://127.0.0.1:18080/api/v1/code-bot/web/ask `
  -H "Content-Type: application/json" `
  -d "{\"askedBy\":\"张三\",\"question\":\"用户登录逻辑在哪里实现？\",\"sendToGroup\":true}"
```

Linux / macOS:

```bash
curl -X POST http://127.0.0.1:18080/api/v1/code-bot/web/ask \
  -H "Content-Type: application/json" \
  -d '{"askedBy":"张三","question":"用户登录逻辑在哪里实现？","sendToGroup":true}'
```

如果接口有答案但群里没有消息，优先检查 webhook 是否正确、机器人是否仍在群里、服务日志是否有 `WeCom robot send failed`。

## 方式二：企微机器人回调

### 流程

```text
成员在群里 @机器人 提问
  -> 企业微信把加密消息回调到 codebot
  -> codebot 校验签名并解密
  -> codebot 检索目标代码仓库并生成答案
  -> codebot 通过群机器人 webhook 把答案发回群
```

当前实现收到回调后立即返回 `success`，回答在后台异步发送，避免企业微信回调等待超时。

### 前置条件

- 有企业微信后台配置权限。
- `codebot` 服务有公网 HTTPS 地址，企业微信后台能够访问。
- 已准备回调 `Token` 和 `EncodingAESKey`。
- 建议同时准备群机器人 webhook，用于发送答案。

### codebot 配置

```yaml
codebot:
  # repository-path 和 repository-url 二选一；配置 repository-url 时优先使用远程仓库。
  repository-path: /path/to/your-target-repository
  repository-url: https://github.com/your-org/your-repo.git
  repository-cache-path: .codebot/repositories
  branch: main
  wecom:
    token: 企微回调Token
    encoding-aes-key: 企微回调EncodingAESKey
    receive-id: 企业IDcorpId或留空
    strict-receive-id: false
    webhook-url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
  llm:
    base-url: https://api.openai.com/v1
    api-key: sk-xxx
    model: gpt-4.1-mini
```

环境变量写法：

```powershell
$env:WECOM_CALLBACK_TOKEN="企微回调Token"
$env:WECOM_ENCODING_AES_KEY="企微回调EncodingAESKey"
$env:WECOM_RECEIVE_ID="企业IDcorpId或留空"
$env:WECOM_ROBOT_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
```

Linux / macOS：

```bash
export WECOM_CALLBACK_TOKEN="企微回调Token"
export WECOM_ENCODING_AES_KEY="企微回调EncodingAESKey"
export WECOM_RECEIVE_ID="企业IDcorpId或留空"
export WECOM_ROBOT_WEBHOOK_URL="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx"
```

`EncodingAESKey` 必须是 43 位。`receive-id` 是否必填取决于企微回调类型；如果不确定，可以先留空并保持
`strict-receive-id: false`。

### 企业微信后台配置

在企业微信后台创建支持消息回调的机器人或应用回调能力，并填写：

| 企业微信配置         | 填写内容                                          |
|----------------|-----------------------------------------------|
| URL            | `https://你的域名/api/v1/code-bot/wecom/callback` |
| Token          | 与 `codebot.wecom.token` 完全一致                  |
| EncodingAESKey | 与 `codebot.wecom.encoding-aes-key` 完全一致       |

服务端接口：

```text
GET  /api/v1/code-bot/wecom/callback
POST /api/v1/code-bot/wecom/callback
```

`GET` 用于企业微信 URL 验证。验证通过后，企业微信才会向 `POST` 接口推送群消息。

### 群内使用

配置完成后，在群里发送：

```text
@代码助手 用户登录逻辑在哪里实现？
```

服务会去掉消息里的 `@xxx` 内容，把剩余文本作为问题。

### 验证

1. 企业微信后台 URL 验证通过。
2. 群里 `@机器人 测试问题`。
3. 服务日志出现 `Received WeCom message`。
4. 群里收到 `代码问答` 消息。

如果服务日志收到了消息但群里没有回答，通常是 `codebot.wecom.webhook-url` 没有配置或 webhook 无效。

## 配置项说明

| 配置项                               | 说明                           |
|-----------------------------------|------------------------------|
| `codebot.wecom.webhook-url`       | 群机器人 webhook，用于发送答案到群        |
| `codebot.wecom.token`             | 企业微信回调 Token，用于签名校验          |
| `codebot.wecom.encoding-aes-key`  | 企业微信回调 EncodingAESKey，用于消息解密 |
| `codebot.wecom.receive-id`        | 企业 ID corpId 或回调接收 ID        |
| `codebot.wecom.strict-receive-id` | 是否严格校验解密消息中的 receive-id      |

远程仓库相关配置：

| 配置项 | 说明 |
| --- | --- |
| `codebot.repository-url` | 远程 Git 仓库 URL；配置后优先于 `repository-path` |
| `codebot.repository-cache-path` | 远程仓库本地缓存目录，默认 `.codebot/repositories` |
| `codebot.branch` | 要拉取并索引的远程分支，默认 `main` |
| `codebot.admin-token` | 可选管理 token；配置后保护 `/admin/reindex` 和 `/debug/ask` |

## 常见问题

### 只配置 webhook 为什么不能群里 @机器人？

webhook 是单向推送入口，只能让 `codebot` 往群里发消息。它不能读取群成员消息，所以不能实现群内 `@机器人` 提问。

### URL 验证失败

检查：

- 外网 HTTPS 域名是否能访问到 `codebot`。
- 回调 URL 是否精确到 `/api/v1/code-bot/wecom/callback`。
- `Token` 是否完全一致。
- `EncodingAESKey` 是否是 43 位。
- 服务时间和企业微信请求时间是否相差过大。

### 收到回调但没有群消息

检查：

- 是否配置 `codebot.wecom.webhook-url`。
- 群机器人是否在当前群。
- webhook 是否被企业微信安全策略限制。
- 服务日志是否有 `WeCom robot send failed`。

### 回答只返回代码片段或文件列表

说明大模型没有配置或不可用。配置 `OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_MODEL` 后重启服务。

### 切分支后回答还是旧代码

切换目标仓库分支或拉取代码后，调用重新索引。远程仓库模式下，服务会先更新本地缓存仓库：

```powershell
curl -X POST http://127.0.0.1:18080/api/v1/code-bot/admin/reindex
```

Linux / macOS:

```bash
curl -X POST http://127.0.0.1:18080/api/v1/code-bot/admin/reindex
```

如果配置了 `codebot.admin-token`，需要带上请求头：

```powershell
curl -X POST http://127.0.0.1:18080/api/v1/code-bot/admin/reindex `
  -H "X-CodeBot-Admin-Token: change-me"
```

Linux / macOS:

```bash
curl -X POST http://127.0.0.1:18080/api/v1/code-bot/admin/reindex \
  -H "X-CodeBot-Admin-Token: change-me"
```

然后检查：

```powershell
curl http://127.0.0.1:18080/api/v1/code-bot/health
```

Linux / macOS:

```bash
curl http://127.0.0.1:18080/api/v1/code-bot/health
```
