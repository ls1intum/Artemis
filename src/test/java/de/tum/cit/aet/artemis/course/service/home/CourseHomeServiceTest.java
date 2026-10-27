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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.CourseHomeQuestionState;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeCommunicationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.communication.service.CourseHomeCommunicationService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.course.domain.CourseHomeTargetKind;
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
import de.tum.cit.aet.artemis.iris.dto.IrisCourseHomeSessionsDTO;
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
        when(learning.load(anyLong(), any(), any(), any())).thenReturn(new CourseHomeLearningDTO(new CourseHomeDTO.ProgressDTO(0, 0, 0, 0), List.of(), List.of()));
        when(team.load(COURSE, 0, 3)).thenReturn(new CourseHomeTeachingTeamDTO(List.of(), false, 0));
        when(communication.load(anyLong(), any(), any(), any()))
                .thenReturn(new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(CourseHomeSectionStatus.AVAILABLE, List.of()), null, List.of()));
        when(tabs.getAvailableTabs(COURSE, user, CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING, false))
                .thenReturn(new CourseAvailableTabsDTO(false, false, false, false, false, false, false, true, false));
    }

    @Test
    void absentModulesHaveExplicitDisabledSections() {
        var result = service(Optional.empty()).load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(result.sections().get("lectures")).isEqualTo(CourseHomeSectionStatus.DISABLED);
        assertThat(result.sections().get("exams")).isEqualTo(CourseHomeSectionStatus.DISABLED);
        assertThat(result.irisSessions().status()).isEqualTo(CourseHomeSectionStatus.DISABLED);
    }

    @ParameterizedTest
    @ValueSource(ints = { -60, 0, 60 })
    void onlyAnnouncementsNewerThanTheBaselineBecomeTheNextStep(int secondsFromBaseline) {
        Instant baseline = NOW.minusSeconds(600);
        when(visits.read(COURSE, user.getId())).thenReturn(new CourseHomeVisitService.VisitState(baseline, baseline, 1));
        var announcement = new CourseHomeDTO.ItemDTO("Pinned course information", new CourseHomeTargetDTO(CourseHomeTargetKind.THREAD, 10L, 11L, null),
                baseline.plusSeconds(secondsFromBaseline), null, "IMPORTANT_ANNOUNCEMENT");
        when(communication.load(anyLong(), any(), any(), any()))
                .thenReturn(new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(CourseHomeSectionStatus.AVAILABLE, List.of()), announcement, List.of()));
        var result = service(Optional.empty()).load(COURSE, user, new CourseHomeQueryDTO(baseline), NOW);
        assertThat(result.announcement()).isEqualTo(announcement);
        if (secondsFromBaseline > 0) {
            assertThat(result.nextStep()).isEqualTo(announcement);
            assertThat(result.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.IMPORTANT_ANNOUNCEMENT);
        }
        else {
            assertThat(result.nextStep()).isNull();
            assertThat(result.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.CAUGHT_UP);
        }
    }

    @Test
    void anOptionalDatabaseFailureLeavesTheCoreUsable() {
        var api = mock(LectureCourseHomeApi.class);
        when(api.load(anyLong(), any(), any(), any())).thenThrow(new DataAccessResourceFailureException("offline"));
        var result = service(Optional.of(api)).load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(result.sections().get("lectures")).isEqualTo(CourseHomeSectionStatus.UNAVAILABLE);
        assertThat(result.welcome()).isNotNull();
        assertThat(result.course().id()).isEqualTo(COURSE);
    }

    @Test
    void unrelatedChatHistoryIsNotPromotedToThePrimaryLearningStep() {
        var sections = mock(CourseHomeSectionsService.class);
        when(sections.lectures(anyLong(), any(), any(), any()))
                .thenReturn(new de.tum.cit.aet.artemis.lecture.dto.LectureCourseHomeDTO(CourseHomeSectionStatus.DISABLED, List.of()));
        when(sections.exams(anyLong(), any(), any(), any())).thenReturn(new de.tum.cit.aet.artemis.exam.dto.ExamCourseHomeDTO(CourseHomeSectionStatus.DISABLED, List.of()));
        when(sections.discussions(anyLong(), any(), any(), any()))
                .thenReturn(new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(CourseHomeSectionStatus.AVAILABLE, List.of()), null, List.of()));
        var history = new IrisCourseHomeSessionsDTO(CourseHomeSectionStatus.AVAILABLE,
                List.of(new IrisCourseHomeSessionsDTO.ItemDTO(10L, "Weather question", "COURSE", COURSE, "Course", NOW.minusSeconds(3600))));
        when(sections.chats(anyLong(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(history);
        var home = new CourseHomeService(courses, configurations, visits, learning, team, tabs, new CourseHomeContextService(), authorization, sections,
                mock(CourseHomeAiPolicyService.class));

        var firstVisit = home.load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(firstVisit.nextStep()).isNull();
        assertThat(firstVisit.irisSessions()).isEqualTo(history);
        when(visits.read(COURSE, user.getId())).thenReturn(new CourseHomeVisitService.VisitState(NOW, NOW, 1));
        var returning = home.load(COURSE, user, new CourseHomeQueryDTO(null), NOW.plusSeconds(60));
        assertThat(returning.nextStep()).isNull();
        assertThat(returning.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.CAUGHT_UP);
        assertThat(returning.irisSessions()).isEqualTo(history);
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
        var item = new CourseHomeDTO.ItemDTO("Exercise", new CourseHomeTargetDTO(CourseHomeTargetKind.EXERCISE, 12, null, null), NOW, due, "DEADLINE_SOON");
        when(learning.load(anyLong(), any(), any(), any())).thenReturn(new CourseHomeLearningDTO(new CourseHomeDTO.ProgressDTO(1, 0, 1, 0),
                List.of(new CourseHomeLearningDTO.CandidateDTO(CourseHomeWelcomeCase.DEADLINE_SOON, item, true)), List.of()));
        var question = new CourseHomeQuestionsDTO.ItemDTO(19, 5, "Private question", 1, CourseHomeQuestionState.ANSWERED, 1L, NOW, 20L);
        when(communication.load(anyLong(), any(), any(), any()))
                .thenReturn(new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(CourseHomeSectionStatus.AVAILABLE, List.of(question)), null, List.of()));
        var home = service(Optional.empty());
        var result = home.load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(result.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.UNREAD_REPLY);
        assertThat(result.nextRefreshAt()).isEqualTo(due.minusSeconds(24 * 3600));
        var boundary = home.load(COURSE, user, new CourseHomeQueryDTO(result.visit().newSince()), due.minusSeconds(24 * 3600));
        assertThat(boundary.welcome().welcomeCase()).isEqualTo(CourseHomeWelcomeCase.DEADLINE_TODAY);
        assertThat(boundary.welcome().contextFingerprint()).isNotEqualTo(result.welcome().contextFingerprint());
    }

    @Test
    void schedulesFutureEligibilityIndependentlyOfDisplayedCandidates() {
        var firstChange = NOW.plusSeconds(30);
        when(learning.load(anyLong(), any(), any(), any())).thenReturn(new CourseHomeLearningDTO(new CourseHomeDTO.ProgressDTO(0, 0, 0, 0), List.of(), List.of(), firstChange));
        var lectures = mock(LectureCourseHomeApi.class);
        when(lectures.load(anyLong(), any(), any(), any()))
                .thenReturn(new de.tum.cit.aet.artemis.lecture.dto.LectureCourseHomeDTO(CourseHomeSectionStatus.AVAILABLE, List.of(), NOW.plusSeconds(60)));
        var result = service(Optional.of(lectures)).load(COURSE, user, new CourseHomeQueryDTO(null), NOW);
        assertThat(result.nextRefreshAt()).isEqualTo(firstChange);
        assertThat(result.updates()).isEmpty();
        assertThat(result.nextStep()).isNull();
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
        return new CourseHomeService(courses, configurations, visits, learning, team, tabs, new CourseHomeContextService(), authorization,
                new CourseHomeSectionsService(communication, lectures, Optional.empty(), Optional.empty()), mock(CourseHomeAiPolicyService.class));
    }
}
