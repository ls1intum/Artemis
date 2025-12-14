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
import de.tum.cit.aet.artemis.core.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestsAdminOverviewDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
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
        validateShortNameUniqueness(createDTO.shortName(), createDTO.title(), createDTO.semester(), null);

        if (createDTO.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", CourseRequest.ENTITY_NAME, "courseRequestTitleTooLong");
        }

        Course validationCourse = new Course();
        validationCourse.setShortName(createDTO.shortName());
        validationCourse.validateShortName();
        validationCourse.setStartDate(createDTO.startDate());
        validationCourse.setEndDate(createDTO.endDate());
        validationCourse.validateStartAndEndDate();

        CourseRequest courseRequest = new CourseRequest();
        courseRequest.setTitle(createDTO.title());
        courseRequest.setShortName(createDTO.shortName());
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
        sendReceivedEmail(courseRequest);

        // Re-fetch with eager loading to avoid LazyInitializationException when converting to DTO
        courseRequest = getRequestWithRequesterElseThrow(courseRequest.getId());
        return toDto(courseRequest);
    }

    /**
     * Accepts the course request and creates a course.
     *
     * @param requestId the request id
     * @return the updated course request DTO
     */
    public CourseRequestDTO acceptRequest(long requestId) {
        CourseRequest courseRequest = getRequestWithRequesterElseThrow(requestId);
        if (courseRequest.getStatus() != CourseRequestStatus.PENDING) {
            throw new BadRequestAlertException("The course request has already been processed", CourseRequest.ENTITY_NAME, "courseRequestProcessed");
        }

        validateShortNameUniqueness(courseRequest.getShortName(), courseRequest.getTitle(), courseRequest.getSemester(), courseRequest.getId());

        Course createdCourse = createCourseFromRequest(courseRequest);
        courseRequest.setCreatedCourseId(createdCourse.getId());
        courseRequest.setStatus(CourseRequestStatus.ACCEPTED);
        courseRequest.setDecisionReason(null);
        courseRequest.setAdmin(SecurityUtils.getCurrentUserLogin().orElse(null));
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequest = courseRequestRepository.save(courseRequest);

        sendAcceptedEmail(courseRequest, createdCourse);
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
        courseRequest.setStatus(CourseRequestStatus.REJECTED);
        courseRequest.setDecisionReason(decisionReason != null ? decisionReason.trim() : null);
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequest.setAdmin(SecurityUtils.getCurrentUserLogin().orElse(null));
        courseRequest = courseRequestRepository.save(courseRequest);
        sendRejectedEmail(courseRequest);

        // Re-fetch with eager loading to avoid LazyInitializationException when converting to DTO
        courseRequest = getRequestWithRequesterElseThrow(courseRequest.getId());
        return toDto(courseRequest);
    }

    /**
     * Updates a pending course request. Only admins can update requests.
     *
     * @param requestId the request id
     * @param updateDTO the updated course request data
     * @return the updated course request DTO
     */
    public CourseRequestDTO updateCourseRequest(long requestId, CourseRequestCreateDTO updateDTO) {
        CourseRequest courseRequest = getRequestWithRequesterElseThrow(requestId);
        if (courseRequest.getStatus() != CourseRequestStatus.PENDING) {
            throw new BadRequestAlertException("The course request has already been processed", CourseRequest.ENTITY_NAME, "courseRequestProcessed");
        }

        validateShortNameUniqueness(updateDTO.shortName(), updateDTO.title(), updateDTO.semester(), requestId);

        if (updateDTO.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", CourseRequest.ENTITY_NAME, "courseRequestTitleTooLong");
        }

        // Validate date range if both dates are provided
        if (updateDTO.startDate() != null && updateDTO.endDate() != null) {
            Course validationCourse = new Course();
            validationCourse.setStartDate(updateDTO.startDate());
            validationCourse.setEndDate(updateDTO.endDate());
            validationCourse.validateStartAndEndDate();
        }

        courseRequest.setTitle(updateDTO.title());
        courseRequest.setShortName(updateDTO.shortName());
        courseRequest.setSemester(updateDTO.semester());
        courseRequest.setStartDate(updateDTO.startDate());
        courseRequest.setEndDate(updateDTO.endDate());
        courseRequest.setTestCourse(updateDTO.testCourse());
        courseRequest.setReason(updateDTO.reason());

        courseRequest = courseRequestRepository.save(courseRequest);
        // Re-fetch with eager loading to avoid LazyInitializationException when converting to DTO
        courseRequest = getRequestWithRequesterElseThrow(courseRequest.getId());
        return toDto(courseRequest);
    }

    private CourseRequest getRequestWithRequesterElseThrow(long requestId) {
        return courseRequestRepository.findOneWithEagerRelationshipsById(requestId).orElseThrow(() -> new EntityNotFoundException(CourseRequest.ENTITY_NAME, requestId));
    }

    private void validateShortNameUniqueness(String shortName, String title, String semester, Long currentRequestId) {
        boolean existsInCourse = courseRepository.existsByShortNameIgnoreCase(shortName);
        var existingRequest = courseRequestRepository.findOneByShortNameIgnoreCase(shortName);
        boolean existsInRequest = existingRequest.isPresent() && (currentRequestId == null || !existingRequest.get().getId().equals(currentRequestId));

        if (existsInCourse || existsInRequest) {
            String suggestedShortName = generateUniqueShortName(title, semester);
            String errorKey = existsInCourse ? "courseShortNameExists" : "courseRequestShortNameExists";
            throw new BadRequestAlertException("A course or request with the same short name already exists", CourseRequest.ENTITY_NAME, errorKey,
                    Map.of("suggestedShortName", suggestedShortName));
        }
    }

    /**
     * Generates a unique short name based on the first letters of the title words and the semester number.
     * If the generated short name already exists, appends an incrementing number until a unique one is found.
     *
     * @param title    the course title
     * @param semester the semester (e.g., "WS25/26", "SS24")
     * @return a unique short name suggestion
     */
    private String generateUniqueShortName(String title, String semester) {
        // Extract first letters from title words (only alphanumeric characters)
        StringBuilder baseShortName = new StringBuilder();
        if (title != null && !title.isBlank()) {
            String[] words = title.split("\\s+");
            for (String word : words) {
                if (!word.isEmpty()) {
                    char firstChar = Character.toUpperCase(word.charAt(0));
                    if (Character.isLetterOrDigit(firstChar)) {
                        baseShortName.append(firstChar);
                    }
                }
            }
        }

        // Extract semester number (digits only)
        if (semester != null && !semester.isBlank()) {
            String semesterDigits = semester.replaceAll("[^0-9]", "");
            if (!semesterDigits.isEmpty()) {
                baseShortName.append(semesterDigits);
            }
        }

        // Ensure minimum length of 3 characters by appending random uppercase letters
        String base = baseShortName.toString();
        if (base.length() < 3) {
            base = base + generateRandomLetters(3 - base.length());
        }

        // Find a unique short name by appending a number if necessary
        String candidate = base;
        int counter = 1;
        while (isShortNameTaken(candidate)) {
            candidate = base + counter;
            counter++;
        }

        return candidate;
    }

    private boolean isShortNameTaken(String shortName) {
        return courseRepository.existsByShortNameIgnoreCase(shortName) || courseRequestRepository.findOneByShortNameIgnoreCase(shortName).isPresent();
    }

    private String generateRandomLetters(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('A' + (int) (Math.random() * 26)));
        }
        return sb.toString();
    }

    private Course createCourseFromRequest(CourseRequest request) {
        Course course = new Course();

        course.setTitle(request.getTitle());
        course.setShortName(request.getShortName());
        course.setSemester(request.getSemester());
        course.setStartDate(request.getStartDate());
        course.setEndDate(request.getEndDate());
        course.setTestCourse(request.isTestCourse());
        course.setOnlineCourse(Boolean.FALSE);
        course.setEnrollmentEnabled(Boolean.FALSE);
        course.setLearningPathsEnabled(false);
        course.setStudentCourseAnalyticsDashboardEnabled(false);
        course.setRestrictedAthenaModulesAccess(false);
        course.setAccuracyOfScores(1);
        course.setFaqEnabled(true);
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

        var emailData = new ContactEmailData(request.getTitle(), request.getShortName(), request.getSemester(), request.getStartDate(), request.getEndDate(),
                request.isTestCourse(), request.getReason(), requesterName, requesterEmail);

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
    private record ContactEmailData(String title, String shortName, String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse, String reason,
            String requesterName, String requesterEmail) {
    }

    private void sendAcceptedEmail(CourseRequest request, Course course) {
        if (request.getRequester() == null) {
            return;
        }
        mailSendingService.buildAndSendAsync(request.getRequester(), "email.courseRequest.accepted.title", "mail/courseRequestAcceptedEmail",
                Map.of("course", course, "courseRequest", request));
    }

    private void sendRejectedEmail(CourseRequest request) {
        if (request.getRequester() == null) {
            return;
        }
        mailSendingService.buildAndSendAsync(request.getRequester(), "email.courseRequest.rejected.title", "mail/courseRequestRejectedEmail", Map.of("courseRequest", request));
    }

    private void sendReceivedEmail(CourseRequest request) {
        if (request.getRequester() == null) {
            return;
        }
        mailSendingService.buildAndSendAsync(request.getRequester(), "email.courseRequest.received.title", "mail/courseRequestReceivedEmail", Map.of("courseRequest", request));
    }

    private CourseRequestDTO toDto(CourseRequest courseRequest) {
        return toDto(courseRequest, null);
    }

    private CourseRequestDTO toDto(CourseRequest courseRequest, Integer instructorCourseCount) {
        UserDTO requesterDto = courseRequest.getRequester() != null ? new UserDTO(courseRequest.getRequester()) : null;
        Long createdCourseId = courseRequest.getCreatedCourseId();
        return new CourseRequestDTO(courseRequest.getId(), courseRequest.getTitle(), courseRequest.getShortName(), courseRequest.getSemester(), courseRequest.getStartDate(),
                courseRequest.getEndDate(), courseRequest.isTestCourse(), courseRequest.getReason(), courseRequest.getStatus(), courseRequest.getCreatedDate(),
                courseRequest.getProcessedDate(), courseRequest.getDecisionReason(), requesterDto, createdCourseId, instructorCourseCount);
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
