package de.tum.cit.aet.artemis.course.service.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.service.CourseHomeCommunicationService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.exam.api.ExamCourseHomeApi;
import de.tum.cit.aet.artemis.iris.api.IrisCourseHomeApi;
import de.tum.cit.aet.artemis.lecture.api.LectureCourseHomeApi;

class CourseHomeSectionsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private final User user = new User();

    private final CourseHomeCommunicationService discussions = mock(CourseHomeCommunicationService.class);

    private final LectureCourseHomeApi lectures = mock(LectureCourseHomeApi.class);

    private final ExamCourseHomeApi exams = mock(ExamCourseHomeApi.class);

    private final IrisCourseHomeApi iris = mock(IrisCourseHomeApi.class);

    private final CourseHomeSectionsService service = new CourseHomeSectionsService(discussions, Optional.of(lectures), Optional.of(exams), Optional.of(iris));

    @Test
    void sectionDatabaseFailuresStayLocalAndNeverBecomeKnownEmptyContent() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(lectures.load(42, user, NOW, NOW)).thenThrow(failure);
        when(exams.load(42, user, NOW, NOW)).thenThrow(failure);
        when(discussions.load(42, user, NOW, NOW)).thenThrow(failure);
        when(iris.load(42, user, NOW, NOW)).thenThrow(failure);
        var lectureResult = service.lectures(42, user, NOW, NOW);
        var examResult = service.exams(42, user, NOW, NOW);
        var discussionResult = service.discussions(42, user, NOW, NOW);
        var irisResult = service.chats(42, user, NOW, NOW, true);
        assertThat(lectureResult.status()).isEqualTo(CourseHomeSectionStatus.UNAVAILABLE);
        assertThat(lectureResult.items()).isEmpty();
        assertThat(examResult.status()).isEqualTo(CourseHomeSectionStatus.UNAVAILABLE);
        assertThat(examResult.items()).isEmpty();
        assertThat(discussionResult.questions().status()).isEqualTo(CourseHomeSectionStatus.UNAVAILABLE);
        assertThat(discussionResult.questions().items()).isEmpty();
        assertThat(discussionResult.announcement()).isNull();
        assertThat(discussionResult.faqs()).isEmpty();
        assertThat(irisResult.status()).isEqualTo(CourseHomeSectionStatus.UNAVAILABLE);
        assertThat(irisResult.items()).isEmpty();
    }

    @Test
    void disabledIrisDoesNotLoadChatMetadata() {
        assertThat(service.chats(42, user, NOW, NOW, false).status()).isEqualTo(CourseHomeSectionStatus.DISABLED);
        verifyNoInteractions(iris);
    }

    @Test
    void anAuthorizationFailureIsNeverDisguisedAsAnOptionalOutage() {
        when(lectures.load(42, user, NOW, NOW)).thenThrow(new AccessForbiddenException("course", 42L));
        assertThatThrownBy(() -> service.lectures(42, user, NOW, NOW)).isInstanceOf(AccessForbiddenException.class);
    }
}
