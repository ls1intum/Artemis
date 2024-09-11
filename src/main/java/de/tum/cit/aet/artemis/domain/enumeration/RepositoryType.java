package de.tum.cit.aet.artemis.domain.enumeration;

public enum RepositoryType {

    TEMPLATE("exercise"), SOLUTION("solution"), TESTS("tests"), AUXILIARY("auxiliary"), USER("user");

    private final String name;

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
