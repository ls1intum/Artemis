package de.tum.cit.aet.artemis.programming.domain;

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

    /**
     * Converts a string to a RepositoryType enum value.
     * <p>
     * Expects enum constant names (e.g., "TEMPLATE", "template", "SOLUTION"), not actual repository names (e.g., "exercise").
     *
     * @param name the enum constant name (case-insensitive)
     * @return the corresponding RepositoryType
     * @throws IllegalArgumentException if the name does not match any enum constant
     */
    public static RepositoryType fromString(String name) {
        return RepositoryType.valueOf(name.toUpperCase());
    }
}
