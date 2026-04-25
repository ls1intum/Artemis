package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.domain.CourseRequest;
import de.tum.cit.aet.artemis.core.domain.CourseRequestStatus;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseRequestAcceptDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestRequesterDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestsAdminOverviewDTO;
import de.tum.cit.aet.artemis.core.dto.InstructorCourseDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;

@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseRequestService {

    private static final Logger log = LoggerFactory.getLogger(CourseRequestService.class);

    private static final int MAX_TITLE_LENGTH = 255;

    private static final int MAX_INSTRUCTOR_COURSES = 10;

    private final ResourceLoaderService resourceLoaderService;

    @Value("${info.contact:}")
    private String contactEmail;

    private final CourseRequestRepository courseRequestRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final CourseAccessService courseAccessService;

    private final ChannelService channelService;

    private final MailSendingService mailSendingService;

    public CourseRequestService(CourseRequestRepository courseRequestRepository, CourseRepository courseRepository, UserRepository userRepository,
            CourseAccessService courseAccessService, ChannelService channelService, MailSendingService mailSendingService, ResourceLoaderService resourceLoaderService) {
        this.courseRequestRepository = courseRequestRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseAccessService = courseAccessService;
        this.channelService = channelService;
        this.mailSendingService = mailSendingService;
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Creates a new course request for the currently logged in user.
     *
     * @param createDTO the request data
     * @return the persisted request as DTO
     */
    public CourseRequestDTO createCourseRequest(CourseRequestCreateDTO createDTO) {
        var requester = userRepository.getUserWithGroupsAndAuthorities();

        if (createDTO.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", CourseRequest.ENTITY_NAME, "courseRequestTitleTooLong");
        }

        Course validationCourse = new Course();
        validationCourse.setStartDate(createDTO.startDate());
        validationCourse.setEndDate(createDTO.endDate());
        validationCourse.validateStartAndEndDate();

        CourseRequest courseRequest = new CourseRequest();
        courseRequest.setTitle(createDTO.title());
        courseRequest.setSemester(createDTO.semester());
        courseRequest.setStartDate(createDTO.startDate());
        courseRequest.setEndDate(createDTO.endDate());
        courseRequest.setTestCourse(createDTO.testCourse());
        courseRequest.setReason(createDTO.reason());
        courseRequest.setRequester(requester);
        courseRequest.setCreatedDate(ZonedDateTime.now());
        courseRequest.setStatus(CourseRequestStatus.PENDING);

        courseRequest = courseRequestRepository.save(courseRequest);
        notifyContact(courseRequest);
        sendReceivedEmail(requester, courseRequest);

        // Re-fetch with eager loading to avoid LazyInitializationException when converting to DTO
        courseRequest = getRequestWithRequesterElseThrow(courseRequest.getId());
        return toDto(courseRequest);
    }

    /**
     * Accepts the course request and creates a course with the admin-provided data.
     *
     * @param requestId the request id
     * @param acceptDTO the admin-provided course data including short name
     * @return the updated course request DTO
     */
    public CourseRequestDTO acceptRequest(long requestId, CourseRequestAcceptDTO acceptDTO) {
        CourseRequest courseRequest = getRequestWithRequesterElseThrow(requestId);
        if (courseRequest.getStatus() != CourseRequestStatus.PENDING) {
            throw new BadRequestAlertException("The course request has already been processed", CourseRequest.ENTITY_NAME, "courseRequestProcessed");
        }

        if (acceptDTO.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", CourseRequest.ENTITY_NAME, "courseRequestTitleTooLong");
        }

        validateShortNameUniqueness(acceptDTO.shortName());

        // Save eagerly loaded requester before save() which calls merge() and replaces the association with an uninitialized lazy proxy
        User requester = courseRequest.getRequester();

        Course createdCourse = createCourseFromAcceptDTO(courseRequest, acceptDTO);
        courseRequest.setCreatedCourseId(createdCourse.getId());
        courseRequest.setStatus(CourseRequestStatus.ACCEPTED);
        courseRequest.setDecisionReason(null);
        courseRequest.setAdmin(SecurityUtils.getCurrentUserLogin().orElse(null));
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequest = courseRequestRepository.save(courseRequest);

        sendAcceptedEmail(requester, courseRequest, createdCourse);
        // Re-fetch with eager loading to avoid LazyInitializationException when converting to DTO
        courseRequest = getRequestWithRequesterElseThrow(courseRequest.getId());
        return toDto(courseRequest);
    }

    /**
     * Rejects the course request.
     *
     * @param requestId      the request id
     * @param decisionReason the rejection reason
     * @return the updated course request DTO
     */
    public CourseRequestDTO rejectRequest(long requestId, String decisionReason) {
        CourseRequest courseRequest = getRequestWithRequesterElseThrow(requestId);
        if (courseRequest.getStatus() != CourseRequestStatus.PENDING) {
            throw new BadRequestAlertException("The course request has already been processed", CourseRequest.ENTITY_NAME, "courseRequestProcessed");
        }

        // Save eagerly loaded requester before save() which calls merge() and replaces the association with an uninitialized lazy proxy
        User requester = courseRequest.getRequester();

        courseRequest.setStatus(CourseRequestStatus.REJECTED);
        courseRequest.setDecisionReason(decisionReason != null ? decisionReason.trim() : null);
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequest.setAdmin(SecurityUtils.getCurrentUserLogin().orElse(null));
        courseRequest = courseRequestRepository.save(courseRequest);
        sendRejectedEmail(requester, courseRequest);

        // Re-fetch with eager loading to avoid LazyInitializationException when converting to DTO
        courseRequest = getRequestWithRequesterElseThrow(courseRequest.getId());
        return toDto(courseRequest);
    }

    /**
     * Retrieves the most recent courses where the given user is an instructor.
     *
     * @param userId the user id of the requester
     * @return list of up to 10 most recent instructor courses as DTOs
     */
    public List<InstructorCourseDTO> getInstructorCourses(long userId) {
        User user = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(userId);
        Set<String> groups = user.getGroups();
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        List<Course> courses = courseRepository.findRecentCoursesForInstructorWithGroups(groups, PageRequest.of(0, MAX_INSTRUCTOR_COURSES));
        return courses.stream().map(c -> new InstructorCourseDTO(c.getTitle(), c.getShortName(), c.getSemester(), c.getStartDate(), c.getEndDate())).toList();
    }

    private CourseRequest getRequestWithRequesterElseThrow(long requestId) {
        return courseRequestRepository.findOneWithEagerRelationshipsById(requestId).orElseThrow(() -> new EntityNotFoundException(CourseRequest.ENTITY_NAME, requestId));
    }

    private void validateShortNameUniqueness(String shortName) {
        if (courseRepository.existsByShortNameIgnoreCase(shortName)) {
            throw new BadRequestAlertException("A course with the same short name already exists", CourseRequest.ENTITY_NAME, "courseShortNameExists");
        }
    }

    private Course createCourseFromAcceptDTO(CourseRequest request, CourseRequestAcceptDTO acceptDTO) {
        Course course = new Course();

        // Use admin-provided data from the accept modal
        course.setTitle(acceptDTO.title());
        course.setShortName(acceptDTO.shortName());
        course.setSemester(acceptDTO.semester());
        course.setStartDate(acceptDTO.startDate());
        course.setEndDate(acceptDTO.endDate());
        course.setTestCourse(request.isTestCourse());
        course.setOnlineCourse(Boolean.FALSE);
        course.setEnrollmentEnabled(Boolean.FALSE);
        course.setLearningPathsEnabled(false);
        course.setStudentCourseAnalyticsDashboardEnabled(false);
        course.setRestrictedAthenaModulesAccess(false);
        course.setAccuracyOfScores(1);
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);

        var templatePath = Path.of("templates", "codeofconduct", "README.md");

        try {
            log.debug("Loading template: {}", templatePath);
            var resource = resourceLoaderService.getResource(templatePath);
            try (var inputStream = resource.getInputStream()) {
                var informationSharingMessageCodeOfConduct = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                course.setCourseInformationSharingMessagingCodeOfConduct(informationSharingMessageCodeOfConduct);
            }
        }
        catch (IOException e) {
            log.warn("Could not load code of conduct template from path: {}", templatePath, e);
        }

        courseAccessService.setDefaultGroupsIfNotSet(course);

        course.validateShortName();
        course.validateStartAndEndDate();
        course.validateEnrollmentStartAndEndDate();
        course.validateUnenrollmentEndDate();
        course.validateEnrollmentConfirmationMessage();
        course.validateComplaintsAndRequestMoreFeedbackConfig();
        course.validateOnlineCourseAndEnrollmentEnabled();
        course.validateAccuracyOfScores();

        Course createdCourse = courseRepository.save(course);
        channelService.createDefaultChannels(createdCourse);

        if (request.getRequester() != null) {
            User requesterWithGroups = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(request.getRequester().getId());
            courseAccessService.addUserToGroup(requesterWithGroups, createdCourse.getInstructorGroupName(), createdCourse);
        }
        return createdCourse;
    }

    private void notifyContact(CourseRequest request) {
        if (!StringUtils.hasText(contactEmail)) {
            log.warn("Contact email is not configured, skipping course request notification");
            return;
        }

        // Extract scalar values from entity before async call to avoid LazyInitializationException
        User requester = request.getRequester();
        String requesterName = requester != null ? requester.getName() : null;
        String requesterEmail = requester != null ? requester.getEmail() : null;
        String requesterLangKey = requester != null && requester.getLangKey() != null ? requester.getLangKey() : "en";

        var emailData = new ContactEmailData(request.getTitle(), request.getSemester(), request.getStartDate(), request.getEndDate(), request.isTestCourse(), request.getReason(),
                requesterName, requesterEmail);

        User recipient = new User();
        recipient.setEmail(contactEmail);
        recipient.setLangKey(requesterLangKey);
        recipient.setLogin("course-request-contact");
        mailSendingService.buildAndSendAsync(recipient, "email.courseRequest.contact.title", List.of(request.getTitle()), "mail/courseRequestContactEmail",
                Map.of("courseRequest", emailData));
    }

    /**
     * DTO for contact email template to avoid lazy loading issues in async context.
     * Contains only scalar values needed by the email template.
     */
    private record ContactEmailData(String title, String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse, String reason, String requesterName,
            String requesterEmail) {
    }

    /**
     * DTO for course request email templates to avoid lazy loading issues in async context.
     * The post-save entity returned by merge() may contain uninitialized lazy proxies, so we
     * extract all scalar values needed by the templates before passing to the async email method.
     */
    private record CourseRequestEmailData(String title, String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse, String reason, String decisionReason) {
    }

    private static CourseRequestEmailData toEmailData(CourseRequest request) {
        return new CourseRequestEmailData(request.getTitle(), request.getSemester(), request.getStartDate(), request.getEndDate(), request.isTestCourse(), request.getReason(),
                request.getDecisionReason());
    }

    private void sendAcceptedEmail(User requester, CourseRequest request, Course course) {
        if (requester == null) {
            return;
        }
        mailSendingService.buildAndSendAsync(requester, "email.courseRequest.accepted.title", "mail/courseRequestAcceptedEmail",
                Map.of("course", course, "courseRequest", toEmailData(request)));
    }

    private void sendRejectedEmail(User requester, CourseRequest request) {
        if (requester == null) {
            return;
        }
        mailSendingService.buildAndSendAsync(requester, "email.courseRequest.rejected.title", "mail/courseRequestRejectedEmail", Map.of("courseRequest", toEmailData(request)));
    }

    private void sendReceivedEmail(User requester, CourseRequest request) {
        if (requester == null) {
            return;
        }
        mailSendingService.buildAndSendAsync(requester, "email.courseRequest.received.title", "mail/courseRequestReceivedEmail", Map.of("courseRequest", toEmailData(request)));
    }

    private CourseRequestDTO toDto(CourseRequest courseRequest) {
        return toDto(courseRequest, null);
    }

    private CourseRequestDTO toDto(CourseRequest courseRequest, Integer instructorCourseCount) {
        CourseRequestRequesterDTO requesterDto = courseRequest.getRequester() != null ? new CourseRequestRequesterDTO(courseRequest.getRequester()) : null;
        Long createdCourseId = courseRequest.getCreatedCourseId();
        return new CourseRequestDTO(courseRequest.getId(), courseRequest.getTitle(), courseRequest.getSemester(), courseRequest.getStartDate(), courseRequest.getEndDate(),
                courseRequest.isTestCourse(), courseRequest.getReason(), courseRequest.getStatus(), courseRequest.getCreatedDate(), courseRequest.getProcessedDate(),
                courseRequest.getDecisionReason(), requesterDto, createdCourseId, instructorCourseCount);
    }

    /**
     * Retrieves the admin overview of course requests with pending requests (including instructor course count)
     * and decided requests with pagination.
     *
     * @param decidedPage     the page number for decided requests (0-indexed)
     * @param decidedPageSize the page size for decided requests
     * @return the admin overview DTO containing pending and decided requests
     */
    public CourseRequestsAdminOverviewDTO getAdminOverview(int decidedPage, int decidedPageSize) {
        // Get pending requests with instructor course count
        List<CourseRequest> pendingRequests = courseRequestRepository.findAllByStatusOrderByCreatedDateDesc(CourseRequestStatus.PENDING);
        List<CourseRequestDTO> pendingDtos = pendingRequests.stream().map(request -> {
            Integer instructorCount = computeInstructorCourseCount(request.getRequester());
            return toDto(request, instructorCount);
        }).toList();

        // Get decided requests with pagination (without instructor course count)
        var pageable = PageRequest.of(decidedPage, decidedPageSize);
        var decidedPageResult = courseRequestRepository.findAllByStatusNotOrderByProcessedDateDesc(CourseRequestStatus.PENDING, pageable);
        List<CourseRequestDTO> decidedDtos = decidedPageResult.getContent().stream().map(this::toDto).toList();

        return new CourseRequestsAdminOverviewDTO(pendingDtos, decidedDtos, decidedPageResult.getTotalElements());
    }

    private Integer computeInstructorCourseCount(User requester) {
        if (requester == null) {
            return null;
        }
        Set<String> groups = requester.getGroups();
        if (groups == null || groups.isEmpty()) {
            return 0;
        }
        return (int) courseRepository.countCoursesForInstructorWithGroups(groups);
    }
}
