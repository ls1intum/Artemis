package de.tum.in.www1.artemis.domain.enumeration;

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
