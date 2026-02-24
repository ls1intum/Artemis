package de.tum.cit.aet.artemis.core.service.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseRequest;
import de.tum.cit.aet.artemis.core.domain.CourseRequestStatus;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

@ExtendWith(MockitoExtension.class)
class CourseRequestServiceTest {

    @Mock
    private CourseRequestRepository courseRequestRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private CourseAccessService courseAccessService;

    @Mock
    private ChannelService channelService;

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private ResourceLoaderService resourceLoaderService;

    @InjectMocks
    private CourseRequestService courseRequestService;

    @Captor
    private ArgumentCaptor<Course> courseCaptor;

    @Captor
    private ArgumentCaptor<CourseRequest> courseRequestCaptor;

    @Captor
    private ArgumentCaptor<User> userCaptor;

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
        when(resourceLoaderService.getResource(any())).thenReturn(new ByteArrayResource("code of conduct".getBytes(StandardCharsets.UTF_8)));

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

    /**
     * Regression test: save() calls merge() for existing entities, which replaces the eagerly loaded
     * requester User with an uninitialized lazy proxy. The email method must use the User reference
     * captured before save(), not the one from the merged entity. Otherwise, accessing User properties
     * on the async thread throws LazyInitializationException and the email is silently not sent.
     */
    @Test
    void acceptRequestShouldSendEmailWithEagerlyLoadedRequesterNotMergedProxy() {
        User requester = createRequester();
        CourseRequest pendingRequest = createPendingRequest(requester);

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
        when(resourceLoaderService.getResource(any())).thenReturn(new ByteArrayResource("code of conduct".getBytes(StandardCharsets.UTF_8)));

        // Simulate merge() behavior: return a new entity where the requester association is null
        // (in production, merge() replaces it with an uninitialized lazy proxy)
        when(courseRequestRepository.save(any(CourseRequest.class))).thenAnswer(invocation -> {
            CourseRequest original = invocation.getArgument(0);
            CourseRequest merged = new CourseRequest();
            merged.setId(original.getId());
            merged.setTitle(original.getTitle());
            merged.setShortName(original.getShortName());
            merged.setStatus(original.getStatus());
            merged.setCreatedCourseId(original.getCreatedCourseId());
            merged.setRequester(null);
            return merged;
        });

        courseRequestService.acceptRequest(1L);

        verify(mailSendingService).buildAndSendAsync(userCaptor.capture(), anyString(), eq("mail/courseRequestAcceptedEmail"), anyMap());
        assertThat(userCaptor.getValue()).isSameAs(requester);
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("instructor@uni.test");
    }

    /**
     * Same regression test as above, but for the reject flow.
     */
    @Test
    void rejectRequestShouldSendEmailWithEagerlyLoadedRequesterNotMergedProxy() {
        User requester = createRequester();
        CourseRequest pendingRequest = createPendingRequest(requester);

        when(courseRequestRepository.findOneWithEagerRelationshipsById(1L)).thenReturn(Optional.of(pendingRequest));

        // Simulate merge() behavior: return a new entity where the requester association is null
        when(courseRequestRepository.save(any(CourseRequest.class))).thenAnswer(invocation -> {
            CourseRequest original = invocation.getArgument(0);
            CourseRequest merged = new CourseRequest();
            merged.setId(original.getId());
            merged.setTitle(original.getTitle());
            merged.setShortName(original.getShortName());
            merged.setStatus(original.getStatus());
            merged.setDecisionReason(original.getDecisionReason());
            merged.setRequester(null);
            return merged;
        });

        courseRequestService.rejectRequest(1L, "Not enough justification");

        verify(mailSendingService).buildAndSendAsync(userCaptor.capture(), anyString(), eq("mail/courseRequestRejectedEmail"), anyMap());
        assertThat(userCaptor.getValue()).isSameAs(requester);
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("instructor@uni.test");
    }

    /**
     * Verifies that createCourseRequest sends the received confirmation email with the
     * eagerly loaded requester User, not from the post-save entity. While new entities
     * use persist() (which preserves the association), this test ensures the defensive
     * pattern is in place for consistency with accept/reject.
     */
    @Test
    void createCourseRequestShouldSendReceivedEmailWithCorrectRequester() {
        User requester = createRequester();
        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(requester);
        when(courseRepository.existsByShortNameIgnoreCase("NEW123")).thenReturn(false);
        when(courseRequestRepository.findOneByShortNameIgnoreCase("NEW123")).thenReturn(Optional.empty());

        // save() returns a new entity without requester (simulates merge behavior for defensive testing)
        when(courseRequestRepository.save(any(CourseRequest.class))).thenAnswer(invocation -> {
            CourseRequest original = invocation.getArgument(0);
            CourseRequest saved = new CourseRequest();
            saved.setId(42L);
            saved.setTitle(original.getTitle());
            saved.setShortName(original.getShortName());
            saved.setSemester(original.getSemester());
            saved.setStartDate(original.getStartDate());
            saved.setEndDate(original.getEndDate());
            saved.setTestCourse(original.isTestCourse());
            saved.setReason(original.getReason());
            saved.setStatus(original.getStatus());
            saved.setRequester(null);
            return saved;
        });
        when(courseRequestRepository.findOneWithEagerRelationshipsById(42L)).thenAnswer(invocation -> {
            CourseRequest refetched = new CourseRequest();
            refetched.setId(42L);
            refetched.setTitle("New Course");
            refetched.setShortName("NEW123");
            refetched.setStatus(CourseRequestStatus.PENDING);
            refetched.setRequester(requester);
            return Optional.of(refetched);
        });

        var createDTO = new CourseRequestCreateDTO("New Course", "NEW123", "WS25", ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(10), false, "Need a course");
        courseRequestService.createCourseRequest(createDTO);

        // Verify received email was sent with the original requester, not from the saved entity
        verify(mailSendingService).buildAndSendAsync(userCaptor.capture(), eq("email.courseRequest.received.title"), eq("mail/courseRequestReceivedEmail"), anyMap());
        assertThat(userCaptor.getValue()).isSameAs(requester);
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("instructor@uni.test");

        // Verify contact notification was also sent
        verify(mailSendingService).buildAndSendAsync(any(User.class), eq("email.courseRequest.contact.title"), any(List.class), eq("mail/courseRequestContactEmail"), anyMap());
    }

    private User createRequester() {
        User requester = new User();
        requester.setId(7L);
        requester.setLogin("instructor1");
        requester.setEmail("instructor@uni.test");
        requester.setLangKey("en");
        return requester;
    }

    private CourseRequest createPendingRequest(User requester) {
        CourseRequest request = new CourseRequest();
        request.setId(1L);
        request.setTitle("New Course");
        request.setShortName("NEW123");
        request.setStartDate(ZonedDateTime.now().minusDays(1));
        request.setEndDate(ZonedDateTime.now().plusDays(10));
        request.setStatus(CourseRequestStatus.PENDING);
        request.setRequester(requester);
        return request;
    }
}
