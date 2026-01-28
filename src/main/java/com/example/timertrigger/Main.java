package com.example.timertrigger;

import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Command(name = "timer-trigger", mixinStandardHelpOptions = true, description = "Timer-triggered HTTP GET runner")
public class Main implements Runnable {
    @Option(names = "--config", description = "Path to YAML config file")
    private String configPath;

    @Option(names = "--interval-min", description = "Trigger interval in minutes")
    private Integer intervalMin;

    @Option(names = "--run-for", description = "Total runtime duration, e.g. 30m, 2h, 1d")
    private String runFor;

    @Option(names = "--mode", description = "Mode 1-6")
    private Integer mode;

    @Option(names = "--base-url", description = "Base URL")
    private String baseUrl;

    @Option(names = "--epc-list", split = ",", description = "Comma-separated EPC list")
    private List<String> epcList;

    @Option(names = "--device-id", description = "Device ID")
    private Integer deviceId;

    @Option(names = "--device-port", description = "Device port")
    private Integer devicePort;

    @Option(names = "--device-port-alt", description = "Alternate device port for mode 6")
    private Integer devicePortAlt;

    @Option(names = "--duration-sec", description = "Duration seconds per request")
    private Integer durationSec;

    @Option(names = "--qvalue", description = "Q value")
    private Integer qvalue;

    @Option(names = "--rfmode", description = "RF mode")
    private Integer rfmode;

    @Option(names = "--connect-timeout-sec", description = "HTTP connect timeout seconds")
    private Integer connectTimeoutSec;

    @Option(names = "--request-timeout-sec", description = "HTTP request timeout seconds")
    private Integer requestTimeoutSec;

    @Option(names = "--shutdown-wait", description = "Shutdown wait duration, e.g. 30s")
    private String shutdownWait;

    @Option(names = "--log-dir", description = "Log output directory")
    private String logDir;

