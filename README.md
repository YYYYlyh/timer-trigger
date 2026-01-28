# timer-trigger

定时触发本地 HTTP GET 的 Java 命令行程序（可执行 JAR）。

## 构建

```bash
mvn -q -DskipTests package
```

生成的可执行 JAR：

```
target/timer-trigger-1.0.0.jar
```

## 运行

### 方式一：命令行参数

```bash
java -jar target/timer-trigger-1.0.0.jar \
  --interval-min 10 \
  --run-for 2h \
  --mode 5 \
  --base-url http://localhost:9055 \
  --epc-list E28011B0A502006D6D1E90F7,E28011B0A502006D6D1EF607,E28011B0A502006D6D1EF637 \
  --device-id 1 \
  --device-port 0 \
  --device-port-alt 1 \
  --duration-sec 60 \
  --qvalue 0 \
  --rfmode 113
```

### 方式二：YAML 配置文件

编辑 `config.yaml` 后运行：

```bash
java -jar target/timer-trigger-1.0.0.jar --config config.yaml
```

Windows 可直接双击或执行 `start.bat`。

## 参数说明

- `--interval-min`：触发间隔（分钟）。
- `--run-for`：总运行时长，例如 `30m`、`2h`、`1d`、`30d`。
- `--mode`：1..6（单签或轮转/合并触发，含双端口轮转）。
- `--base-url`：默认 `http://localhost:9055`。
- `--epc-list`：英文逗号分隔的 EPC 列表（默认 3 个 EPC）。
- `--device-id`：默认 `1`。
- `--device-port`：默认 `0`。
- `--device-port-alt`：mode 6 使用的交替端口（默认 `1`）。
- `--duration-sec`：默认 `60`。
- `--qvalue`：默认 `0`。
- `--rfmode`：默认 `113`。
- `--connect-timeout-sec`：连接超时（默认 5s）。
- `--request-timeout-sec`：请求超时（默认 30s）。
- `--shutdown-wait`：停止等待时长（默认 30s）。
- `--log-dir`：日志目录（默认 `logs`，按天分文件）。

## 日志

每次触发都会输出到 stdout，并写入 `logs/YYYY-MM-DD.txt`。

## 请求 URL 规则

```
{baseUrl}/tempsense/start?deviceId=...&devicePort=...&epcList=...&duration=...&qValue=...&rfMode=...
```

EPC 固定值：

- `E28011B0A502006D6D1E90F7`
- `E28011B0A502006D6D1EF607`
- `E28011B0A502006D6D1EF637`

## 模式说明

- Mode 1：每 interval 分钟触发一次，使用 EPC 列表第 1 个。
- Mode 2：每 interval 分钟触发一次，使用 EPC 列表第 2 个。
- Mode 3：每 interval 分钟触发一次，使用 EPC 列表第 3 个。
- Mode 4：每 interval 分钟触发一次，按 EPC 列表顺序轮转（每次只发一个 EPC）。
- Mode 5：每 interval 分钟触发一次，单次请求携带全部 EPC。
- Mode 6：每 interval 分钟触发一次，按端口与 EPC 分组轮转：
  - 第 1 次：`devicePort` + EPC 列表前两个
  - 第 2 次：`devicePortAlt` + EPC 列表第 3 个
  - 后续按上述顺序循环
