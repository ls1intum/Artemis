package de.tum.cit.aet.artemis.course.service.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeCommunicationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.communication.service.CourseHomeCommunicationService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeCase;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeLearningDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeQueryDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTargetDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeTeachingTeamDTO;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.repository.CourseHomeRepository;
import de.tum.cit.aet.artemis.course.service.CourseAvailableTabsService;
import de.tum.cit.aet.artemis.lecture.api.LectureCourseHomeApi;

class CourseHomeServiceTest {

    private static final long COURSE = 42;

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private final CourseHomeRepository courses = mock(CourseHomeRepository.class);

    private final CourseConfigurationRepository configurations = mock(CourseConfigurationRepository.class);

    private final CourseHomeVisitService visits = mock(CourseHomeVisitService.class);

    private final CourseHomeLearningService learning = mock(CourseHomeLearningService.class);

    private final CourseHomeTeachingTeamService team = mock(CourseHomeTeachingTeamService.class);

    private final CourseHomeCommunicationService communication = mock(CourseHomeCommunicationService.class);

    private final CourseAvailableTabsService tabs = mock(CourseAvailableTabsService.class);

    private final AuthorizationCheckService authorization = mock(AuthorizationCheckService.class);

    private final User user = new User();

    @BeforeEach
    void setup() {
        user.setId(7L);
        var header = mock(CourseHomeRepository.Header.class);
        when(header.getId()).thenReturn(COURSE);
        when(header.getTitle()).thenReturn("Course");
        when(header.getCourseInformationSharingConfiguration()).thenReturn(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        when(courses.findHeaderById(COURSE)).thenReturn(Optional.of(header));
        when(authorization.isAtLeastStudentInCourse(COURSE)).thenReturn(true);
        when(configurations.findStudentWelcomeContextByCourseId(COURSE)).thenReturn(Optional.empty());
        when(visits.read(COURSE, user.getId())).thenReturn(new CourseHomeVisitService.VisitState(null, null, 0));
        when(learning.load(anyLong(), any(), any(), any())).thenReturn(new CourseHomeLearningDTO(new CourseHomeDTO.Progress(0, 0, 0, 0), List.of(), List.of()));
        when(team.load(COURSE, 0, 3)).thenReturn(new CourseHomeTeachingTeamDTO(List.of(), false, 0));
        when(communication.load(anyLong(), any(), any(), any()))
                .thenReturn(new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(CourseHomeDTO.SectionStatus.AVAILABLE, List.of()), null, List.of()));
        when(tabs.getAvailableTabs(COURSE, user, CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, false))
                .thenReturn(new CourseAvailableTabsDTO(false, false, false, false, false, false, false, true, false));
    }

    @Test
    void absentModulesHaveExplicitDisabledSections() {
        var result = service(Optional.empty()).load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(result.sections().get("lectures")).isEqualTo(CourseHomeDTO.SectionStatus.DISABLED);
        assertThat(result.sections().get("exams")).isEqualTo(CourseHomeDTO.SectionStatus.DISABLED);
        assertThat(result.irisSessions().status()).isEqualTo(CourseHomeDTO.SectionStatus.DISABLED);
    }

    @Test
    void anOptionalDatabaseFailureLeavesTheCoreUsable() {
        var api = mock(LectureCourseHomeApi.class);
        when(api.load(anyLong(), any(), any(), any())).thenThrow(new DataAccessResourceFailureException("offline"));
        var result = service(Optional.of(api)).load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(result.sections().get("lectures")).isEqualTo(CourseHomeDTO.SectionStatus.UNAVAILABLE);
        assertThat(result.welcome()).isNotNull();
        assertThat(result.course().id()).isEqualTo(COURSE);
    }

    @Test
    void optionalAuthorizationErrorsAreNotHidden() {
        var api = mock(LectureCourseHomeApi.class);
        when(api.load(anyLong(), any(), any(), any())).thenThrow(new AccessForbiddenException("course", COURSE));
        assertThatThrownBy(() -> service(Optional.of(api)).load(COURSE, user, new CourseHomeQueryDTO(null), NOW)).isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void visitAcknowledgmentDoesNotChangeTheMeaningfulFingerprint() {
        var home = service(Optional.empty());
        var first = home.load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        when(visits.read(COURSE, user.getId())).thenReturn(new CourseHomeVisitService.VisitState(NOW, NOW, 1));
        var next = home.load(COURSE, user, new CourseHomeQueryDTO(first.visit().newSince()), NOW.plusSeconds(1));
        assertThat(next.welcome().contextFingerprint()).isEqualTo(first.welcome().contextFingerprint());
        assertThat(next.welcome().variantIndex()).isNotEqualTo(first.welcome().variantIndex());
    }

    @Test
    void aNonprimaryDeadlineStillSchedulesTheUrgencyRefresh() {
        var due = NOW.plusSeconds(25 * 3600);
        var item = new CourseHomeDTO.Item("Exercise", new CourseHomeTargetDTO(CourseHomeTargetDTO.Kind.EXERCISE, 12, null, null), NOW, due, "DEADLINE_SOON");
        when(learning.load(anyLong(), any(), any(), any())).thenReturn(new CourseHomeLearningDTO(new CourseHomeDTO.Progress(1, 0, 1, 0),
                List.of(new CourseHomeLearningDTO.Candidate(CourseHomeWelcomeCase.DEADLINE_SOON, item, true)), List.of()));
        var question = new CourseHomeQuestionsDTO.Item(19, 5, "Private question", 1, CourseHomeQuestionsDTO.State.ANSWERED, 1L, NOW, 20L);
        when(communication.load(anyLong(), any(), any(), any()))
                .thenReturn(new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(CourseHomeDTO.SectionStatus.AVAILABLE, List.of(question)), null, List.of()));
        var home = service(Optional.empty());
        var result = home.load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(result.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.UNREAD_REPLY);
        assertThat(result.nextRefreshAt()).isEqualTo(due.minusSeconds(24 * 3600));
        var boundary = home.load(COURSE, user, new CourseHomeQueryDTO(result.visit().newSince()), due.minusSeconds(24 * 3600));
        assertThat(boundary.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.DEADLINE_TODAY);
        assertThat(boundary.welcome().contextFingerprint()).isNotEqualTo(result.welcome().contextFingerprint());
    }

    @Test
    void fingerprintFramesEachCourseFieldSeparately() {
        var header = courses.findHeaderById(COURSE).orElseThrow();
        when(header.getTitle()).thenReturn("Alpha, description=Beta");
        when(header.getDescription()).thenReturn("Gamma");
        var home = service(Optional.empty());
        var first = home.load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        when(header.getTitle()).thenReturn("Alpha");
        when(header.getDescription()).thenReturn("Beta, description=Gamma");
        var changed = home.load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(changed.welcome().contextFingerprint()).isNotEqualTo(first.welcome().contextFingerprint());
    }

    @Test
    void missingCoursesHaveADefinedNotFoundResponse() {
        when(courses.findHeaderById(COURSE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(Optional.empty()).load(COURSE, user, new CourseHomeQueryDTO(null), NOW))
                .isInstanceOf(de.tum.cit.aet.artemis.core.exception.EntityNotFoundException.class);
    }

    private CourseHomeService service(Optional<LectureCourseHomeApi> lectures) {
        return new CourseHomeService(courses, configurations, visits, learning, team, communication, tabs, new CourseHomeContextService(), authorization, lectures,
                Optional.empty(), Optional.empty());
    }
}