    private Config config;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            config = loadConfig();
            validate(config);
            runScheduler(config);
        } catch (ParameterException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            throw new ParameterException(new CommandLine(this), e.getMessage(), e);
        }
    }

    private Config loadConfig() {
        Config base = new Config();
        Config yamlConfig = configPath != null ? loadYaml(configPath) : new Config();
        Config merged = merge(base, yamlConfig);
        Config cliConfig = new Config();
        cliConfig.intervalMin = intervalMin;
        cliConfig.runFor = runFor;
        cliConfig.mode = mode;
        cliConfig.baseUrl = baseUrl;
        cliConfig.epcList = epcList;
        cliConfig.deviceId = deviceId;
        cliConfig.devicePort = devicePort;
        cliConfig.devicePortAlt = devicePortAlt;
        cliConfig.durationSec = durationSec;
        cliConfig.qvalue = qvalue;
        cliConfig.rfmode = rfmode;
        cliConfig.connectTimeoutSec = connectTimeoutSec;
        cliConfig.requestTimeoutSec = requestTimeoutSec;
        cliConfig.shutdownWait = shutdownWait;
        cliConfig.logDir = logDir;
        return merge(merged, cliConfig);
    }

    private Config loadYaml(String path) {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))) {
            Config loaded = yaml.loadAs(inputStream, Config.class);
            return loaded == null ? new Config() : loaded;
        } catch (IOException e) {
            throw new ParameterException(new CommandLine(this), "Failed to read config: " + e.getMessage(), e);
        }
    }

    private Config merge(Config base, Config override) {
        Config result = new Config();
        result.intervalMin = pick(override.intervalMin, base.intervalMin);
        result.runFor = pick(override.runFor, base.runFor);
        result.mode = pick(override.mode, base.mode);
        result.baseUrl = pick(override.baseUrl, base.baseUrl);
        result.epcList = pick(override.epcList, base.epcList);
        result.deviceId = pick(override.deviceId, base.deviceId);
        result.devicePort = pick(override.devicePort, base.devicePort);
        result.devicePortAlt = pick(override.devicePortAlt, base.devicePortAlt);
        result.durationSec = pick(override.durationSec, base.durationSec);
        result.qvalue = pick(override.qvalue, base.qvalue);
        result.rfmode = pick(override.rfmode, base.rfmode);
        result.connectTimeoutSec = pick(override.connectTimeoutSec, base.connectTimeoutSec);
        result.requestTimeoutSec = pick(override.requestTimeoutSec, base.requestTimeoutSec);
        result.shutdownWait = pick(override.shutdownWait, base.shutdownWait);
        result.logDir = pick(override.logDir, base.logDir);
        return result;
    }

    private <T> T pick(T override, T base) {
        return override != null ? override : base;
    }

    private void validate(Config config) {
        if (config.intervalMin == null || config.intervalMin <= 0) {
            throw new ParameterException(new CommandLine(this), "interval-min must be > 0");
        }
        if (config.mode == null || config.mode < 1 || config.mode > 6) {
            throw new ParameterException(new CommandLine(this), "mode must be between 1 and 6");
        }
        if (config.runFor == null) {
            throw new ParameterException(new CommandLine(this), "run-for is required");
        }
        if (config.baseUrl == null || config.baseUrl.isBlank()) {
            throw new ParameterException(new CommandLine(this), "base-url is required");
        }
        if (config.epcList == null || config.epcList.isEmpty()) {
            throw new ParameterException(new CommandLine(this), "epc-list must not be empty");
        }
        int epcCount = config.epcList.size();
        if (config.mode == 1 && epcCount < 1) {
            throw new ParameterException(new CommandLine(this), "mode 1 requires at least 1 EPC value");
        }
        if (config.mode == 2 && epcCount < 2) {
            throw new ParameterException(new CommandLine(this), "mode 2 requires at least 2 EPC values");
        }
        if (config.mode == 3 && epcCount < 3) {
            throw new ParameterException(new CommandLine(this), "mode 3 requires at least 3 EPC values");
        }
        if (config.mode == 6 && epcCount < 3) {
            throw new ParameterException(new CommandLine(this), "mode 6 requires at least 3 EPC values");
        }
    }

    private void runScheduler(Config config) {
        Duration runForDuration = DurationParser.parse(config.runFor);
        Duration shutdownWaitDuration = DurationParser.parse(config.shutdownWait);
        Duration connectTimeout = Duration.ofSeconds(config.connectTimeoutSec);
        Duration requestTimeout = Duration.ofSeconds(config.requestTimeoutSec);

        LogWriter logger = new LogWriter(config.logDir);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(runForDuration);

        scheduler.schedule(() -> {
            logger.info("Run-for reached, shutting down scheduler.");
            scheduler.shutdown();
        }, runForDuration.toMillis(), TimeUnit.MILLISECONDS);

        Runnable task = new Runnable() {
            private int tickIndex = 0;

            @Override
            public void run() {
                if (scheduler.isShutdown()) {
                    return;
                }
                int currentPort = resolveDevicePort(config, tickIndex);
                String epcList = resolveEpcList(config, tickIndex);
                tickIndex = tickIndex + 1;
                String url = buildUrl(config, currentPort, epcList);
                long start = System.nanoTime();
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(url))
                            .timeout(requestTimeout)
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    String body = response.body() == null ? "" : response.body();
                    String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    String logMessage = String.format("mode=%d interval=%dmin devicePort=%d epcList=%s url=%s status=%d elapsedMs=%d response=%s",
                            config.mode, config.intervalMin, currentPort, epcList, url, response.statusCode(), elapsedMs, snippet);
                    logger.info(logMessage);
                } catch (Exception e) {
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    String logMessage = String.format("mode=%d interval=%dmin devicePort=%d epcList=%s url=%s error=%s elapsedMs=%d",
                            config.mode, config.intervalMin, currentPort, epcList, url, e.getMessage(), elapsedMs);
                    logger.error(logMessage);
                }

                if (Instant.now().isAfter(endTime)) {
                    logger.info("Reached end time, shutting down scheduler.");
                    scheduler.shutdown();
                }
            }
        };

        scheduler.scheduleWithFixedDelay(task, 0, config.intervalMin, TimeUnit.MINUTES);

        try {
            long waitMillis = runForDuration.plus(shutdownWaitDuration).toMillis();
            boolean finished = scheduler.awaitTermination(waitMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                logger.error("Scheduler did not shut down in time, forcing shutdown.");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private String resolveEpcList(Config config, int tickIndex) {
        List<String> epcList = config.epcList;
        return switch (config.mode) {
            case 1 -> epcList.get(0);
            case 2 -> epcList.get(1);
            case 3 -> epcList.get(2);
            case 4 -> epcList.get(tickIndex % epcList.size());
            case 5 -> String.join(",", epcList);
            case 6 -> resolveMode6EpcList(epcList, tickIndex);
            default -> throw new IllegalArgumentException("Unsupported mode: " + config.mode);
        };
    }

    private String resolveMode6EpcList(List<String> epcList, int tickIndex) {
        if (tickIndex % 2 == 0) {
            return String.join(",", epcList.subList(0, 2));
        }
        return epcList.get(2);
    }

    private int resolveDevicePort(Config config, int tickIndex) {
        if (config.mode == 6) {
            return tickIndex % 2 == 0 ? config.devicePort : config.devicePortAlt;
        }
        return config.devicePort;
    }

    private String buildUrl(Config config, int devicePort, String epcList) {
        String base = config.baseUrl.endsWith("/") ? config.baseUrl.substring(0, config.baseUrl.length() - 1) : config.baseUrl;
        return String.format("%s/tempsense/start?deviceId=%d&devicePort=%d&epcList=%s&duration=%d&qValue=%d&rfMode=%d",
                base,
                config.deviceId,
                devicePort,
                epcList,
                config.durationSec,
                config.qvalue,
                config.rfmode);
    }
}
