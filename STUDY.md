# 系统运行逻辑说明

以下描述假设程序通过 `java -jar target/timer-trigger-1.0.0.jar --config config.yaml` 启动。

1. 程序启动后会读取默认配置，然后加载 YAML 配置（若提供），最后应用命令行参数覆盖。
2. 程序校验参数合法性：`interval-min > 0`、`mode` 在允许范围、`run-for` 可解析；mode 1-3 依赖 `epc-list`，mode 1 可选 `single-epc` 或 `single-epc-index`，mode 4 需要 `scheduleSteps`。
3. 程序创建单线程 `ScheduledExecutorService`，确保任意时刻只会有一个 HTTP 请求在执行。
4. 立即触发第一次请求（延迟 0）。根据 `mode` 计算本次使用的 `epcList` 与 `devicePort`：
   - Mode 1：使用 `single-epc` 或 `single-epc-index` 指定的单个 EPC。
   - Mode 2：按 EPC 列表顺序轮转（每次只发一个）。
   - Mode 3：每次发送完整 EPC 列表。
   - Mode 4：按 `scheduleSteps` 逐条执行，每步指定 `devicePort` 与 `epcList`。
5. 生成请求 URL：`{baseUrl}/tempsense/start?...`，并发起一次 HTTP GET。
6. 每次请求结束后输出一条日志（stdout + 按天日志文件），内容包含时间戳、mode、interval、devicePort、epcList、完整 URL、HTTP 状态码、耗时等。
7. 若请求异常（超时、连接失败等），记录错误日志并进入下一轮，不终止程序。
8. 调度采用“单次执行后自行计算下一次延迟”的方式：
   - mode 1/3 使用 `interval-min` 分钟作为固定间隔；
   - mode 2/4 使用 `epc-interval-sec` 与 `interval-min` 组成两级间隔；
   - 保证串行执行，不会并发堆积。
9. 程序持续运行直到 `run-for` 到期：
   - 到期后先停止调度器接收新任务；
   - 等待当前请求结束，最多等待 `shutdown-wait`；
   - 正常退出返回码 0。

# Java 17 -> Java 8 兼容修改点（学习记录）

1. `Path.of(...)` 与 `List.of(...)` 是 Java 11/9 之后才提供的工厂方法，Java 8 里不存在：
   - `Path.of(...)` 改为 `Paths.get(...)`；
   - `List.of(...)` 改为 `Arrays.asList(...)`。
   这样可在 Java 8 编译与运行。
2. `Files.writeString(...)` 是 Java 11 才加入的 API，Java 8 需要用 `Files.write(...)` + `String.getBytes(StandardCharsets.UTF_8)`。
3. `Duration.toSeconds()` 在 Java 8 的可见性与 Java 17 不一致，统一使用 `Duration.getSeconds()` 读取秒数，避免编译错误。

# 模式间隔的细化（学习记录）

现有 `interval-min` 只能控制整体触发频率，对“多 EPC 逐个发送”的场景不够细粒度，因此引入两级间隔配置：

1. **组内间隔（`epc-interval-sec`）**：同一组内每个 EPC 之间的等待时间（秒）。
2. **组间间隔（`interval-min`）**：一组 EPC 发送完毕后，到下一组开始前的等待时间（分钟）。

对应模式行为：

- **Mode 1**：单个 EPC，仍使用 `interval-min` 作为固定触发周期。
- **Mode 2**：按 EPC 列表轮询，列表内相邻 EPC 使用 `epc-interval-sec`；列表结束后等待 `interval-min` 再开启下一轮。
- **Mode 3**：一次请求携带全部 EPC，仍使用 `interval-min`。
- **Mode 4**：`scheduleSteps` 中每个 step 视作一组，step 内 EPC 逐个发送，组内间隔用 `epc-interval-sec`，step 与下一个 step 之间等待 `interval-min`。

# 变更记录

- 2025-09-26：修正可执行 JAR 命名与文档一致（去掉 `-shaded` 后缀），新增 EPC 列表可配置、`devicePortAlt` 与 mode 6 轮转规则，日志中加入 `devicePort`，并扩展 README 中的参数与模式说明。
- 2025-09-26：简化模式为 1..4：mode 1 使用 `single-epc`/`single-epc-index`，mode 2 轮转，mode 3 全量，mode 4 使用 `scheduleSteps` 自定义顺序。
- 2025-09-26：新增 JUnit 测试覆盖 duration 解析与模式选择逻辑，并补充测试设计说明。
- 2025-09-26：新增 stop/restart 批处理脚本，便于在 Windows 环境停止或重启服务。
- 2025-09-26：简化端口为单一 `devicePort`，移除 mode 6，保留 mode 7 用 `scheduleSteps` 进行自定义端口与 EPC 顺序。
