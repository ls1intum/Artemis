package de.tum.cit.aet.artemis.iris.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleAccessArchitectureTest;

class IrisApiArchitectureTest extends AbstractModuleAccessArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".iris";
    }

    @Override
    protected Set<Class<?>> getIgnoredClasses() {
        // @formatter:off
        return Set.of(
            de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent.class,
            de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyExtractionInputDTO.class,
            de.tum.cit.aet.artemis.iris.config.PyrisAuthorizationInterceptor.class,
            de.tum.cit.aet.artemis.iris.config.IrisEnabled.class,
            de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent.class
        );
        // @formatter:on
    }
}
