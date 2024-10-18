package de.tum.cit.aet.artemis.programming.service.ci;

public enum DependencyDownloadScript {

    GRADLE {

        @Override
        public String getScript() {
            return getBaseScript() + """
                    chmod +x ./gradlew
                    ./gradlew build --refresh-dependencies
                    """;
        }
    },
    MAVEN {

        @Override
        public String getScript() {
            return getBaseScript() + """
                    mvn clean install -U
                    """;
        }
    },
    KOTLIN {

        @Override
        public String getScript() {
            return getBaseScript() + """
                    mvn clean install -U
                    """;
        }
    },
    RUST {

        @Override
        public String getScript() {
            return getBaseScript() + """
                    cargo build --verbose
                    """;
        }
    },
    JAVASCRIPT {

        @Override
        public String getScript() {
            return getBaseScript() + """
                    npm ci --prefer-offline --no-audit
                    """;
        }
    },
    OTHER {

        @Override
        public String getScript() {
            return getBaseScript() + """
                    echo "No dependency download script needed for this programming language"
                    """;
        }
    };

    private static String getBaseScript() {
        return """
                #!/bin/bash
                cd /var/tmp/testing-dir
                #!/usr/bin/env bash
                set -e
                echo "Starting dependency download script"
                """;
    }

    public abstract String getScript();
}
