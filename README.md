# PayHubProject

支付收单与对账平台 —— 一个用来「真的搞懂支付」的项目。

> 不是 demo 堆功能，而是按支付系统的真实关注点推进：
> **下单 → 可信回调 → 补偿/关单 → 退款 → 账务 → 对账**。

## 当前进度

| 周 | 主题 | 状态 |
|---|---|---|
| W1 | 统一下单骨架：支付单、渠道适配、幂等 | ✅ |
| **W2** | **可信回调：验签、金额比对、状态机 CAS、outbox、补偿、关单** | ✅ |
| W3 | 退款：退款单、部分退款、超退防护、退款回调 | 待做 |
| W4 | 账务：复式记账、凭证、日终试算平衡 | 待做 |
| W5 | 对账：账单解析、双向比对、差错池、清分结算 | 待做 |
| W6 | 性能：回调洪峰、对账批处理、压测数据 | 待做 |

---

## 快速开始

### 默认：本机 MySQL

```yaml
spring.datasource.url: jdbc:mysql://127.0.0.1:3306/payhub?createDatabaseIfNotExist=true
username: root / password: ""
```

`schema.sql` 在每次启动时执行（`CREATE TABLE IF NOT EXISTS`），会自动补建 W2 新增的两张表。

### 备用：没有 MySQL 时用 H2

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2
# 或
java -jar target/PayHubProject-0.0.1-SNAPSHOT.jar --spring.profiles.active=h2
```

H2 数据文件在 `./data/payhub.mv.db`，控制台 <http://localhost:8080/h2-console>。

### 跑测试

```bash
./mvnw test
```

12 个用例：3 个签名（W1）+ 8 个回调场景 + 1 个待补。全部用 H2 内存库，不依赖 MySQL 和支付宝。

---

## 一笔支付的完整链路（W2 后）

```
① 统一下单
POST /openapi/pay/unifiedorder
   → PayOpenApiController（验签开关 + DTO→Command）
   → PaymentOrderService.create（三层幂等 → 落库 → 调渠道 → 置 PAYING）
   → 返回 payUrl（MOCK 模式指向本地收银台）

② 用户支付（浏览器点「确认支付」）

③ 渠道回调（W2 核心）
POST /notify/mock  或  /notify/alipay
   → NotifyService.handle
        ① 落证据 payment_callback_log（notify_key 唯一）
        ② 验签（渠道公钥 RSA2）
        ③ 找单 app_id → merchant_no → out_trade_no
        ④ 金额比对（元 → 分）
        ⑤ advanceToSuccess（状态机 + CAS + outbox，同一事务）
        ⑥ 回写结果
        ⑦ 应答 success / failure

④ 兜底
   PaymentCompensationJob（掉单：主动查询补单，复用同一推进方法）
   PaymentTimeoutCloseJob（超时：先本地 CAS 关单，再关渠道）
   LocalMessageRelayJob（outbox：投递事件，W3 换成 RocketMQ）

⑤ 查询
GET /openapi/pay/query/{paymentNo}
```

---

## 接口清单

### 对外（商户）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/openapi/pay/unifiedorder` | 统一下单（`sign.verify-enabled` 打开后必须带 sign） |
| GET | `/openapi/pay/query/{paymentNo}` | 查询支付单 |

### 回调（渠道 → 平台）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/notify/alipay` | 支付宝异步通知，返回纯字符串 `success` / `failure` |
| POST | `/notify/mock` | 模拟渠道回调，**同样要验签**，裸调会被拒 |

### 折腾台（仅本地开发）

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/mock/cashier?paymentNo=` | 模拟收银台 |
| POST | `/mock/cashier/pay` | 模拟用户付款 → 发签名回调 |
| POST | `/mock/notify/send?paymentNo=&tamper=none\|sign\|amount` | 手动发回调，可模拟篡改 |
| POST | `/mock/order/drop/{paymentNo}` | 模拟掉单（渠道已付，回调丢失） |
| POST | `/mock/order/compensate/{paymentNo}` | 手动触发补偿 |
| POST | `/mock/order/close/{paymentNo}` | 手动关单 |
| POST | `/mock/order/expire/{paymentNo}` | 把单改成已过期，等关单任务 |

---

## 目录结构

```
com.payhub
├── openapi/                 对外接口层
│   ├── controller/          下单、回调、折腾台
│   ├── dto/                 请求/响应（对外契约）
│   └── support/             商户验签、Mock 折腾台服务
├── core/                    支付核心域（不依赖 openapi）
│   ├── command/             领域命令对象
│   ├── entity/              支付单、回调日志、本地消息
│   ├── enums/               渠道、状态机、通知结果、推进结果
│   ├── mapper/              MyBatis-Plus Mapper + CAS SQL
│   ├── channel/             渠道适配（策略 + 路由）
│   ├── notify/              回调处理、验签器、Mock 签名器
│   └── service/             支付单服务（唯一推进入口）、outbox 写侧
├── merchant/                商户应用（信任链起点）
├── job/                     定时任务：补偿、关单、outbox 投递
├── common/                  单号、签名、统一返回、异常边界
├── config/                  MyBatis-Plus 乐观锁、自动填充
└── tools/                   SignTool（商户密钥生成 + 签名示例）
```

依赖方向只能向内：`openapi → core → merchant → common`。

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/W1-骨架导读.md](docs/W1-骨架导读.md) | W1 逐层导读、5 个自测题、3 个动手练习 |
| [docs/W2-回调体系导读.md](docs/W2-回调体系导读.md) | 七步处理链、三个新概念、8 个实验、5 个坑 |
| [http/w1.http](http/w1.http) / [http/w2.http](http/w2.http) | IDEA HTTP Client 一键调用 |

---

## 关键设计决策（面试可直接讲）

| 决策 | 为什么 |
|---|---|
| 回调先落报文再验签 | 验签失败也是事件，必须留痕（举证/排查/对账） |
| `notify_key` 唯一键 | 重推 8 次只处理一次；失败可重试 |
| 状态推进收敛到一个方法 | 回调/查询/补偿复用，避免逻辑漂移 |
| CAS 带 `ver` + `status` 双条件 | 并发只有一个成功；0 行是正常分支不是异常 |
| outbox 而非直接发 MQ | 改库与发消息同事务，天然最终一致 |
| 关单先本地后渠道 | 反过来会「钱收了单关了」 |
| 金额全程用分 | 浮点在钱上是致命的 |
| 渠道侧 key 用 outTradeNo | 渠道不认识我们的 paymentNo |

---

## 已知待办（W3 起）

- 退款单模型与 `out_request_no` 幂等、累计不超退
- outbox 投递接 RocketMQ
- 账务：复式记账、凭证、试算平衡
- 对账：账单下载解析、差错池、清分结算
- 多实例下定时任务分片（XXL-Job）

---

## 技术栈

Java 21 · Spring Boot 3.5.16 · MyBatis-Plus 3.5.18 · MySQL 8 / H2 · Lombok
