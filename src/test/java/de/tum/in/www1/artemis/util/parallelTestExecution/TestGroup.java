package de.tum.in.www1.artemis.util.parallelTestExecution;

import de.tum.in.www1.artemis.*;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;

public enum TestGroup {

    BAMBOO(AbstractSpringIntegrationBambooBitbucketJiraTest.class), GITLAB_CI(AbstractSpringIntegrationGitlabCIGitlabSamlTest.class),
    JENKINS(AbstractSpringIntegrationJenkinsGitlabTest.class), LOCAL(AbstractSpringIntegrationLocalCILocalVCTest.class), SIMPLE(AbstractSpringIntegrationTest.class),
    UNIT_TEST(null);

    private final Class<?> clazz;

    TestGroup(Class<?> clazz) {
        this.clazz = clazz;
    }

    public static TestGroup fromClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }

        for (TestGroup group : values()) {
            if (group.clazz != null && group.clazz.isAssignableFrom(clazz)) {
                return group;
            }
        }

        if (AbstractArtemisIntegrationTest.class.isAssignableFrom(clazz)) {
            throw new RuntimeException("Test class " + clazz.getName() + " extends ArtemisIntegrationTest but is not assigned to a test group");
        }

        return UNIT_TEST;
    }
}
