package de.tum.in.www1.artemis.domain.monaco;

import java.util.Date;

import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A helper object used to represent the metrics retrieved by the
 * health endpoint of a LSP server
 */
@Profile("lsp")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LspServerStatus {

    public LspServerStatus() {
    }

    public LspServerStatus(String url) {
        this.url = url;
    }

    public LspServerStatus(String url, boolean healthy, boolean paused, int activeSessions, double cpuUsage) {
        this.url = url;
        this.healthy = healthy;
        this.paused = paused;
        this.activeSessions = activeSessions;
        this.cpuUsage = cpuUsage;
    }

    private String url;

    private boolean healthy;

    private boolean paused;

    private int activeSessions;

    private float loadAvg1;

    private float loadAvg5;

    private float loadAvg15;

    private long totalMem;

    private long freeMem;

    private double cpuUsage;

    private Date timestamp;

    public float getLoadAvg1() {
        return loadAvg1;
    }

    public void setLoadAvg1(float loadAvg1) {
        this.loadAvg1 = loadAvg1;
    }

    public float getLoadAvg5() {
        return loadAvg5;
    }

    public void setLoadAvg5(float loadAvg5) {
        this.loadAvg5 = loadAvg5;
    }

    public float getLoadAvg15() {
        return loadAvg15;
    }

    public void setLoadAvg15(float loadAvg15) {
        this.loadAvg15 = loadAvg15;
    }

    public long getTotalMem() {
        return totalMem;
    }

    public void setTotalMem(long totalMem) {
        this.totalMem = totalMem;
    }

    public long getFreeMem() {
        return freeMem;
    }

    public void setFreeMem(long freeMem) {
        this.freeMem = freeMem;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public int getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(int activeSessions) {
        this.activeSessions = activeSessions;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
