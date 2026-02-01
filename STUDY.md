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
8. 调度采用 `scheduleWithFixedDelay`：上一轮请求结束后再等待 `interval-min` 分钟执行下一次，保证串行执行，不会并发堆积。
9. 程序持续运行直到 `run-for` 到期：
   - 到期后先停止调度器接收新任务；
   - 等待当前请求结束，最多等待 `shutdown-wait`；
   - 正常退出返回码 0。

# 变更记录

- 2025-09-26：修正可执行 JAR 命名与文档一致（去掉 `-shaded` 后缀），新增 EPC 列表可配置、`devicePortAlt` 与 mode 6 轮转规则，日志中加入 `devicePort`，并扩展 README 中的参数与模式说明。
- 2025-09-26：简化模式为 1..4：mode 1 使用 `single-epc`/`single-epc-index`，mode 2 轮转，mode 3 全量，mode 4 使用 `scheduleSteps` 自定义顺序。
