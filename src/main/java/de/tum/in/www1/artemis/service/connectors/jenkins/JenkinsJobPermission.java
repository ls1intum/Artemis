package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.util.Set;

public enum JenkinsJobPermission {

    JOB_BUILD("hudson.model.Item.Build"), JOB_CANCEL("hudson.model.Item.Cancel"), JOB_CONFIGURE("hudson.model.Item.Configure"), JOB_CREATE("hudson.model.Item.Create"),
    JOB_DELETE("hudson.model.Item.Delete"), JOB_READ("hudson.model.Item.Read"), JOB_WORKSPACE("hudson.model.Item.Workspace"), RUN_DELETE("hudson.model.Run.Delete"),
    RUN_REPLAY("hudson.model.Run.Replay"), RUN_UPDATE("hudson.model.Run.Update"), SCM_TAG("hudson.scm.SCM.Tag");

    private final String name;

    JenkinsJobPermission(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Set<JenkinsJobPermission> getTeachingAssistantPermissions() {
        return Set.of(JenkinsJobPermission.JOB_READ, JenkinsJobPermission.JOB_BUILD, JenkinsJobPermission.JOB_CANCEL, JenkinsJobPermission.RUN_UPDATE);
    }

    public static Set<JenkinsJobPermission> getInstructorPermissions() {
        return Set.of(JenkinsJobPermission.values());
    }
}
