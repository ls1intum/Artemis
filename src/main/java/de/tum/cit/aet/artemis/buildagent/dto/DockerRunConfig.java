package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DockerRunConfig(boolean isNetworkDisabled, List<String> env) implements Serializable {

    public enum AllowedDockerFlags {

        NETWORK("network"), ENV("env");

        private final String flag;

        AllowedDockerFlags(String flag) {
            this.flag = flag;
        }

        public String flag() {
            return flag;
        }

        private static final Set<String> ALLOWED_FLAGS = new HashSet<>();

        static {
            for (AllowedDockerFlags value : values()) {
                ALLOWED_FLAGS.add(value.flag());
            }
        }

        public static boolean isAllowed(String flag) {
            return ALLOWED_FLAGS.contains(flag);
        }
    }
}
