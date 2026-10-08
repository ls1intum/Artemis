package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Importing a competency together with its exercises runs the programming exercise import for a programming exercise. The
 * import only logs an exercise that fails, so these tests look at the database instead of at the response: the exercise has
 * to exist in the target course and carry the settings of its source, which it can only do if the source's stored team and
 * plagiarism settings were read before the import copied them.
 */
class CompetencyImportProgrammingExerciseIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "competencyimportprogramming";

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    @Autowired
    private TeamAssignmentConfigRepository teamAssignmentConfigRepository;

    @Autowired
    private CompetencyExerciseLinkTestRepository competencyExerciseLinkRepository;

    @Autowired
    private PlagiarismDetectionConfigRepository plagiarismDetectionConfigRepository;

    private Course sourceCourse;

    private Course targetCourse;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        sourceCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        targetCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importingATeamProgrammingExerciseKeepsItsTeamAndPlagiarismSettings() throws Exception {
        ProgrammingExercise source = programmingExerciseUtilService.addProgrammingExerciseToCourse(sourceCourse);
        source.setMode(ExerciseMode.TEAM);
        source = programmingExerciseTestRepository.save(source);
        var team = new TeamAssignmentConfig();
        team.setMinTeamSize(3);
        team.setMaxTeamSize(4);
        exerciseUtilService.saveTeamAssignmentConfig(source, team);
        var plagiarism = PlagiarismDetectionConfig.createDefault();
        plagiarism.setSimilarityThreshold(42);
        exerciseUtilService.savePlagiarismDetectionConfig(source, plagiarism);
        Competency competency = competencyUtilService.createCompetency(sourceCourse, "");
        competencyExerciseLinkRepository.save(new CompetencyExerciseLink(competency, source, 1));

        var options = new CompetencyImportOptionsDTO(Set.of(competency.getId()), Optional.empty(), false, true, false, Optional.empty(), false);
        request.postWithResponseBody("/api/atlas/courses/" + targetCourse.getId() + "/competencies/import", options, Competency.class, HttpStatus.CREATED);

        var imported = programmingExerciseTestRepository.findAllByCourseId(targetCourse.getId());
        assertThat(imported).as("the exercise was imported into the target course").hasSize(1);
        long importedId = imported.getFirst().getId();
        assertThat(imported.getFirst().getMode()).isEqualTo(ExerciseMode.TEAM);
        var importedTeam = teamAssignmentConfigRepository.findByExerciseId(importedId).orElseThrow();
        assertThat(importedTeam.getMinTeamSize()).isEqualTo(3);
        assertThat(importedTeam.getMaxTeamSize()).isEqualTo(4);
        var importedPlagiarism = plagiarismDetectionConfigRepository.findByExerciseId(importedId).orElseThrow();
        assertThat(importedPlagiarism.getSimilarityThreshold()).isEqualTo(42);
        exerciseUtilService.assertHasPermanentConfigurations(importedId);
    }
}
