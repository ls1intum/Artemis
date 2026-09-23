package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * A stored snapshot outlives the shape of the record that wrote it: a field dropped from the record stays in every row
 * written before the change. Reading such a row has to keep working, because a version is read both to write the next
 * one and to show the history, and neither can be repaired by a later release.
 */
class ExerciseVersionSnapshotCompatibilityTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "versionsnapshot";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseVersionService exerciseVersionService;

    @Autowired
    private ExerciseVersionTestRepository exerciseVersionTestRepository;

    private TextExercise textExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        textExercise = (TextExercise) course.getExercises().iterator().next();
        exerciseVersionService.createExerciseVersion(textExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void keepsVersioningAnExerciseWhoseNewestSnapshotIsOfAnOlderShape() {
        ExerciseVersion version = exerciseVersionTestRepository.findTopByExerciseIdOrderByCreatedDateDesc(textExercise.getId()).orElseThrow();
        storeLegacySnapshot(version.getId());

        // Writing a version reads the newest one first, so an unreadable snapshot stops the exercise from ever being
        // versioned again. The failure is swallowed on an async executor, which is why this asserts on the rows.
        exerciseVersionService.createExerciseVersion(textExercise);

        assertThat(exerciseVersionTestRepository.findAllByExerciseId(textExercise.getId())).hasSize(2);
        ExerciseSnapshotDTO stored = exerciseVersionTestRepository.findByIdElseThrow(version.getId()).getExerciseSnapshot();
        assertThat(stored.title()).isEqualTo("Legacy title");
        assertThat(stored.teamAssignmentConfig().maxTeamSize()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void answersTheVersionHistoryForASnapshotOfAnOlderShape() throws Exception {
        ExerciseVersion version = exerciseVersionTestRepository.findTopByExerciseIdOrderByCreatedDateDesc(textExercise.getId()).orElseThrow();
        storeLegacySnapshot(version.getId());

        // The request that answered with 500 while the snapshot could not be read.
        ExerciseSnapshotDTO snapshot = request.get("/api/exercise/exercises/" + textExercise.getId() + "/versions/" + version.getId(), HttpStatus.OK, ExerciseSnapshotDTO.class);

        assertThat(snapshot.title()).isEqualTo("Legacy title");
    }

    /**
     * Writes a snapshot of an older shape. It carries {@code allowFeedbackRequests}, which really did sit on the
     * exercise until Athena's feedback configuration moved to the course, and an unknown key on a nested record, which
     * stands for the next such removal at depth. The record writes only what it declares today, so a snapshot of an
     * older shape can only be put into the column as raw json.
     *
     * @param exerciseVersionId the version whose snapshot is replaced
     */
    private void storeLegacySnapshot(long exerciseVersionId) {
        exerciseVersionTestRepository.overwriteSnapshot(exerciseVersionId, """
                {
                  "id": %d,
                  "title": "Legacy title",
                  "allowFeedbackRequests": true,
                  "teamAssignmentConfig": {"id": 1, "minTeamSize": 2, "maxTeamSize": 3, "assignmentStrategy": "RANDOM"}
                }
                """.formatted(textExercise.getId()));
    }
}
