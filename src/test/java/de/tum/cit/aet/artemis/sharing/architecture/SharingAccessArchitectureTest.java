package de.tum.cit.aet.artemis.sharing.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;
import de.tum.cit.aet.artemis.core.config.RestClientConfiguration;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.ServiceCredentialBudget;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.connectors.ConnectorHealth;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.web.util.ResponseUtil;
import de.tum.cit.aet.artemis.programming.api.ProgrammingExerciseArchiveApi;
import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

class SharingAccessArchitectureTest extends AbstractArchitectureTest {

    private static final String SHARING = ARTEMIS_PACKAGE + ".sharing..";

    @Test
    void sharingUsesSharedCoreAndTheProgrammingArchiveApi() {
        var availablePackages = resideInAnyPackage(SHARING, ARTEMIS_PACKAGE + ".core..");
        var archiveContracts = belongToAnyOf(ProgrammingExerciseArchiveApi.class, ImportProgrammingExerciseRequestDTO.class, ProgrammingExerciseResponseDTO.class);
        classes().that().resideInAPackage(SHARING).should().onlyDependOnClassesThat(resideOutsideOfPackage(ARTEMIS_PACKAGE + "..").or(availablePackages).or(archiveContracts))
                .check(productionClasses);
    }

    @Test
    void sharingOnlyUsesTheCoreInfrastructureNeededByTheConnector() {
        var infrastructure = belongToAnyOf(ArtemisConfigHelper.class, RestClientConfiguration.class, FeatureInteraction.class, BadRequestAlertException.class,
                EntityNotFoundException.class, ServiceCredentialBudget.class, EnforceAtLeastStudent.class, ManualConfig.class, ConnectorHealth.class, FeatureUsage.class,
                UsageInteraction.class, UserFeature.class, ResponseUtil.class);
        classes().that().resideInAPackage(SHARING).should().onlyDependOnClassesThat(resideOutsideOfPackage(ARTEMIS_PACKAGE + ".core..").or(infrastructure))
                .check(productionClasses);
    }

    @Test
    void otherModulesCanRunWithoutSharing() {
        noClasses().that().resideOutsideOfPackage(SHARING).should().dependOnClassesThat().resideInAPackage(SHARING).check(productionClasses);
    }
}
