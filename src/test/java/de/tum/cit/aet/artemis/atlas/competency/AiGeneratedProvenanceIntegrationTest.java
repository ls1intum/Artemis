package de.tum.cit.aet.artemis.atlas.competency;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@Transactional
class AiGeneratedProvenanceIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final Set<String> PROVENANCE_TABLES = Set.of("competency", "competency_exercise", "competency_lecture_unit");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EntityManager entityManager;

    @Test
    void migrationCreatesNonNullableFalseDefaults() throws SQLException {
        Map<String, ColumnMetadata> columns = new HashMap<>();
        try (var connection = dataSource.getConnection(); var resultSet = connection.getMetaData().getColumns(connection.getCatalog(), null, "%", "%")) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                String columnName = resultSet.getString("COLUMN_NAME");
                if (PROVENANCE_TABLES.contains(tableName) && "generated_by_ai".equalsIgnoreCase(columnName)) {
                    columns.put(tableName, new ColumnMetadata(resultSet.getInt("NULLABLE"), resultSet.getString("COLUMN_DEF")));
                }
            }
        }

        assertThat(columns).containsOnlyKeys(PROVENANCE_TABLES);
        columns.values().forEach(column -> {
            assertThat(column.nullability()).isEqualTo(DatabaseMetaData.columnNoNulls);
            assertThat(column.defaultValue()).isNotNull();
            String normalizedDefault = column.defaultValue().toLowerCase(Locale.ROOT);
            assertThat(normalizedDefault.contains("false") || normalizedDefault.contains("0")).isTrue();
        });
    }

    @Test
    void persistsManualAndAiProvenanceForCompetenciesAndLinks() {
        ProvenanceFixture manual = persistFixture(false, "manual");
        ProvenanceFixture ai = persistFixture(true, "ai");
        entityManager.flush();
        entityManager.clear();

        assertFixtureProvenance(manual, false);
        assertFixtureProvenance(ai, true);
    }

    private ProvenanceFixture persistFixture(boolean generatedByAi, String suffix) {
        Course course = courseUtilService.createCourse();
        Competency competency = competencyUtilService.createCompetency(course, suffix);
        competency.setGeneratedByAi(generatedByAi);
        competency = competencyRepository.save(competency);

        TextExercise exercise = textExerciseUtilService.createSampleTextExercise(course);
        CompetencyExerciseLink exerciseLink = new CompetencyExerciseLink(competency, exercise, 0.5);
        exerciseLink.setGeneratedByAi(generatedByAi);
        competencyExerciseLinkRepository.save(exerciseLink);

        Lecture lecture = lectureUtilService.createLecture(course);
        TextUnit textUnit = lectureUtilService.createTextUnit(lecture);
        CompetencyLectureUnitLink lectureUnitLink = new CompetencyLectureUnitLink(competency, textUnit, 1.0);
        lectureUnitLink.setGeneratedByAi(generatedByAi);
        competencyLectureUnitLinkRepository.save(lectureUnitLink);

        return new ProvenanceFixture(competency.getId(), exercise.getId(), textUnit.getId());
    }

    private void assertFixtureProvenance(ProvenanceFixture fixture, boolean expected) {
        assertThat(courseCompetencyRepository.findByIdElseThrow(fixture.competencyId()).isGeneratedByAi()).isEqualTo(expected);
        assertThat(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(fixture.exerciseId(), fixture.competencyId())).get()
                .extracting(CompetencyExerciseLink::isGeneratedByAi).isEqualTo(expected);
        assertThat(competencyLectureUnitLinkRepository.findAll()).filteredOn(link -> link.getLectureUnit().getId().equals(fixture.lectureUnitId()))
                .filteredOn(link -> link.getCompetency().getId().equals(fixture.competencyId())).singleElement().extracting(CompetencyLectureUnitLink::isGeneratedByAi)
                .isEqualTo(expected);
    }

    private record ColumnMetadata(int nullability, String defaultValue) {
    }

    private record ProvenanceFixture(long competencyId, long exerciseId, long lectureUnitId) {
    }
}
