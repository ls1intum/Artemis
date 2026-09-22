package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;

class HyperionMutationActivationTest {

    private static final Class<?> ADMIN_RESOURCE = adminResourceClass();

    private static Class<?> adminResourceClass() {
        try {
            return Class.forName("de.tum.cit.aet.artemis.hyperion.web.admin.AdminHyperionGenerationResource");
        }
        catch (ClassNotFoundException exception) {
            throw new AssertionError(exception);
        }
    }

    private final DistributedDataProvider provider = mock();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(HyperionExerciseMutationApi.class, GenerationExternalMutationService.class, ProgrammingExerciseMutationGuardService.class, ADMIN_RESOURCE)
            .withBean(DistributedDataProvider.class, () -> provider).withBean(AuditEventRepository.class, () -> mock(AuditEventRepository.class));

    @ParameterizedTest
    @ValueSource(strings = { "core", "localvc" })
    void absentOrDisabledGenerationDoesNotContactDistributedState(String profile) {
        for (String flag : new String[] { "unused.property=true", "artemis.hyperion.exercise-generation.enabled=false" }) {
            runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile)).withPropertyValues(flag).run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(HyperionExerciseMutationApi.class).doesNotHaveBean(GenerationExternalMutationService.class)
                        .doesNotHaveBean(ADMIN_RESOURCE);
                try (var ignored = context.getBean(ProgrammingExerciseMutationGuardService.class).claimExternalMutation(1L)) {
                    verifyNoInteractions(provider);
                }
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "core", "localvc" })
    void generationDeploymentProtectsWriterEvenWithoutLocalModelsOrWorker(String profile) {
        when(provider.getLocalNodeId()).thenReturn("writer");
        when(provider.getCoordinationSnapshot()).thenReturn(Optional.empty());
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("artemis.hyperion.exercise-generation.enabled=true", "artemis.hyperion.enabled=false", "artemis.aiworker.enabled=false").run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(HyperionExerciseMutationApi.class).hasSingleBean(GenerationExternalMutationService.class);
                    if ("core".equals(profile)) {
                        assertThat(context).hasSingleBean(ADMIN_RESOURCE);
                    }
                    assertThatThrownBy(() -> context.getBean(ProgrammingExerciseMutationGuardService.class).claimExternalMutation(1L))
                            .isInstanceOf(ServiceUnavailableAlertException.class);
                });
    }
}
