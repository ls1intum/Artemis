package de.tum.in.www1.artemis.service.connectors.jenkins.jobs;

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

    public static Set<JenkinsJobPermission> getStudentPermissions() {
        return Set.of(JOB_BUILD, JOB_READ);
    }

    public static Set<JenkinsJobPermission> getTeachingAssistantPermissions() {
        return Set.of(JOB_BUILD, JOB_CANCEL, JOB_READ, RUN_UPDATE);
    }

    public static Set<JenkinsJobPermission> getEditorPermissions() {
        return Set.of(JOB_BUILD, JOB_CANCEL, JOB_CONFIGURE, JOB_CREATE, JOB_READ, JOB_WORKSPACE, RUN_REPLAY, RUN_UPDATE, SCM_TAG);
    }

    public static Set<JenkinsJobPermission> getInstructorPermissions() {
        return Set.of(JenkinsJobPermission.values());
    }
}
