# Netty 文件传输中台

<p align="center">
  <img src="https://img.shields.io/badge/Java-21+-blue.svg" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-green.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Netty-4.x-orange.svg" alt="Netty">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

> 基于 Spring Boot 和 Netty 的高性能文件传输中台，支持多客户端文件分发、优先级队列管理、可靠传输确认（ACK）和实时监控。

## 📖 项目概述

Netty 文件传输中台是一个企业级的文件分发解决方案，采用 Netty 异步事件驱动框架实现高效的文件传输，支持：

- **多客户端分发**：一对多的文件分发模式
- **可靠传输**：基于 ACK 确认机制的可靠传输
- **优先级队列**：支持高/中/低三级优先级的任务调度
- **超时重试**：基于时间轮的超时检测和自动重试
- **实时监控**：完整的队列状态和传输统计监控
- **断点续传**：文件级别的传输记录和去重机制

## ✨ 核心特性

| 特性 | 描述 |
|------|------|
| 🔄 **文件分发** | 服务端主动向多个客户端分发文件 |
| ⏰ **定时任务** | 基于 Cron 表达式的定时文件发送 |
| ✅ **ACK 确认** | 文件传输完成后的确认机制 |
| 🔁 **超时重试** | 基于时间轮的超时检测和自动重试 |
| 📊 **优先级队列** | 高/中/低三级优先级队列管理 |
| 📈 **实时监控** | 队列状态、传输统计实时监控 |
| 🔒 **流量控制** | TCP 缓冲区、写入水位线配置 |
| 🔗 **重连机制** | 客户端自动重连 |
| 🛡️ **权限验证** | 客户端连接验证机制 |

## 🛠️ 技术栈

| 组件 | 技术 |
|------|------|
| **核心框架** | Spring Boot 3.x |
| **网络通信** | Netty 4.x |
| **消息协议** | 自定义二进制协议 + 数字签名 |
| **构建工具** | Maven |
| **Java 版本** | Java 21+ |
| **并发处理** | JUC (ConcurrentHashMap, PriorityBlockingQueue) |
| **定时任务** | ScheduledExecutorService + 时间轮 |

## 📋 环境要求

### 运行环境

- **JDK**: 21 或更高版本
- **Maven**: 3.6 或更高版本
- **操作系统**: Windows / Linux / macOS

### 硬件推荐

- **CPU**: 2 核或以上
- **内存**: 512MB 或以上
- **磁盘**: 根据传输文件大小决定

## 🚀 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd springboot-to-launch4j
```

### 2. 编译项目

```bash
mvn clean package -DskipTests
```

### 3. 配置应用

#### 服务端配置 (`file-transfer-server/src/main/resources/application.yml`)

```yaml
server:
  port: 8081  # HTTP 管理端口

file-transfer:
  server:
    port: 8080                     # Netty 服务端口
    file-source-path: ./server-files  # 待分发文件目录
    cron-expression: "0 0/5 * * * ?"  # 定时任务 Cron 表达式
    retry-count: 3                 # 重试次数
    retry-interval: 5000           # 重试间隔（毫秒）
    chunk-size: 1048576            # 分片大小（1MB）
    validate-flag: FILE_TRANSFER_TOKEN  # 验证令牌
    
    # 队列管理配置
    max-queue-size: 1000
    overflow-strategy: REJECT      # 队列满时的策略
    queue-timeout-ms: 300000       # 队列任务超时（5分钟）
    
    # 流量控制配置
    tcp-nodelay: true
    so-sndbuf: 10485760
    so-rcvbuf: 10485760
```

#### 客户端配置 (`file-transfer-client/src/main/resources/application.yml`)

```yaml
file-transfer:
  client:
    server-host: localhost         # 服务端地址
    server-port: 8080              # 服务端端口
    file-save-path: ./client-files # 文件保存目录
    reconnect-interval: 5000       # 重连间隔（毫秒）
    validate-flag: FILE_TRANSFER_TOKEN  # 验证令牌
