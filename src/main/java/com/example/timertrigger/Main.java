package com.example.timertrigger;

import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
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

    @Option(names = "--epc-interval-sec", description = "Interval seconds between EPC requests in mode 2/4")
    private Integer epcIntervalSec;

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
        cliConfig.epcIntervalSec = epcIntervalSec;
        cliConfig.connectTimeoutSec = connectTimeoutSec;
        cliConfig.requestTimeoutSec = requestTimeoutSec;
        cliConfig.shutdownWait = shutdownWait;
        cliConfig.logDir = logDir;
        return merge(merged, cliConfig);
    }

    private Config loadYaml(String path) {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(Paths.get(path))) {
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
        result.epcIntervalSec = pick(override.epcIntervalSec, base.epcIntervalSec);
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
        if (config.baseUrl == null || config.baseUrl.trim().isEmpty()) {
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
        if (config.mode == 2 || config.mode == 4) {
            validateEpcIntervals(config);
        }
    }

    private void runScheduler(Config config) {
        Duration runForDuration = DurationParser.parse(config.runFor);
        Duration shutdownWaitDuration = DurationParser.parse(config.shutdownWait);
        Duration connectTimeout = Duration.ofSeconds(config.connectTimeoutSec);
        Duration requestTimeout = Duration.ofSeconds(config.requestTimeoutSec);
        Duration intervalDuration = Duration.ofMinutes(config.intervalMin);
        Duration epcIntervalDuration = Duration.ofSeconds(config.epcIntervalSec);
        Duration roundIntervalDuration = intervalDuration;

        LogWriter logger = new LogWriter(config.logDir);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Instant startTime = Instant.now();
        AtomicReference<Instant> endTimeRef = new AtomicReference<>(startTime.plus(runForDuration));
        AtomicReference<ScheduledFuture<?>> shutdownFutureRef = new AtomicReference<>();
        AtomicReference<Duration> expectedGapRef = new AtomicReference<>(intervalDuration);

        shutdownFutureRef.set(scheduleShutdown(scheduler, logger, endTimeRef.get()));

        Runnable task = new Runnable() {
            private final ModeCursor cursor = new ModeCursor();
            private Instant lastRunAt;

            @Override
            public void run() {
                if (scheduler.isShutdown()) {
                    return;
                }
                Instant now = Instant.now();
                if (lastRunAt != null) {
                    Duration gap = Duration.between(lastRunAt, now);
                    Duration expectedGap = expectedGapRef.get();
                    Duration sleepDetectionThreshold = expectedGap.plus(Duration.ofSeconds(30));
                    if (gap.compareTo(sleepDetectionThreshold) > 0) {
                        Duration missed = gap.minus(expectedGap);
                        if (!missed.isNegative() && !missed.isZero()) {
                            Instant newEndTime = endTimeRef.get().plus(missed);
                            endTimeRef.set(newEndTime);
                            ScheduledFuture<?> previous = shutdownFutureRef.getAndSet(
                                    scheduleShutdown(scheduler, logger, newEndTime)
                            );
                            if (previous != null) {
                                previous.cancel(false);
                            }
                            logger.info("Detected sleep gap " + gap.getSeconds() + "s, extending end time by "
                                    + missed.getSeconds() + "s.");
                        }
                    }
                }
                lastRunAt = now;
                ScheduleExecution execution = nextExecution(config, cursor);
                String epcList = String.join(",", execution.epcList);
                String url = buildUrl(config, execution.devicePort, epcList);
                long start = System.nanoTime();
                try {
                    HttpURLConnection connection = openConnection(url, connectTimeout, requestTimeout);
                    int statusCode = connection.getResponseCode();
                    String body = readResponseBody(connection);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    String logMessage = String.format("mode=%d interval=%dmin devicePort=%d epcList=%s url=%s status=%d elapsedMs=%d response=%s",
                            config.mode, config.intervalMin, execution.devicePort, epcList, url, statusCode, elapsedMs, snippet);
                    logger.info(logMessage);
                } catch (Exception e) {
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    String logMessage = String.format("mode=%d interval=%dmin devicePort=%d epcList=%s url=%s error=%s elapsedMs=%d",
                            config.mode, config.intervalMin, execution.devicePort, epcList, url, e.getMessage(), elapsedMs);
                    logger.error(logMessage);
                }

                if (Instant.now().isAfter(endTimeRef.get())) {
                    logger.info("Reached end time, shutting down scheduler.");
                    scheduler.shutdown();
                    return;
                }

                Duration nextDelay = computeNextDelay(config, execution, intervalDuration, epcIntervalDuration, roundIntervalDuration);
                expectedGapRef.set(nextDelay);
                scheduler.schedule(this, nextDelay.toMillis(), TimeUnit.MILLISECONDS);
            }
        };

        expectedGapRef.set(Duration.ZERO);
        scheduler.schedule(task, 0, TimeUnit.MILLISECONDS);

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
        switch (config.mode) {
            case 1:
                return newStep(config.devicePort, Collections.singletonList(resolveSingleEpc(config)));
            case 2:
                return newStep(config.devicePort, Collections.singletonList(config.epcList.get(tickIndex % config.epcList.size())));
            case 3:
                return newStep(config.devicePort, config.epcList);
            case 4:
                return resolveMode4Step(config, tickIndex);
            default:
                throw new IllegalArgumentException("Unsupported mode: " + config.mode);
        }
    }

    private ScheduleStep resolveMode4Step(Config config, int tickIndex) {
        List<ScheduleStep> steps = config.scheduleSteps;
        int index = tickIndex % steps.size();
        ScheduleStep source = steps.get(index);
        return newStep(source.devicePort, source.epcList);
    }

    private String resolveSingleEpc(Config config) {
        if (config.singleEpc != null && !config.singleEpc.trim().isEmpty()) {
            return config.singleEpc.trim();
        }
        int index = config.singleEpcIndex == null ? 0 : config.singleEpcIndex;
        if (index < 0 || index >= config.epcList.size()) {
            throw new IllegalArgumentException("single-epc-index out of range: " + index);
        }
        return config.epcList.get(index);
    }

    private void validateMode1(Config config, int epcCount) {
        if (config.singleEpc != null && !config.singleEpc.trim().isEmpty()) {
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

    private void validateEpcIntervals(Config config) {
        if (config.epcIntervalSec == null || config.epcIntervalSec <= 0) {
            throw new ParameterException(new CommandLine(this), "epc-interval-sec must be > 0 for mode 2/4");
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

    private HttpURLConnection openConnection(String url, Duration connectTimeout, Duration requestTimeout) throws IOException {
        URL target = URI.create(url).toURL();
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        connection.setReadTimeout(Math.toIntExact(requestTimeout.toMillis()));
        return connection;
    }

    private String readResponseBody(HttpURLConnection connection) throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (inputStream == null) {
                return "";
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString("UTF-8");
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private static class ModeCursor {
        private int mode2Index;
        private int mode4StepIndex;
        private int mode4EpcIndex;
    }

    private static class ScheduleExecution {
        private final int devicePort;
        private final List<String> epcList;
        private final boolean endOfGroup;

        private ScheduleExecution(int devicePort, List<String> epcList, boolean endOfGroup) {
            this.devicePort = devicePort;
            this.epcList = epcList;
            this.endOfGroup = endOfGroup;
        }
    }

    private ScheduleExecution nextExecution(Config config, ModeCursor cursor) {
        switch (config.mode) {
            case 1:
                return new ScheduleExecution(config.devicePort, Collections.singletonList(resolveSingleEpc(config)), true);
            case 2:
                return nextMode2Execution(config, cursor);
            case 3:
                return new ScheduleExecution(config.devicePort, config.epcList, true);
            case 4:
                return nextMode4Execution(config, cursor);
            default:
                throw new IllegalArgumentException("Unsupported mode: " + config.mode);
        }
    }

    private ScheduleExecution nextMode2Execution(Config config, ModeCursor cursor) {
        int index = cursor.mode2Index % config.epcList.size();
        String epc = config.epcList.get(index);
        cursor.mode2Index = index + 1;
        boolean endOfGroup = cursor.mode2Index >= config.epcList.size();
        if (endOfGroup) {
            cursor.mode2Index = 0;
        }
        return new ScheduleExecution(config.devicePort, Collections.singletonList(epc), endOfGroup);
    }

    private ScheduleExecution nextMode4Execution(Config config, ModeCursor cursor) {
        List<ScheduleStep> steps = config.scheduleSteps;
        ScheduleStep step = steps.get(cursor.mode4StepIndex);
        int epcIndex = cursor.mode4EpcIndex;
        String epc = step.epcList.get(epcIndex);
        cursor.mode4EpcIndex = epcIndex + 1;
        boolean endOfGroup = cursor.mode4EpcIndex >= step.epcList.size();
        if (endOfGroup) {
            cursor.mode4EpcIndex = 0;
            cursor.mode4StepIndex = (cursor.mode4StepIndex + 1) % steps.size();
        }
        return new ScheduleExecution(step.devicePort, Collections.singletonList(epc), endOfGroup);
    }

    private Duration computeNextDelay(Config config,
                                      ScheduleExecution execution,
                                      Duration intervalDuration,
                                      Duration epcIntervalDuration,
                                      Duration roundIntervalDuration) {
        if (config.mode == 2 || config.mode == 4) {
            return execution.endOfGroup ? roundIntervalDuration : epcIntervalDuration;
        }
        return intervalDuration;
    }
}
