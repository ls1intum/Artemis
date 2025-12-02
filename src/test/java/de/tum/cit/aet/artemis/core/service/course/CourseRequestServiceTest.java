package de.tum.cit.aet.artemis.core.service.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseRequest;
import de.tum.cit.aet.artemis.core.domain.CourseRequestStatus;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CourseRequestServiceTest {

    @Mock
    private CourseRequestRepository courseRequestRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseAccessService courseAccessService;

    @Mock
    private ChannelService channelService;

    @Mock
    private MailSendingService mailSendingService;

    @InjectMocks
    private CourseRequestService courseRequestService;

    @Captor
    private ArgumentCaptor<Course> courseCaptor;

    @Captor
    private ArgumentCaptor<CourseRequest> courseRequestCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(courseRequestService, "contactEmail", "contact@example.org");
    }

    @Test
    void acceptRequestShouldCreateCourseWithDefaultsAndNotify() {
        CourseRequest pendingRequest = new CourseRequest();
        pendingRequest.setId(1L);
        pendingRequest.setTitle("New Course");
        pendingRequest.setShortName("NEW123");
        pendingRequest.setStartDate(ZonedDateTime.now().minusDays(1));
        pendingRequest.setEndDate(ZonedDateTime.now().plusDays(10));
        User requester = new User();
        requester.setId(7L);
        requester.setLogin("instructor1");
        requester.setEmail("instructor@uni.test");
        pendingRequest.setRequester(requester);

        when(courseRequestRepository.findOneWithEagerRelationshipsById(1L)).thenReturn(Optional.of(pendingRequest));
        when(courseRepository.existsByShortNameIgnoreCase("NEW123")).thenReturn(false);
        when(courseRequestRepository.findOneByShortNameIgnoreCase("NEW123")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setStudentGroupName(course.getDefaultStudentGroupName());
            course.setTeachingAssistantGroupName(course.getDefaultTeachingAssistantGroupName());
            course.setEditorGroupName(course.getDefaultEditorGroupName());
            course.setInstructorGroupName(course.getDefaultInstructorGroupName());
            return null;
        }).when(courseAccessService).setDefaultGroupsIfNotSet(any(Course.class));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            course.setId(22L);
            return course;
        });
        when(userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(7L)).thenReturn(requester);
        when(courseRequestRepository.save(any(CourseRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CourseRequestDTO result = courseRequestService.acceptRequest(1L);

        verify(courseAccessService).setDefaultGroupsIfNotSet(courseCaptor.capture());
        verify(channelService).createDefaultChannels(courseCaptor.getValue());
        verify(courseAccessService).addUserToGroup(eq(requester), eq(courseCaptor.getValue().getInstructorGroupName()), eq(courseCaptor.getValue()));
        verify(mailSendingService).buildAndSendAsync(eq(requester), anyString(), eq("mail/courseRequestAcceptedEmail"), anyMap());
        verify(courseRequestRepository).save(courseRequestCaptor.capture());

        assertThat(result.status()).isEqualTo(CourseRequestStatus.ACCEPTED);
        assertThat(result.createdCourseId()).isEqualTo(22L);
        assertThat(courseRequestCaptor.getValue().getProcessedDate()).isNotNull();
    }
}
