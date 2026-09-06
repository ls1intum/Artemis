package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchWithPasswordDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.create.MultipleChoiceQuestionCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.create.QuizQuestionCreateDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Pins the JSON the application puts on the wire.
 * <p>
 * Every expected payload here was recorded from the Jackson 2 mapper that served HTTP before the Jackson 3
 * migration, so this is what the released clients read. Jackson 3 changed a number of serialization defaults;
 * {@link ArtemisJacksonDefaults} restores the ones that would have been visible here, and this test is what says
 * whether each of those pins still earns its place.
 * <p>
 * <b>To decide whether a pin can go:</b> remove it from {@link ArtemisJacksonDefaults} and run this test. If nothing
 * fails, the pin is dead and should be deleted. If a fixture fails, its expected payload is exactly the shape that
 * would change for clients, which is the conversation to have before dropping it.
 * <p>
 * Payloads are compared as parsed trees rather than as strings, because one difference is accepted rather than
 * pinned: Jackson 3 sorts the properties of a non-record type alphabetically. That does not change what a client
 * reads, while a renamed property, a different value, a different JSON type or a property that appears or disappears
 * all still fail.
 */
class JacksonSerializationContractTest extends AbstractSpringIntegrationIndependentTest {

    private static final ZonedDateTime INSTANT = ZonedDateTime.of(2026, 3, 14, 15, 9, 26, 535000000, ZoneOffset.ofHours(2));

    @Autowired
    private JsonMapper jsonMapper;

    // --- fixtures: one per pinned default, plus the near misses that turned out not to need one -----------------

    /** {@code WRITE_ENUMS_USING_TO_STRING}: both enums override toString() with a value that differs from name(). */
    record EnumFixture(RepositoryType repositoryType, Role role) {
    }

    /** Guards the date/time defaults that need no pin: Spring Boot already made Jackson 2 write ISO-8601 too. */
    record DurationFixture(Duration duration) {
    }

    /** Year-months reach the API through the calendar module; java.time.Month and MonthDay never do. */
    record MonthFixture(YearMonth yearMonth) {
    }

    /** {@code STRIP_TRAILING_BIGDECIMAL_ZEROES}: Jackson 3 would keep the trailing zeroes. */
    record DecimalFixture(BigDecimal amount) {
    }

    /** The date format Spring Boot configures, and the offset the value was created with. */
    record DateFixture(ZonedDateTime instant) {
    }

    /** {@code FAIL_ON_NULL_FOR_PRIMITIVES}: an explicit null for a primitive component must still bind to zero. */
    record PrimitiveFixture(int count, boolean flag, double score) {
    }

    /** {@code USE_GETTERS_AS_SETTERS}: a collection reachable only through its getter must still be populated. */
    static class GetterOnlyCollectionFixture {

        private final List<String> tags = new ArrayList<>();

        public List<String> getTags() {
            return tags;
        }
    }

