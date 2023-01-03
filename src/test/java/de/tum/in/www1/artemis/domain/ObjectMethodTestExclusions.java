package de.tum.in.www1.artemis.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings;

/**
 * Exclusions of domain classes for specific tests.
 * <p>
 * <b>Don't add anything here without a very good reason!</b>
 */
enum ObjectMethodTestExclusions {

    /**
     * All possible tests.
     */
    ALL_TESTS,
    /**
     * Tests if {@link Enum#toString()} and {@link Enum#name()} are equal.
     * <p>
     * This is usually the case, and could be expected by some people or tools (but should not).
     */
    ENUM_TOSTRING_NAME_EQUALITY,
    /**
     * Tests that try to create instances of the class.
     */
    INSTANCE_TESTS,
    /**
     * Tests that concern classes that have a <code>getId</code> and <code>setId</code> Method.
     */
    ID_RELATED_TESTS,
    /**
     * Tests that <code>getId</code> and <code>setId</code> match.
     */
    ID_GET_AND_SET,
    /**
     * Tests for {@link Object#hashCode()} in relation to the id.
     */
    ID_HASHCODE,
    /**
     * Tests for {@link Object#equals(Object)} in relation to the id.
     */
    ID_EQUALS;

    private static final Map<Class<?>, EnumSet<ObjectMethodTestExclusions>> EXCLUSIONS = Map.ofEntries( //
            exclusionEntry(RepositoryType.class, ENUM_TOSTRING_NAME_EQUALITY), // Reason: toString() returns the repository names
            exclusionEntry(ConversationSettings.class, ALL_TESTS) // Reason: This class only contains constants
    );

    /**
     * Checks if the given class is excluded from the given test.
     *
     * @param clazz the class to look for
     * @param test  the test exclusion to check
     * @return <code>true</code> if the class is excluded from the test, <code>false</code> otherwise
     */
    static boolean isClassExcludedFrom(Class<?> clazz, ObjectMethodTestExclusions test) {
        var exclusions = EXCLUSIONS.get(clazz);
        return exclusions != null && exclusions.contains(test);
    }

    /**
     * Checks if the given class is not excluded from the given test.
     *
     * @param clazz the class to look for
     * @param test  the test exclusion to check
     * @return <code>true</code> if the class is not excluded from the test, <code>false</code> otherwise
     */
    static boolean isClassNotExcludedFrom(Class<?> clazz, ObjectMethodTestExclusions test) {
        return !isClassExcludedFrom(clazz, test);
    }

    /**
     * Utility method for generating map entries with Enum sets.
     */
    private static Entry<Class<?>, EnumSet<ObjectMethodTestExclusions>> exclusionEntry(Class<?> clazz, ObjectMethodTestExclusions... testIds) {
        return Map.entry(clazz, EnumSet.copyOf(List.of(testIds)));
    }
}