```

### 4. 启动服务

**启动服务端：**

```bash
cd file-transfer-server
java -jar target/file-transfer-server-1.0.0.jar
```

**启动客户端：**

```bash
cd file-transfer-client
java -jar target/file-transfer-client-1.0.0.jar
```

## 📚 使用指南

### 基础操作

#### 触发文件分发

```bash
# 触发单个文件分发
curl -X POST "http://localhost:8081/api/transfer/file?fileName=example.txt"

# 触发多个文件分发
curl -X POST "http://localhost:8081/api/transfer/files" \
  -H "Content-Type: application/json" \
  -d '["file1.txt", "file2.txt"]'

# 触发所有文件分发
curl -X POST "http://localhost:8081/api/transfer/all"
```

#### 优先级队列分发

```bash
# 高优先级分发
curl -X POST "http://localhost:8081/api/transfer/file-with-priority?fileName=urgent.txt&priority=HIGH"

# 中优先级分发
curl -X POST "http://localhost:8081/api/transfer/file-with-priority?fileName=normal.txt&priority=MEDIUM"

# 低优先级分发
curl -X POST "http://localhost:8081/api/transfer/file-with-priority?fileName=background.txt&priority=LOW"
```

### 队列管理

#### 查询队列状态

```bash
# 获取完整队列状态
curl "http://localhost:8081/api/queue/status"
```

**响应示例：**

```json
{
  "totalQueueLength": 15,
  "peakQueueLength": 25,
  "loadPercentage": "15.00%",
  "queueLengthByPriority": {
    "HIGH": 3,
    "MEDIUM": 7,
    "LOW": 5
  },
  "taskCountByStatus": {
    "WAITING": 12,
    "EXECUTING": 3,
    "COMPLETED": 156,
    "FAILED": 2,
    "CANCELLED": 1,
    "TIMEOUT": 0
  },
  "averageWaitTimeMs": "1234.56",
  "averageExecutionTimeMs": "567.89"
}
```

#### 查询队列任务

```bash
# 获取等待队列中的任务
curl "http://localhost:8081/api/queue/tasks?limit=50"
```

#### 取消任务

```bash
# 根据任务ID取消
curl -X DELETE "http://localhost:8081/api/queue/task/{taskId}"
```

#### 调整任务优先级

```bash
# 将任务从低优先级调整为高优先级
curl -X PUT "http://localhost:8081/api/queue/task/{taskId}/priority?priority=HIGH"
```

#### 清空队列

```bash
# 清空所有队列
curl -X DELETE "http://localhost:8081/api/queue/clear"

# 仅清空低优先级队列
curl -X DELETE "http://localhost:8081/api/queue/clear?priority=LOW"
```

#### 动态调整队列容量

```bash
# 将队列最大容量调整为 2000
curl -X PUT "http://localhost:8081/api/queue/capacity?maxSize=2000"
```

#### 设置溢出策略

```bash
# 设置溢出策略为降级处理
curl -X PUT "http://localhost:8081/api/queue/overflow-strategy?strategy=DEGRADE"
```

### 传输记录管理

#### 查询传输记录

```bash
# 获取所有传输记录
curl "http://localhost:8081/api/transfer/records"

# 获取特定文件的记录
curl "http://localhost:8081/api/transfer/record?fileName=example.txt"

# 获取传输统计
curl "http://localhost:8081/api/transfer/statistics"
```

#### 重置记录

```bash
# 重置所有传输记录
curl -X POST "http://localhost:8081/api/transfer/reset/all"
```

### ACK 队列监控

```bash
# 查询 ACK 队列状态
curl "http://localhost:8081/api/transfer/ack/status"
```

### 通道管理

```bash
# 查询所有活动通道
curl "http://localhost:8081/api/channel/list"

