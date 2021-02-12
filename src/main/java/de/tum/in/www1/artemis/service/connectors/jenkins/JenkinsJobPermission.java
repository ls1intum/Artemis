package de.tum.in.www1.artemis.service.connectors.jenkins;

public enum JenkinsJobPermission {

    JOB_BUILD("hudson.model.Item.Build"), JOB_CANCEL("hudson.model.Item.Cancel"), JOB_CONFIGURE("hudson.model.Item.Configure"), JOB_CREATE("hudson.model.Item.Create"),
    JOB_DELETE("hudson.model.Item.Delete"), JOB_READ("hudson.model.Item.Read"), RUN_DELETE("hudson.model.Run.Delete"), RUN_REPLAY("hudson.model.Run.Replay"),
    RUN_UPDATE("hudson.model.Run.Update");

    private String name;

    JenkinsJobPermission(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
