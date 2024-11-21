package de.tum.cit.aet.artemis.iris.domain.settings.event;

/**
 * The type of event that can be triggered by the Iris system.
 */
public enum IrisEventType {

    BUILD_FAILED("build_failed"), PROGRESS_STALLED("progress_stalled"), JOL("jol");

    private final String name;

    IrisEventType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