# 查询通道状态
curl "http://localhost:8081/api/channel/status"
```

## 🔧 API 接口汇总

### 文件传输接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/transfer/file` | POST | 触发单个文件分发 |
| `/api/transfer/files` | POST | 触发多个文件分发 |
| `/api/transfer/all` | POST | 触发所有文件分发 |
| `/api/transfer/file-with-priority` | POST | 优先级队列分发 |
| `/api/transfer/records` | GET | 获取传输记录列表 |
| `/api/transfer/record` | GET | 获取特定文件记录 |
| `/api/transfer/statistics` | GET | 获取传输统计信息 |
| `/api/transfer/reset/all` | POST | 重置所有记录 |

### 队列管理接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/queue/status` | GET | 获取队列状态 |
| `/api/queue/tasks` | GET | 获取队列任务列表 |
| `/api/queue/task/{taskId}` | GET | 获取任务详情 |
| `/api/queue/task/{taskId}` | DELETE | 取消任务 |
| `/api/queue/task/{taskId}/priority` | PUT | 调整任务优先级 |
| `/api/queue/clear` | DELETE | 清空队列 |
| `/api/queue/capacity` | PUT | 调整队列容量 |
| `/api/queue/overflow-strategy` | PUT | 设置溢出策略 |
| `/api/queue/reset-temporary-expand` | POST | 重置临时扩容 |
| `/api/queue/reset-statistics` | POST | 重置统计信息 |

### 通道管理接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/channel/list` | GET | 获取活动通道列表 |
| `/api/channel/status` | GET | 获取通道状态统计 |

### ACK 队列接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/transfer/ack/status` | GET | 获取 ACK 队列状态 |

## 📁 项目结构

```
springboot-to-launch4j/
├── file-transfer-common/          # 公共模块
│   └── src/main/java/com/example/common/
│       ├── codec/                # 消息编解码器
│       │   ├── MessageDecoder.java
│       │   ├── MessageEncoder.java
│       │   └── MessageSignature.java
│       ├── config/               # 常量配置
│       │   └── TransferConstants.java
│       ├── exception/            # 异常类
│       │   ├── SignatureVerificationException.java
│       │   └── TransferException.java
│       └── message/              # 消息协议
│           ├── ChunkParams.java
│           ├── FileMetadata.java
│           └── TransferMessage.java
│
├── file-transfer-server/         # 服务端模块
│   └── src/main/java/com/example/server/
│       ├── config/               # 配置类
│       │   ├── ServerConfig.java
│       │   └── QueueConfigInitializer.java
│       ├── controller/           # REST 控制器
│       │   ├── TransferController.java
│       │   ├── QueueController.java
│       │   └── ChannelController.java
│       ├── handler/              # Netty 处理器
│       │   ├── ServerHandler.java
│       │   └── ServerMessageHandler.java
│       ├── model/                # 数据模型
│       │   ├── AckRecord.java
│       │   ├── FileTransferRecord.java
│       │   ├── FileTransferStatus.java
│       │   ├── OverflowStrategy.java
│       │   ├── QueueStatistics.java
│       │   └── TransferTask.java
│       ├── netty/               # Netty 服务器
│       │   └── NettyServer.java
│       ├── service/              # 业务服务
│       │   ├── AckQueueService.java
│       │   ├── ChannelManager.java
│       │   ├── FileTransferRecordService.java
│       │   ├── FileTransferService.java
│       │   ├── QueueTaskProcessor.java
│       │   ├── TimeWheel.java
│       │   └── TransferQueueService.java
│       └── task/                # 定时任务
│           └── ScheduledFileTask.java
│
├── file-transfer-client/         # 客户端模块
│   └── src/main/java/com/example/client/
│       ├── config/               # 配置类
│       │   └── ClientConfig.java
│       ├── handler/             # Netty 处理器
│       │   ├── ClientHandler.java
│       │   └── ClientMessageHandler.java
│       ├── netty/               # Netty 客户端
│       │   └── NettyClient.java
│       └── service/             # 业务服务
│           └── FileReceiveService.java
│
└── pom.xml                      # 父项目配置
```

