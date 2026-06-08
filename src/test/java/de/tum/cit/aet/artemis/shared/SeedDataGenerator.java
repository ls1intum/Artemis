package de.tum.cit.aet.artemis.shared;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Manually-run generator (NOT part of CI) that builds a text-exercise data graph on the shared seed course via the
 * existing util services — guaranteeing schema-correctness — and writes it out as Liquibase {@code loadData} CSV files
 * with ids remapped into the seed range (via {@link #OFFSET}). Re-run it whenever the schema changes; it overwrites the
 * generated CSVs in place. If a previously generated CSV is invalid (so the seed fails to load), temporarily remove the
 * {@code seed-text-*} changesets before regenerating, since this generator's own context also loads the seed.
 * <p>
 * Run with: {@code ./gradlew test -x webapp --tests 'SeedDataGenerator' with SEED_GENERATE=true in the environment}. It is gated on the SEED_GENERATE environment variable so it
 * never runs in CI.
 */
@EnabledIfEnvironmentVariable(named = "SEED_GENERATE", matches = "true")
class SeedDataGenerator extends AbstractSpringIntegrationIndependentTest {

    private static final long OFFSET = 9_000_000L;

    private static final Path SEED_DIR = Path.of("src/main/resources/config/liquibase/e2e");

    // Optional 1:1 config rows the exercise factory auto-creates but that we do not seed; null the FK so the seed is self-contained.
    private static final Set<String> NULL_COLUMNS = Set.of("plagiarism_detection_config_id");

    // Enum/flag columns the util leaves null but which Liquibase loadData would insert as "" (failing enum mapping on
    // read). Force a valid value so the seed represents a finished, manually assessed submission. Keyed by column name.
    private static final Map<String, String> DEFAULT_VALUES = Map.of("assessment_type", "MANUAL", "rated", "true", "initialization_state", "FINISHED", "jhi_type", "MANUAL");

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void generateTextExerciseSeed() throws IOException {
        Course course = courseRepository.findById(SeedData.COURSE_CHANNEL_1_ID).orElseThrow();
        // The seed course has a null presentation score; the exercise factory dereferences it, so default it here.
        course.setPresentationScore(0);
        ZonedDateTime now = ZonedDateTime.now();
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, now.minusDays(7), now.minusDays(2), now.minusDays(1));

        // One participation + submission + assessed result + manual feedback per seed student.
        for (int index : List.of(1, 2, 3)) {
            TextSubmission submission = ParticipationFactory.generateTextSubmission("Seed submission for student " + index, Language.ENGLISH, true);
            textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(exercise, submission, SeedData.userLogin(index), SeedData.TUTOR_LOGIN,
                    ParticipationFactory.generateManualFeedback());
        }

        long exerciseId = exercise.getId();
        dump("exercises.csv", "exercise", "id = " + exerciseId, Set.of("id"));
        dump("participations.csv", "participation", "exercise_id = " + exerciseId, Set.of("id", "exercise_id"));
        dump("submissions.csv", "submission", "participation_id IN (SELECT id FROM participation WHERE exercise_id = " + exerciseId + ")", Set.of("id", "participation_id"));
        dump("results.csv", "result", "exercise_id = " + exerciseId, Set.of("id", "submission_id", "exercise_id"));
        dump("feedback.csv", "feedback", "result_id IN (SELECT id FROM result WHERE exercise_id = " + exerciseId + ")", Set.of("id", "result_id"));
    }

    /**
     * Writes the rows matching the given {@code where} clause from {@code table} into {@code SEED_DIR/fileName} as a
     * {@code ;}-separated Liquibase CSV (header + rows), remapping the given columns into the seed id range.
     */
    private void dump(String fileName, String table, String where, Set<String> offsetColumns) throws IOException {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + table + " WHERE " + where + " ORDER BY id");
        if (rows.isEmpty()) {
            return;
        }
        List<String> columns = new ArrayList<>(rows.getFirst().keySet());
        List<String> lines = new ArrayList<>();
        lines.add(String.join(";", columns));
        for (Map<String, Object> row : rows) {
            lines.add(columns.stream().map(column -> format(column, row.get(column), offsetColumns)).collect(Collectors.joining(";")));
        }
        Files.write(SEED_DIR.resolve(fileName), String.join("\n", lines).concat("\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String format(String column, Object value, Set<String> offsetColumns) {
        if (NULL_COLUMNS.contains(column)) {
            return "";
        }
        if (value == null) {
            return DEFAULT_VALUES.getOrDefault(column, "");
        }
        if (offsetColumns.contains(column) && value instanceof Number number) {
            return String.valueOf(number.longValue() + OFFSET);
        }
        // Liquibase loadData parses dates in ISO-8601 (with a 'T'); Timestamp#toString uses a space, so normalize.
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        return value.toString();
    }
}
