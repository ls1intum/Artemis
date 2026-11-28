package de.tum.cit.aet.artemis.sharing.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;
import de.tum.cit.aet.artemis.sharing.web.ExerciseSharingResource;
import de.tum.cit.aet.artemis.sharing.web.SharingSupportResource;

class SharingResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".sharing";
    }

    @Override
    protected Set<Class<?>> getIgnoredModulePathPrefixResources() {
        return Set.of(SharingSupportResource.class, ExerciseSharingResource.class);
    }

    @Test
    void existingSharingClientsKeepTheirEndpointPrefixes() {
        assertThat(SharingSupportResource.class.getAnnotation(RequestMapping.class).value()).containsExactly("api/core/sharing/");
        assertThat(ExerciseSharingResource.class.getAnnotation(RequestMapping.class).value()).containsExactly("api/programming/sharing/");
    }
}
