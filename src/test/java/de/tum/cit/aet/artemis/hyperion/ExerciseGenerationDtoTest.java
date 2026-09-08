package de.tum.cit.aet.artemis.hyperion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationActivityDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationLiveUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRevertResultDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;

class ExerciseGenerationDtoTest {

    private final JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void generationRequestRequiresExplicitMode() {
        assertThat(validator.validate(new ExerciseGenerationRequestDTO(null, null, null))).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("mode");
            assertThat(violation.getMessage()).isEqualTo("must not be null");
        });
        assertThat(validator.validate(new ExerciseGenerationRequestDTO(GenerationMode.GENERATE, null, null))).isEmpty();
    }

    @Test
    void fileChangeClassifiesRepositoryAndSerializesItsDiscriminator() throws Exception {
        ExerciseGenerationFileChangeDTO change = ExerciseGenerationFileChangeDTO.of("solution/src/A.java", ExerciseGenerationFileChangeDTO.ACTION_DELETE, 7);

        assertThat(change.repo()).isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_SOLUTION);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(change));
        assertThat(json.get("type").asText()).isEqualTo("FILE_CHANGE");
        assertThat(json.get("action").asText()).isEqualTo("delete");
        assertThat(ExerciseGenerationFileChangeDTO.of("template/src/A.java", ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1).repo())
                .isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_TEMPLATE);
        assertThat(ExerciseGenerationFileChangeDTO.of("tests/test/ATest.java", ExerciseGenerationFileChangeDTO.ACTION_WRITE, 1).repo())
                .isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_TESTS);
        assertThat(ExerciseGenerationFileChangeDTO.of("problem-statement.md", ExerciseGenerationFileChangeDTO.ACTION_EDIT, 1).repo())
                .isEqualTo(ExerciseGenerationFileChangeDTO.REPOSITORY_OTHER);
    }

    @Test
    void event_progressEvent_omitsNullTerminalFieldsAndSerialisesEnumAsName() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.STARTED, "go")));

        assertThat(json.get("type").asText()).isEqualTo("STARTED");
        assertThat(json.get("message").asText()).isEqualTo("go");
        assertThat(json.has("completionStatus")).isFalse();
        assertThat(json.has("verdict")).isFalse();
        assertThat(json.has("liveExerciseChanged")).isFalse();
    }

    @Test
    void verdict_serializesReasonsEvenWhenEmpty_toMatchOpenApiContract() throws Exception {
        JsonNode accepted = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationVerdictDTO(true, true, true, 5, List.of())));
        assertThat(accepted.get("reasons")).isEmpty();
        assertThat(accepted.get("mechanicallyVerified").asBoolean()).isTrue();
        assertThat(accepted.get("testCount").asInt()).isEqualTo(5);

        JsonNode rejected = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationVerdictDTO(false, false, true, 2, List.of("solution failed"))));
        assertThat(rejected.get("reasons")).hasSize(1);
        assertThat(rejected.get("reasons").get(0).asText()).isEqualTo("solution failed");
    }

    @Test
    void requiredUsageAndRevertCollectionsArePresentWhenEmpty() throws Exception {
        ExerciseGenerationUsageDTO usage = new ExerciseGenerationUsageDTO(0, 0, 0, 0, 0, 0, 0, true, 0, true, List.of(), List.of(), true);

        JsonNode usageJson = mapper.readTree(mapper.writeValueAsString(usage));
        JsonNode revertJson = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationRevertResultDTO(false, List.of(), Instant.EPOCH)));

        assertThat(usageJson.get("models")).isEmpty();
        assertThat(usageJson.get("providerRequestIds")).isEmpty();
        assertThat(revertJson.get("revertedRepositories")).isEmpty();
    }

    @Test
    void status_serializesEmptyCollectionsRequiredByTheWireContract() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", false, null, List.of(), List.of(), true)));

        assertThat(json.get("events")).isEmpty();
        assertThat(json.get("fileChanges")).isEmpty();
        assertThat(json.get("revertAvailable").asBoolean()).isTrue();
        assertThat(json.get("ownedByCaller").asBoolean()).isTrue();
        assertThat(json.get("cancellable").asBoolean()).isFalse();
        assertThat(json.has("mode")).isFalse();
    }

    @Test
    void status_serializesFalseOwnershipRequiredByTheWireContract() throws Exception {
        JsonNode json = mapper
                .readTree(mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", true, GenerationMode.GENERATE, List.of(), List.of(), false, null, null, false)));

        assertThat(json.has("ownedByCaller")).isTrue();
        assertThat(json.get("ownedByCaller").asBoolean()).isFalse();
        assertThat(json.get("cancellable").asBoolean()).isFalse();
    }

    @Test
    void status_omitsSpecDocumentWhenAbsent_butIncludesItWhenCaptured() throws Exception {
        JsonNode withoutSpecDocument = mapper
                .readTree(mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", false, null, List.of(), List.of(), true, null, null, true, false, null)));
        assertThat(withoutSpecDocument.has("specDocument")).isFalse();

        JsonNode withSpecDocument = mapper.readTree(
                mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", false, null, List.of(), List.of(), true, null, null, true, false, "## Rules\n- R1: computes")));
        assertThat(withSpecDocument.get("specDocument").asText()).isEqualTo("## Rules\n- R1: computes");
    }

    @Test
    void statusSerializesCanonicalNestedUsageAndCompleteness() throws Exception {
        ExerciseGenerationUsageDTO usage = new ExerciseGenerationUsageDTO(2, 3, 17, 4, 100, 50, 40, true, 0.25, false, List.of("model-a", "model-b"),
                List.of("response-a", "response-b"), true);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(new ExerciseGenerationStatusDTO("job", false, GenerationMode.GENERATE, List.of(), List.of(), false, null, null,
                true, false, null, usage, ExerciseGenerationAccountingState.COMPLETE, null, false)));

        assertThat(json.get("accountingState").asText()).isEqualTo("COMPLETE");
        assertThat(json.get("usage").get("modelCalls").asLong()).isEqualTo(2);
        assertThat(json.get("usage").get("toolCalls").asLong()).isEqualTo(3);
        assertThat(json.get("usage").get("agentTurns").asLong()).isEqualTo(17);
        assertThat(json.get("usage").get("attempts").asLong()).isEqualTo(4);
        assertThat(json.get("usage").get("inputTokens").asLong()).isEqualTo(100);
        assertThat(json.get("usage").get("outputTokens").asLong()).isEqualTo(50);
        assertThat(json.get("usage").get("cachedInputTokens").asLong()).isEqualTo(40);
        assertThat(json.get("usage").get("cachedInputTokensComplete").asBoolean()).isTrue();
        assertThat(json.get("usage").get("estimatedCostEur").asDouble()).isEqualTo(0.25);
        assertThat(json.get("usage").get("estimatedCostEurComplete").asBoolean()).isFalse();
        assertThat(json.get("usage").get("providerRequestIds")).hasSize(2);
        assertThat(json.get("usage").get("providerRequestIdsComplete").asBoolean()).isTrue();
        assertThat(json.get("usage").get("models")).hasSize(2);
    }

    /**
     * The activity is the client's only source for "waiting on the model since …", so {@code waitingOnModel: false} and the zero counters have to reach the wire. The DTO's
     * {@code NON_EMPTY} inclusion would silently drop them if it treated {@code false}/{@code 0} as empty.
     */
    @Test
    void activitySerializesFalseAndZeroValuesInsteadOfOmittingThem() throws Exception {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.activity("Working on the exercise.", new ExerciseGenerationActivityDTO(null, 1, 1, false, 0, 0, 0));

        JsonNode json = mapper.readTree(mapper.writeValueAsString(event));

        JsonNode activity = json.get("activity");
        assertThat(activity).isNotNull();
        assertThat(activity.get("waitingOnModel").asBoolean()).isFalse();
        assertThat(activity.get("modelCalls").asInt()).isZero();
        assertThat(activity.get("toolCalls").asInt()).isZero();
        assertThat(activity.get("filesWritten").asInt()).isZero();
        assertThat(activity.get("turn").asInt()).isEqualTo(1);
        assertThat(activity.has("step")).as("an absent substep is omitted rather than sent as null").isFalse();
        assertThat(json.has("phase")).isFalse();
    }

    /**
     * The client renders a progress bar and a euro figure from these, so {@code false} and the zero counters have to reach the wire; {@code NON_EMPTY} inclusion would drop them
     * if it treated them as empty.
     */
    @Test
    void liveUsageSerializesItsCountersEvenWhenNothingHasBeenSpentYet() throws Exception {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.phase(ExerciseGenerationEventDTO.Phase.PREPARING, "Preparing")
                .withLiveUsage(new ExerciseGenerationLiveUsageDTO(0, 0, 0, 0, 250_000, 0, 0d, true));

        JsonNode liveUsage = mapper.readTree(mapper.writeValueAsString(event)).get("liveUsage");

        assertThat(liveUsage.get("inputTokens").asLong()).isZero();
        assertThat(liveUsage.get("outputTokens").asLong()).isZero();
        assertThat(liveUsage.get("cachedInputTokens").asLong()).isZero();
        assertThat(liveUsage.get("billableTokens").asLong()).isZero();
        assertThat(liveUsage.get("modelCalls").asLong()).isZero();
        assertThat(liveUsage.get("tokenBudget").asLong()).isEqualTo(250_000);
        assertThat(liveUsage.get("estimatedCostEur").asDouble()).isZero();
        assertThat(liveUsage.get("estimatedCostComplete").asBoolean()).isTrue();
    }

    /** An unpriced model has to read as "not priced", never as "free": the absent figure is what stops the client from claiming a cost nobody computed. */
    @Test
    void liveUsageOmitsTheCostWhenTheEstimateIsIncomplete() throws Exception {
        ExerciseGenerationEventDTO event = ExerciseGenerationEventDTO.activity("Thinking.", new ExerciseGenerationActivityDTO(null, 1, 1, true, 1, 0, 0))
                .withLiveUsage(new ExerciseGenerationLiveUsageDTO(1000, 100, 800, 700, 250_000, 1, null, false));

        JsonNode liveUsage = mapper.readTree(mapper.writeValueAsString(event)).get("liveUsage");

        assertThat(liveUsage.has("estimatedCostEur")).isFalse();
        assertThat(liveUsage.get("estimatedCostComplete").asBoolean()).isFalse();
        assertThat(liveUsage.get("billableTokens").asLong()).isEqualTo(700);
    }

    @Test
    void eventsWithoutASpendSnapshotOmitIt() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "Setting up the build environment")));

        assertThat(json.has("liveUsage")).isFalse();
    }

    @Test
    void eventsWithoutAnActivityContextOmitIt() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, "Setting up the build environment")));

        assertThat(json.has("activity")).isFalse();
    }
}
