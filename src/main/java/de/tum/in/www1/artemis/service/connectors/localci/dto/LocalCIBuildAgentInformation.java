package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class LocalCIBuildAgentInformation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;

    private int maxNumberOfConcurrentBuildJobs;

    private int numberOfCurrentBuildJobs;

    private List<LocalCIBuildJobQueueItem> runningBuildJobs;

    public LocalCIBuildAgentInformation(String name, int maxNumberOfConcurrentBuildJobs, int numberOfCurrentBuildJobs, List<LocalCIBuildJobQueueItem> runningBuildJobs) {
        this.name = name;
        this.maxNumberOfConcurrentBuildJobs = maxNumberOfConcurrentBuildJobs;
        this.numberOfCurrentBuildJobs = numberOfCurrentBuildJobs;
        this.runningBuildJobs = runningBuildJobs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxNumberOfConcurrentBuildJobs() {
        return maxNumberOfConcurrentBuildJobs;
    }

    public void setMaxNumberOfConcurrentBuildJobs(int maxNumberOfConcurrentBuildJobs) {
        this.maxNumberOfConcurrentBuildJobs = maxNumberOfConcurrentBuildJobs;
    }

    public int getNumberOfCurrentBuildJobs() {
        return numberOfCurrentBuildJobs;
    }

    public void setNumberOfCurrentBuildJobs(int numberOfCurrentBuildJobs) {
        this.numberOfCurrentBuildJobs = numberOfCurrentBuildJobs;
    }

    public List<LocalCIBuildJobQueueItem> getRunningBuildJobs() {
        return runningBuildJobs;
    }

    public void setRunningBuildJobs(List<LocalCIBuildJobQueueItem> runningBuildJobs) {
        this.runningBuildJobs = runningBuildJobs;
    }

    @Override
    public String toString() {
        return "LocalCIBuildAgentInformation{" + "name='" + name + '\'' + ", maxNumberOfConcurrentBuildJobs=" + maxNumberOfConcurrentBuildJobs + ", numberOfCurrentBuildJobs="
                + numberOfCurrentBuildJobs + ", runningBuildJobs=" + runningBuildJobs + '}';
    }
}
