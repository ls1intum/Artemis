package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DockerRunConfig implements Serializable {

    private boolean isNetworkDisabled;

    private List<String> env;

    public boolean isNetworkDisabled() {
        return isNetworkDisabled;
    }

    public void setNetworkDisabled(boolean networkDisabled) {
        isNetworkDisabled = networkDisabled;
    }

    public List<String> getEnv() {
        return env;
    }

    public void setEnv(List<String> env) {
        this.env = env;
    }

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
