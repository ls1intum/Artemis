package de.tum.cit.aet.artemis.iris.domain.settings.event;

/**
 * The type of event that can be triggered by the Iris system.
 */
public enum IrisEventType {
    BUILD_FAILED {

        @Override
        public String toString() {
            return "build_failed";
        }
    },
    PROGRESS_STALLED {

        @Override
        public String toString() {
            return "progress_stalled";
        }
    },
    JOL {

        @Override
        public String toString() {
            return "jol";
        }
    },
}
