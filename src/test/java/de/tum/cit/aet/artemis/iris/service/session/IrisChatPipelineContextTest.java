package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedViewContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSlidesContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVideoContextDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

class IrisChatPipelineContextTest {

    private IrisSessionRepository sessions;

    private CourseTestRepository courses;

    private LectureRepositoryApi lectures;

    private PyrisPipelineService pipelines;

    private IrisChatPipelineExecutionService service;

    private IrisChatSession session;

    private User user;

    private Course course;

    @BeforeEach
    void setUp() {
        sessions = mock(IrisSessionRepository.class);
        courses = mock(CourseTestRepository.class);
        lectures = mock(LectureRepositoryApi.class);
        pipelines = mock(PyrisPipelineService.class);
        service = new IrisChatPipelineExecutionService(sessions, courses, null, null, null, null, null, Optional.empty(), Optional.of(lectures), null, mock(PyrisDTOService.class),
                pipelines);
        course = new Course();
        course.setId(1L);
        user = new User();
        user.setId(2L);
        var lecture = new Lecture();
        lecture.setId(3L);
        lecture.setCourse(course);
        session = new IrisChatSession(lecture, user);
        session.setId(4L);
    }

    @Test
    void lectureContextRejectsUnknownAndMismatchingUnitsAndPreservesValidNestedContext() {
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(session);
        when(courses.findByIdElseThrow(1L)).thenReturn(course);
        when(pipelines.loadCourseWithParticipationOfStudent(1L, 2L)).thenReturn(course);
        var lecture = new Lecture();
        lecture.setId(3L);
        var text = new TextUnit();
        text.setId(5L);
        var video = new AttachmentVideoUnit();
        video.setId(6L);
        var attachment = new Attachment();
        attachment.setVersion(7);
        video.setAttachment(attachment);
        lecture.setLectureUnits(List.of(text, video));
        when(lectures.findByIdWithLectureUnitsElseThrow(3L)).thenReturn(lecture);
        var slides = new IrisSlidesContextDTO(5L, 1);
        var videoContext = new IrisVideoContextDTO(6L, 1.0);
        var onlySlides = new IrisCombinedViewContextDTO(slides, null);
        var onlyVideo = new IrisCombinedViewContextDTO(null, videoContext);
        var both = new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(6L, 2), videoContext);
        List<IrisMessageContextDTO> context = List.of(slides, videoContext, onlySlides, onlyVideo, both, new IrisVideoContextDTO(99L, 0.0), new IrisSlidesContextDTO(99L, 1),
                new IrisCombinedViewContextDTO(null, null), new IrisCombinedViewContextDTO(slides, videoContext),
                new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(99L, 1), null), new IrisCombinedViewContextDTO(null, new IrisVideoContextDTO(99L, 0.0)));
        service.execute(session, Optional.empty(), Optional.of(IrisCourseSettings.defaultSettings()), Optional.empty(), Map.of(), context, "tab");
        var builder = ArgumentCaptor.forClass(PyrisPipelineService.ChatPipelineDTOBuilder.class);
        verify(pipelines).executeChatPipeline(any(), any(), any(), any(), any(), builder.capture());
        var dto = builder.getValue().apply(new PyrisPipelineExecutionDTO(null), user, null);
        assertThat(dto.context()).containsExactly(slides, videoContext, onlySlides, onlyVideo, both);
        assertThat(dto.lectureUnitId()).isEqualTo(5L);
        assertThat(dto.lecture().units()).extracting(unit -> unit.attachmentVersion()).containsExactly(null, 7);
    }

    @Test
    void deletedOrWrongTypeSessionsFailBeforePipelineExecution() {
        assertThatThrownBy(() -> service.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), List.of()))
                .isInstanceOf(de.tum.cit.aet.artemis.core.exception.EntityNotFoundException.class);
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(new IrisTutorSuggestionSession(7L, user));
        assertThatThrownBy(() -> service.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected IrisChatSession");
        org.mockito.Mockito.verifyNoInteractions(pipelines);
    }
}
