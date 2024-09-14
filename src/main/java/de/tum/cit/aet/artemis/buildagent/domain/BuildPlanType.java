package de.tum.cit.aet.artemis.buildagent.domain;

public enum BuildPlanType {

    TEMPLATE("BASE"), SOLUTION("SOLUTION");

    private final String name;

    BuildPlanType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
