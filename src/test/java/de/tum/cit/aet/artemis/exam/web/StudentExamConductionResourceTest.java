package de.tum.cit.aet.artemis.exam.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.service.StudentExamAccessService;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;

@ExtendWith(MockitoExtension.class)
class StudentExamConductionResourceTest {

    @Mock
    private StudentExamTestRepository studentExamRepository;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private StudentExamAccessService studentExamAccessService;

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    @InjectMocks
    private StudentExamConductionResource resource;

    @Test
    void doesNotAnnounceStartWhenPersistenceFails() {
        User student = new User();
        student.setId(1L);
        Exam exam = new Exam();
        exam.setId(2L);
        exam.setCourse(new Course());
        exam.setStartDate(ZonedDateTime.now().minusMinutes(1));
        StudentExam attempt = new StudentExam();
        attempt.setId(3L);
        attempt.setExam(exam);
        attempt.setUser(student);
        when(userRepository.getUserWithCourseRolesAndAuthorities()).thenReturn(student);
        when(studentExamRepository.findByIdWithExercisesElseThrow(3L)).thenReturn(attempt);
        doThrow(new IllegalStateException("save failed")).when(studentExamRepository).startStudentExam(eq(3L), any());

        assertThatThrownBy(() -> resource.getStudentExamForConduction(1L, 2L, 3L, new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");
        verifyNoInteractions(websocketMessagingService);
    }
}
