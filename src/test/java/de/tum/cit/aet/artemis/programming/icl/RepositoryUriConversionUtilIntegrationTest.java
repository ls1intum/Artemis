package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.service.RepositoryUriConversionUtil;

class RepositoryUriConversionUtilIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Test
    void staticMethodsUseDefaultServerUrlInitializedBySpring() {
        String fullUri = RepositoryUriConversionUtil.toFullRepositoryUri("course/ex1");
        assertThat(fullUri).isEqualTo("http://localhost:49152/git/course/ex1.git");
        String shortUri = RepositoryUriConversionUtil.toShortRepositoryUri("http://localhost:49152/git/course/ex1.git");
        assertThat(shortUri).isEqualTo("course/ex1");
    }
}
