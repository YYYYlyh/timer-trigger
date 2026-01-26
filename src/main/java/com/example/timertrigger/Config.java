package com.example.timertrigger;

public class Config {
    public Integer intervalMin;
    public String runFor;
    public Integer mode;

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
