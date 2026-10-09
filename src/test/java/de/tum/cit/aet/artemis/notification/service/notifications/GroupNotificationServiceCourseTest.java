package de.tum.cit.aet.artemis.notification.service.notifications;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class GroupNotificationServiceCourseTest {

    private final UserTestRepository userRepository = mock(UserTestRepository.class);

    private final CourseNotificationService courseNotificationService = mock(CourseNotificationService.class);

    private final GroupNotificationService groupNotificationService = new GroupNotificationService(userRepository, courseNotificationService);

    private void assertFailsWithoutCourse(Consumer<ProgrammingExercise> call) {
        var exercise = new ProgrammingExercise();
        exercise.setId(11L);

        assertThatThrownBy(() -> call.accept(exercise)).isInstanceOf(IllegalStateException.class).hasMessageContaining("11");
        verifyNoInteractions(userRepository, courseNotificationService);
    }

    @Test
    void notifyAboutChangedTestCases_withoutCourse_throws() {
        assertFailsWithoutCourse(groupNotificationService::notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise);
    }

    @Test
    void notifyAboutBuildRunUpdate_withoutCourse_throws() {
        assertFailsWithoutCourse(groupNotificationService::notifyEditorAndInstructorGroupsAboutBuildRunUpdate);
    }

    @Test
    void notifyAboutDuplicateTestCases_withoutCourse_throws() {
        Consumer<Exercise> call = groupNotificationService::notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise;
        assertFailsWithoutCourse(call::accept);
    }
}
