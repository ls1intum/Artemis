package de.tum.in.www1.artemis.domain.enumeration;

public enum RepositoryType {

    TEMPLATE("BASE"), SOLUTION("SOLUTION"), TESTS("TESTS");

    private String name;

    RepositoryType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
