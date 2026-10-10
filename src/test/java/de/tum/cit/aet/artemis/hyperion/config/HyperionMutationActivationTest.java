package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.WedgedSlotInfo;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.WedgedSlotKind;
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
    void absentOrDisabledGenerationStillProtectsWriter(String profile) {
        when(provider.getLocalNodeId()).thenReturn("writer");
        when(provider.getCoordinationSnapshot()).thenReturn(Optional.empty());
        for (String flag : new String[] { "unused.property=true", "artemis.hyperion.exercise-generation.enabled=false" }) {
            runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile)).withPropertyValues(flag).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(HyperionExerciseMutationApi.class);
                if ("core".equals(profile)) {
                    assertThat(context).hasSingleBean(ADMIN_RESOURCE);
                }
                else {
                    assertThat(context).doesNotHaveBean(ADMIN_RESOURCE);
                }
                assertThatThrownBy(() -> context.getBean(ProgrammingExerciseMutationGuardService.class).claimExternalMutation(1L))
                        .isInstanceOf(ServiceUnavailableAlertException.class);
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

    @Test
    void generationDisabledCoreReadsAndAuditsRecoveryThroughTheMutationService() {
        var mutations = mock(GenerationExternalMutationService.class);
        var audit = mock(AuditEventRepository.class);
        var slot = new WedgedSlotInfo(42L, "mutation-token", WedgedSlotKind.EXTERNAL_MUTATION, "departed", Instant.parse("2026-01-01T00:00:00Z"), true);
        when(mutations.getWedgedSlotInfo(42L)).thenReturn(Optional.of(slot));
        when(mutations.recoverWedgedSlot(42L, slot.token())).thenReturn(true);
        new ApplicationContextRunner().withInitializer(context -> {
            context.getEnvironment().setActiveProfiles("core");
            context.getBeanFactory().registerSingleton("externalMutations", mutations);
            context.getBeanFactory().registerSingleton("auditEvents", audit);
        }).withUserConfiguration(ADMIN_RESOURCE).withPropertyValues("artemis.hyperion.exercise-generation.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ADMIN_RESOURCE);
            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(ADMIN_RESOURCE)).build();
            mvc.perform(get("/api/hyperion/admin/exercises/42/wedged-slot")).andExpect(status().isOk()).andExpect(jsonPath("$.token").value(slot.token()))
                    .andExpect(jsonPath("$.kind").value("EXTERNAL_MUTATION"));
            mvc.perform(delete("/api/hyperion/admin/exercises/42/wedged-slot/{token}", slot.token()).param("reason", "Owner stopped after a crash"))
                    .andExpect(status().isNoContent());
            verify(audit).add(any(AuditEvent.class));
            verify(mutations).recoverWedgedSlot(42L, slot.token());
        });
    }
}
