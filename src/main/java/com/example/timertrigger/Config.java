package com.example.timertrigger;

public class Config {
    public Integer intervalMin;
    public String runFor;
    public Integer mode;

    public java.util.List<String> epcList = java.util.Arrays.asList(
            "E28011B0A502006D6D1E90F7",
            "E28011B0A502006D6D1EF607",
            "E28011B0A502006D6D1EF637"
    );
    public String singleEpc;
    public Integer singleEpcIndex = 0;
    public java.util.List<ScheduleStep> scheduleSteps;

    public String baseUrl = "http://localhost:9055";
    public Integer deviceId = 1;
    public Integer devicePort = 0;
    public Integer durationSec = 60;
    public Integer qvalue = 0;
    public Integer rfmode = 113;

    public Integer connectTimeoutSec = 5;
    public Integer requestTimeoutSec = 30;
    public String shutdownWait = "30s";

    public String logDir = "logs";
}
