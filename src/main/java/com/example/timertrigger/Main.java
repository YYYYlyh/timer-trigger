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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Command(name = "timer-trigger", mixinStandardHelpOptions = true, description = "Timer-triggered HTTP GET runner")
public class Main implements Runnable {
    @Option(names = "--config", description = "Path to YAML config file")
    private String configPath;

    @Option(names = "--interval-min", description = "Trigger interval in minutes")
    private Integer intervalMin;

    @Option(names = "--run-for", description = "Total runtime duration, e.g. 30m, 2h, 1d")
    private String runFor;

    @Option(names = "--mode", description = "Mode 1-4")
    private Integer mode;

    @Option(names = "--base-url", description = "Base URL")
    private String baseUrl;

    @Option(names = "--epc-list", split = ",", description = "Comma-separated EPC list")
    private List<String> epcList;

    @Option(names = "--single-epc", description = "Single EPC for mode 1")
    private String singleEpc;

    @Option(names = "--single-epc-index", description = "Single EPC index in epc-list for mode 1")
    private Integer singleEpcIndex;

    @Option(names = "--device-id", description = "Device ID")
    private Integer deviceId;

    @Option(names = "--device-port", description = "Device port")
    private Integer devicePort;

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
        cliConfig.singleEpc = singleEpc;
        cliConfig.singleEpcIndex = singleEpcIndex;
        cliConfig.deviceId = deviceId;
        cliConfig.devicePort = devicePort;
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
        result.singleEpc = pick(override.singleEpc, base.singleEpc);
        result.singleEpcIndex = pick(override.singleEpcIndex, base.singleEpcIndex);
        result.deviceId = pick(override.deviceId, base.deviceId);
        result.devicePort = pick(override.devicePort, base.devicePort);
        result.durationSec = pick(override.durationSec, base.durationSec);
        result.qvalue = pick(override.qvalue, base.qvalue);
        result.rfmode = pick(override.rfmode, base.rfmode);
        result.connectTimeoutSec = pick(override.connectTimeoutSec, base.connectTimeoutSec);
        result.requestTimeoutSec = pick(override.requestTimeoutSec, base.requestTimeoutSec);
        result.shutdownWait = pick(override.shutdownWait, base.shutdownWait);
        result.logDir = pick(override.logDir, base.logDir);
        result.scheduleSteps = pick(override.scheduleSteps, base.scheduleSteps);
        return result;
    }

    private <T> T pick(T override, T base) {
        return override != null ? override : base;
    }

    private void validate(Config config) {
        if (config.intervalMin == null || config.intervalMin <= 0) {
            throw new ParameterException(new CommandLine(this), "interval-min must be > 0");
        }
        if (config.mode == null || config.mode < 1 || config.mode > 4) {
            throw new ParameterException(new CommandLine(this), "mode must be between 1 and 4");
        }
        if (config.runFor == null) {
            throw new ParameterException(new CommandLine(this), "run-for is required");
        }
        if (config.baseUrl == null || config.baseUrl.isBlank()) {
            throw new ParameterException(new CommandLine(this), "base-url is required");
        }
        if (config.mode != 4) {
            if (config.epcList == null || config.epcList.isEmpty()) {
                throw new ParameterException(new CommandLine(this), "epc-list must not be empty");
            }
            int epcCount = config.epcList.size();
            if (config.mode == 1) {
                validateMode1(config, epcCount);
            }
        }
        validatePort(config.devicePort, "devicePort");
        if (config.mode == 4) {
            validateScheduleSteps(config);
        }
    }

    private void runScheduler(Config config) {
        Duration runForDuration = DurationParser.parse(config.runFor);
        Duration shutdownWaitDuration = DurationParser.parse(config.shutdownWait);
        Duration connectTimeout = Duration.ofSeconds(config.connectTimeoutSec);
        Duration requestTimeout = Duration.ofSeconds(config.requestTimeoutSec);
        Duration intervalDuration = Duration.ofMinutes(config.intervalMin);
        Duration sleepDetectionThreshold = intervalDuration.plus(Duration.ofSeconds(30));

        LogWriter logger = new LogWriter(config.logDir);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Instant startTime = Instant.now();
        AtomicReference<Instant> endTimeRef = new AtomicReference<>(startTime.plus(runForDuration));
        AtomicReference<ScheduledFuture<?>> shutdownFutureRef = new AtomicReference<>();

        shutdownFutureRef.set(scheduleShutdown(scheduler, logger, endTimeRef.get()));

        Runnable task = new Runnable() {
            private int tickIndex = 0;
            private Instant lastRunAt;

            @Override
            public void run() {
                if (scheduler.isShutdown()) {
                    return;
                }
                Instant now = Instant.now();
                if (lastRunAt != null) {
                    Duration gap = Duration.between(lastRunAt, now);
                    if (gap.compareTo(sleepDetectionThreshold) > 0) {
                        Duration missed = gap.minus(intervalDuration);
                        if (!missed.isNegative() && !missed.isZero()) {
                            Instant newEndTime = endTimeRef.get().plus(missed);
                            endTimeRef.set(newEndTime);
                            ScheduledFuture<?> previous = shutdownFutureRef.getAndSet(
                                    scheduleShutdown(scheduler, logger, newEndTime)
                            );
                            if (previous != null) {
                                previous.cancel(false);
                            }
                            logger.info("Detected sleep gap " + gap.toSeconds() + "s, extending end time by "
                                    + missed.toSeconds() + "s.");
                        }
                    }
                }
                lastRunAt = now;
                ScheduleStep step = resolveStep(config, tickIndex);
                tickIndex = tickIndex + 1;
                String epcList = String.join(",", step.epcList);
                String url = buildUrl(config, step.devicePort, epcList);
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
                            config.mode, config.intervalMin, step.devicePort, epcList, url, response.statusCode(), elapsedMs, snippet);
                    logger.info(logMessage);
                } catch (Exception e) {
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    String logMessage = String.format("mode=%d interval=%dmin devicePort=%d epcList=%s url=%s error=%s elapsedMs=%d",
                            config.mode, config.intervalMin, step.devicePort, epcList, url, e.getMessage(), elapsedMs);
                    logger.error(logMessage);
                }

                if (Instant.now().isAfter(endTimeRef.get())) {
                    logger.info("Reached end time, shutting down scheduler.");
                    scheduler.shutdown();
                }
            }
        };

        scheduler.scheduleWithFixedDelay(task, 0, config.intervalMin, TimeUnit.MINUTES);

        try {
            while (true) {
                Instant endTime = endTimeRef.get();
                Instant latestShutdown = endTime.plus(shutdownWaitDuration);
                Duration waitDuration = Duration.between(Instant.now(), latestShutdown);
                long waitMillis = Math.max(waitDuration.toMillis(), 0L);
                boolean finished = scheduler.awaitTermination(waitMillis, TimeUnit.MILLISECONDS);
                if (finished) {
                    break;
                }
                if (Instant.now().isAfter(latestShutdown)) {
                    logger.error("Scheduler did not shut down in time, forcing shutdown.");
                    scheduler.shutdownNow();
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private ScheduledFuture<?> scheduleShutdown(ScheduledExecutorService scheduler, LogWriter logger, Instant endTime) {
        Duration delay = Duration.between(Instant.now(), endTime);
        long delayMillis = Math.max(delay.toMillis(), 0L);
        return scheduler.schedule(() -> {
            logger.info("Run-for reached, shutting down scheduler.");
            scheduler.shutdown();
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private ScheduleStep resolveStep(Config config, int tickIndex) {
        return switch (config.mode) {
            case 1 -> newStep(config.devicePort, List.of(resolveSingleEpc(config)));
            case 2 -> newStep(config.devicePort, List.of(config.epcList.get(tickIndex % config.epcList.size())));
            case 3 -> newStep(config.devicePort, config.epcList);
            case 4 -> resolveMode4Step(config, tickIndex);
            default -> throw new IllegalArgumentException("Unsupported mode: " + config.mode);
        };
    }

    private ScheduleStep resolveMode4Step(Config config, int tickIndex) {
        List<ScheduleStep> steps = config.scheduleSteps;
        int index = tickIndex % steps.size();
        ScheduleStep source = steps.get(index);
        return newStep(source.devicePort, source.epcList);
    }

    private String resolveSingleEpc(Config config) {
        if (config.singleEpc != null && !config.singleEpc.isBlank()) {
            return config.singleEpc.trim();
        }
        int index = config.singleEpcIndex == null ? 0 : config.singleEpcIndex;
        if (index < 0 || index >= config.epcList.size()) {
            throw new IllegalArgumentException("single-epc-index out of range: " + index);
        }
        return config.epcList.get(index);
    }

    private void validateMode1(Config config, int epcCount) {
        if (config.singleEpc != null && !config.singleEpc.isBlank()) {
            return;
        }
        if (epcCount < 1) {
            throw new ParameterException(new CommandLine(this), "mode 1 requires at least 1 EPC value");
        }
        int index = config.singleEpcIndex == null ? 0 : config.singleEpcIndex;
        if (index < 0 || index >= epcCount) {
            throw new ParameterException(new CommandLine(this), "single-epc-index out of range: " + index);
        }
    }

    private ScheduleStep newStep(Integer devicePort, List<String> epcList) {
        ScheduleStep step = new ScheduleStep();
        step.devicePort = devicePort;
        step.epcList = epcList;
        return step;
    }

    private void validateScheduleSteps(Config config) {
        if (config.scheduleSteps == null || config.scheduleSteps.isEmpty()) {
            throw new ParameterException(new CommandLine(this), "mode 4 requires scheduleSteps in config");
        }
        for (int i = 0; i < config.scheduleSteps.size(); i++) {
            ScheduleStep step = config.scheduleSteps.get(i);
            if (step == null) {
                throw new ParameterException(new CommandLine(this), "scheduleSteps[" + i + "] is null");
            }
            if (step.devicePort == null) {
                throw new ParameterException(new CommandLine(this), "scheduleSteps[" + i + "].devicePort is required");
            }
            validatePort(step.devicePort, "scheduleSteps[" + i + "].devicePort");
            if (step.epcList == null || step.epcList.isEmpty()) {
                throw new ParameterException(new CommandLine(this), "scheduleSteps[" + i + "].epcList is required");
            }
        }
    }

    private void validatePort(Integer port, String name) {
        if (port == null || port < 0) {
            throw new ParameterException(new CommandLine(this), name + " must be >= 0");
        }
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
