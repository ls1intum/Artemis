package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchWithPasswordDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.create.MultipleChoiceQuestionCreateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.create.QuizQuestionCreateDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Proves that the auto-configured Jackson 3 {@link JsonMapper} produces the same JSON as the Jackson 2
 * {@link ObjectMapper} that currently serves HTTP, so switching the web layer over cannot change the REST contract.
 * <p>
 * Jackson 3 changed a number of serialization defaults. {@link JacksonConfiguration} pins the ones that would be
 * visible on the wire; the fixtures below are one per pin, plus the structural cases that carry the most Jackson
 * configuration in Artemis: a {@code @JsonUnwrapped} response DTO, a polymorphic {@code @JsonTypeInfo} request DTO,
 * and an entity graph with uninitialized Hibernate collections.
 * <p>
 * This is also the tool for deciding whether a pin is still needed: remove one from
 * {@link JacksonConfiguration#artemisJacksonDefaultsCustomizer()} and the fixture that fails names the payload the
 * change would alter. Once Jackson 2 is gone from the application, the comparison against the second mapper drops
 * away and these fixtures become golden-JSON assertions.
 */
class JacksonMapperParityTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ObjectMapper jackson2Mapper;

    @Autowired
    private JsonMapper jackson3Mapper;

    // --- fixtures: one per pinned default, plus the near misses that turned out not to need one -----------------

    /** {@code WRITE_ENUMS_USING_TO_STRING}: both enums override toString() with a value that differs from name(). */
    record EnumFixture(RepositoryType repositoryType, Role role) {
    }

    /** Guards the date/time defaults that need no pin: Spring Boot already makes Jackson 2 write ISO-8601 too. */
    record DurationFixture(Duration duration) {
    }

    /** Year-months reach the API through the calendar module; java.time.Month and MonthDay never do. */
    record MonthFixture(YearMonth yearMonth) {
    }

    /** {@code STRIP_TRAILING_BIGDECIMAL_ZEROES}: Jackson 3 would keep the trailing zeroes. */
    record DecimalFixture(BigDecimal amount) {
    }

    /** {@code ADJUST_DATES_TO_CONTEXT_TIME_ZONE} and the date format Spring Boot configures. */
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
        ZonedDateTime instant = ZonedDateTime.of(2026, 3, 14, 15, 9, 26, 535000000, ZoneOffset.ofHours(2));
        return Stream.of(Arguments.of("enums overriding toString", new EnumFixture(RepositoryType.SOLUTION, Role.ADMIN)),
                Arguments.of("durations", new DurationFixture(Duration.ofMinutes(90).plusSeconds(30))), Arguments.of("year-months", new MonthFixture(YearMonth.of(2026, 3))),
                Arguments.of("big decimals", new DecimalFixture(new BigDecimal("12.500"))), Arguments.of("zoned date times", new DateFixture(instant)),
                Arguments.of("primitive components", new PrimitiveFixture(3, true, 4.5)),
                Arguments.of("unwrapped response DTO", new QuizBatchWithPasswordDTO(new QuizBatchDTO(7L, instant, true, false), "secret")));
    }

    /**
     * Compares the two payloads as parsed trees rather than as strings, because two differences between the mappers
     * are accepted rather than pinned: Jackson 3 sorts the properties of a non-record type alphabetically, and the
     * indentation the test profile asks for currently only reaches the Jackson 3 mapper. Neither changes what a
     * client reads. Everything a tree comparison still catches — a renamed property, a different value, a different
     * JSON type, a property that appears or disappears — is exactly what the pinned defaults exist to prevent.
     *
     * @param description  what the fixture covers, for the assertion message
     * @param jackson3Json the payload produced by the Jackson 3 mapper
     * @param jackson2Json the payload produced by the Jackson 2 mapper
     */
    private void assertSamePayload(String description, String jackson3Json, String jackson2Json) {
        assertThat(jackson3Mapper.readTree(jackson3Json)).as("Jackson 3 payload differs for %s", description).isEqualTo(jackson3Mapper.readTree(jackson2Json));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serializationFixtures")
    void shouldSerializeIdenticallyOnBothMappers(String description, Object fixture) throws JsonProcessingException {
        assertSamePayload(description, jackson3Mapper.writeValueAsString(fixture), jackson2Mapper.writeValueAsString(fixture));
    }

    @Test
    void shouldSerializePolymorphicDtoIdentically() throws JsonProcessingException {
        QuizQuestionCreateDTO question = new MultipleChoiceQuestionCreateDTO("Title", "Text", "Hint", "Explanation", 2.0, ScoringType.ALL_OR_NOTHING, false, List.of(), true);

        // the static type has to be the sealed interface, otherwise the @JsonTypeInfo discriminator is not written
        String jackson3Json = jackson3Mapper.writerFor(QuizQuestionCreateDTO.class).writeValueAsString(question);
        String jackson2Json = jackson2Mapper.writerFor(QuizQuestionCreateDTO.class).writeValueAsString(question);

        assertSamePayload("polymorphic create DTO", jackson3Json, jackson2Json);
        assertThat(jackson3Mapper.readTree(jackson3Json).get("type").asString()).isEqualTo("multiple-choice");
    }

    @Test
    void shouldKeepRecordPropertiesInDeclarationOrderButSortEntityProperties() {
        // Jackson 3 enables SORT_PROPERTIES_ALPHABETICALLY, which Artemis accepts: it makes responses and exported
        // course archives byte-deterministic. Records are unaffected because every component is a creator property
        // and SORT_CREATOR_PROPERTIES_FIRST keeps those in declaration order. This test records that split so the
        // next person to see a reordered payload knows it was a decision rather than a regression.
        String recordJson = jackson3Mapper.writeValueAsString(new QuizBatchDTO(7L, null, true, false));
        assertThat(recordJson.indexOf("\"id\"")).isLessThan(recordJson.indexOf("\"started\""));

        Course course = new Course();
        course.setTitle("Title");
        course.setShortName("short");
        String entityJson = jackson3Mapper.writeValueAsString(course);
        assertThat(entityJson.indexOf("\"shortName\"")).isLessThan(entityJson.indexOf("\"title\""));
    }

    @Test
    void shouldBindExplicitNullForPrimitiveComponentsToZero() throws JsonProcessingException {
        String json = """
                {"count": null, "flag": null, "score": null}""";

        PrimitiveFixture jackson3Value = jackson3Mapper.readValue(json, PrimitiveFixture.class);

        assertThat(jackson3Value).isEqualTo(jackson2Mapper.readValue(json, PrimitiveFixture.class)).isEqualTo(new PrimitiveFixture(0, false, 0.0));
    }

    @Test
    void shouldPopulateACollectionThatOnlyHasAGetter() throws JsonProcessingException {
        String json = """
                {"tags": ["a", "b"]}""";

        GetterOnlyCollectionFixture jackson3Value = jackson3Mapper.readValue(json, GetterOnlyCollectionFixture.class);

        assertThat(jackson3Value.getTags()).containsExactly("a", "b").isEqualTo(jackson2Mapper.readValue(json, GetterOnlyCollectionFixture.class).getTags());
    }

    @Test
    void shouldSerializeAnEntityGraphWithUninitializedCollectionsIdentically() throws JsonProcessingException {
        Course created = courseUtilService.createCourse();
        // read the course back outside any transaction, so its collections are uninitialized Hibernate proxies and
        // serialization has to go through the Hibernate7Module registered on each mapper
        Course course = courseRepository.findById(created.getId()).orElseThrow();

        assertSamePayload("course entity graph", jackson3Mapper.writeValueAsString(course), jackson2Mapper.writeValueAsString(course));
    }
}