## ⚙️ 核心模块说明

### ACK 确认机制

ACK 机制确保文件传输的可靠性：

1. **触发条件**：客户端收到 `END` 消息后发送确认
2. **超时处理**：使用时间轮检测 ACK 超时
3. **重试策略**：超时后自动重试，支持可配置的最大重试次数
4. **状态跟踪**：WAITING → ACKED / TIMEOUT → RETRYING → FAILED

### 优先级队列

采用 `PriorityBlockingQueue` 实现：

- **HIGH (3)**：紧急/重要文件
- **MEDIUM (2)**：普通文件
- **LOW (1)**：后台/批量文件

相同优先级按 FIFO 顺序执行。

### 时间轮定时器

基于层级时间轮实现高效的超时检测：

- 多层时间轮支持不同精度的定时任务
- 避免大量定时器带来的性能问题
- 用于 ACK 超时检测和任务超时处理

### 溢出处理策略

| 策略 | 行为 |
|------|------|
| `REJECT` | 直接拒绝新任务 |
| `DEGRADE` | 将任务降级为低优先级后入队 |
| `EVICT_OLDEST` | 移除最旧的低优先级任务 |
| `TEMPORARY_EXPAND` | 临时扩容队列容量 |

## 🔒 安全特性

### 消息签名

- 使用 HMAC-SHA256 算法对消息进行签名
- 防止消息篡改和伪造
- 可配置签名密钥

### 权限验证

- 客户端连接时需要验证 `validateFlag`
- 服务端和客户端配置需保持一致

## 📊 监控指标

### 队列统计

- 队列长度（总计/按优先级）
- 任务状态分布
- 平均等待时间
- 平均执行时间
- 队列峰值

### 传输统计

- 累计传输文件数
- 累计传输字节数
- 传输成功率
- 传输失败记录

### ACK 统计

- 等待确认的任务数
- 已确认的任务数
- 超时/失败的任务数

## 🧪 测试验证

### 基本测试流程

1. **启动服务端**
   ```bash
   java -jar file-transfer-server-1.0.0.jar
   ```
   确认日志：`Netty server started on port: 8080`

2. **启动客户端**
   ```bash
   java -jar file-transfer-client-1.0.0.jar
   ```
   确认日志：`Authentication successful`

3. **放置测试文件**
   ```bash
   cp test.txt file-transfer-server/server-files/
   ```

4. **触发分发**
   ```bash
   curl -X POST "http://localhost:8081/api/transfer/file?fileName=test.txt"
   ```

5. **验证结果**
   检查 `file-transfer-client/client-files/` 目录

### 队列测试

1. 连续提交多个文件分发任务
2. 查询队列状态观察优先级排序
3. 测试优先级调整功能
4. 测试任务取消功能

### 失败恢复测试

1. 在传输过程中断开客户端
2. 观察服务端重试日志
3. 重新连接客户端
4. 验证文件完整性

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### 开发环境

1. 安装 JDK 21+
2. 安装 Maven 3.6+
3. 克隆项目
4. 运行 `mvn clean compile`

### 代码规范

- 遵循 Google Java Style Guide
- 所有新功能需要添加单元测试
- 提交前运行 `mvn clean verify`

### 分支管理

- `main`: 主分支，稳定版本
- `develop`: 开发分支
- `feature/*`: 功能分支
- `fix/*`: 修复分支

## 📄 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 📧 联系方式

- **项目主页**: https://github.com/yourusername/springboot-to-launch4j
- **问题反馈**: https://github.com/yourusername/springboot-to-launch4j/issues
- **邮箱**: your.email@example.com

## 🙏 致谢

- [Spring Boot](https://spring.io/projects/spring-boot) - 企业级 Java 框架
- [Netty](https://netty.io/) - 异步事件驱动的网络应用框架
- [Maven](https://maven.apache.org/) - 项目管理和构建工具

---

<p align="center">
  如果这个项目对你有帮助，请给一个 ⭐️！
</p>