    private static Stream<Arguments> serializationFixtures() {
        return Stream.of(Arguments.of("enums overriding toString", new EnumFixture(RepositoryType.SOLUTION, Role.ADMIN), """
                {"repositoryType":"SOLUTION","role":"ADMIN"}"""), Arguments.of("durations", new DurationFixture(Duration.ofMinutes(90).plusSeconds(30)), """
                {"duration":"PT1H30M30S"}"""), Arguments.of("year-months", new MonthFixture(YearMonth.of(2026, 3)), """
                {"yearMonth":"2026-03"}"""), Arguments.of("big decimals", new DecimalFixture(new BigDecimal("12.500")), """
                {"amount":12.5}"""), Arguments.of("zoned date times", new DateFixture(INSTANT), """
                {"instant":"2026-03-14T15:09:26.535+02:00"}"""), Arguments.of("primitive components", new PrimitiveFixture(3, true, 4.5), """
                {"count":3,"flag":true,"score":4.5}"""),
                Arguments.of("unwrapped response DTO", new QuizBatchWithPasswordDTO(new QuizBatchDTO(7L, INSTANT, true, false), "secret"), """
                        {"id":7,"startTime":"2026-03-14T15:09:26.535+02:00","started":true,"ended":false,"password":"secret"}"""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serializationFixtures")
    void shouldSerializeToTheRecordedPayload(String description, Object fixture, String expectedJson) {
        assertThat(jsonMapper.readTree(jsonMapper.writeValueAsString(fixture))).as("payload changed for %s", description).isEqualTo(jsonMapper.readTree(expectedJson));
    }

    @Test
    void shouldWriteTheDiscriminatorOfAPolymorphicDto() {
        QuizQuestionCreateDTO question = new MultipleChoiceQuestionCreateDTO("Title", "Text", "Hint", "Explanation", 2.0, ScoringType.ALL_OR_NOTHING, false, List.of(), true);

        // the static type has to be the sealed interface, otherwise the @JsonTypeInfo discriminator is not written
        String json = jsonMapper.writerFor(QuizQuestionCreateDTO.class).writeValueAsString(question);

        assertThat(jsonMapper.readTree(json)).isEqualTo(jsonMapper.readTree("""
                {"type":"multiple-choice","title":"Title","text":"Text","hint":"Hint","explanation":"Explanation","points":2.0,"scoringType":"ALL_OR_NOTHING",
                 "randomizeOrder":false,"singleChoice":true}"""));
    }

    @Test
    void shouldWriteExactlyOneDiscriminatorForAPolymorphicEntity() {
        // The entity hierarchies declare @JsonTypeInfo on a property the subclasses also expose as a real getter
        // (TextExercise.getType() returns "text", which is also its @JsonSubTypes name). Jackson 2 tolerated writing
        // the type id next to the identically named bean property; Jackson 3 rejects the definition outright unless
        // the mapping says As.EXISTING_PROPERTY. Getting that wrong takes down every endpoint that touches an
        // exercise, submission, participation, lecture unit or competency, so it is worth a fixture.
        TextExercise exercise = new TextExercise();
        exercise.setTitle("Title");

        JsonNode json = jsonMapper.readTree(jsonMapper.writerFor(Exercise.class).writeValueAsString(exercise));

        assertThat(json.get("type").asString()).isEqualTo("text");
        assertThat(jsonMapper.writerFor(Exercise.class).writeValueAsString(exercise).split("\"type\"", -1)).hasSize(2);
    }

    @Test
    void shouldReadAnAuthorityFromItsNameAlone() {
        // A UserDTO carries authorities as plain strings. Jackson 2 treated Authority's lone String constructor as a
        // delegating creator; Jackson 3 reads the parameter name and would bind the string to a "name" property
        // instead, which broke every endpoint that accepts a User.
        assertThat(jsonMapper.readValue("\"ROLE_USER\"", Authority.class)).isEqualTo(new Authority("ROLE_USER"));
    }

    @Test
    void shouldFlattenAPolymorphicQuizQuestionOntoOneObject() {
        // The exam quiz DTOs are sealed hierarchies whose discriminator is the "type" the base projection already
        // writes (As.EXISTING_PROPERTY). Before the Jackson 3 migration the same payload came from a single record
        // that unwrapped three mutually exclusive branches; Jackson 3 rejects that because two branches declare the
        // same property name. This fixture is what says the replacement puts the identical object on the wire.
        var question = new ShortAnswerQuestion();
        question.setId(4L);
        question.setTitle("Title");
        question.setPoints(2.0);
        question.setScoringType(ScoringType.ALL_OR_NOTHING);
        question.setExplanation("why");

        // written the way a response body is: by runtime type, as an element of the exam's quizQuestions list
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(QuizQuestionWithSolutionDTO.of(question)));

        // one flat object: the shared fields, exactly one "type", the explanation, and no nested branch object
        assertThat(json.get("type").asString()).isEqualTo("short-answer");
        assertThat(json.get("id").asLong()).isEqualTo(4L);
        assertThat(json.get("title").asString()).isEqualTo("Title");
        assertThat(json.get("explanation").asString()).isEqualTo("why");
        assertThat(json.propertyNames()).doesNotContain("quizQuestionBaseDTO", "shortAnswerQuestionWithMappingDTO");
    }

    @Test
    void shouldBindExplicitNullForPrimitiveComponentsToZero() {
        String json = """
                {"count": null, "flag": null, "score": null}""";

        assertThat(jsonMapper.readValue(json, PrimitiveFixture.class)).isEqualTo(new PrimitiveFixture(0, false, 0.0));
    }

    @Test
    void shouldPopulateACollectionThatOnlyHasAGetter() {
        String json = """
                {"tags": ["a", "b"]}""";

        assertThat(jsonMapper.readValue(json, GetterOnlyCollectionFixture.class).getTags()).containsExactly("a", "b");
    }

    @Test
    void shouldSerializeAnEntityGraphWithUninitializedCollections() {
        Course created = courseUtilService.createCourse();
        // read the course back outside any transaction, so its collections are uninitialized Hibernate proxies and
        // serialization has to go through the Hibernate7Module registered on the mapper
        Course course = courseRepository.findById(created.getId()).orElseThrow();

        assertThatCode(() -> jsonMapper.writeValueAsString(course)).doesNotThrowAnyException();
        assertThat(jsonMapper.readTree(jsonMapper.writeValueAsString(course)).propertyNames()).contains("id", "title", "shortName").doesNotContain("exercises", "lectures");
    }

    @Test
    void shouldKeepRecordPropertiesInDeclarationOrderButSortEntityProperties() {
        // Jackson 3 enables SORT_PROPERTIES_ALPHABETICALLY, which Artemis accepts: it makes responses and exported
        // course archives byte-deterministic. Records are unaffected because every component is a creator property
        // and SORT_CREATOR_PROPERTIES_FIRST keeps those in declaration order. This test records that split so the
        // next person to see a reordered payload knows it was a decision rather than a regression.
        String recordJson = jsonMapper.writeValueAsString(new QuizBatchDTO(7L, null, true, false));
        assertThat(recordJson.indexOf("\"id\"")).isLessThan(recordJson.indexOf("\"started\""));

        Course course = new Course();
        course.setTitle("Title");
        course.setShortName("short");
        String entityJson = jsonMapper.writeValueAsString(course);
        assertThat(entityJson.indexOf("\"shortName\"")).isLessThan(entityJson.indexOf("\"title\""));
    }
}
