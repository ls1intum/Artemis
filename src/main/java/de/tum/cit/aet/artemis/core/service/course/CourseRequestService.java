package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseRequest;
import de.tum.cit.aet.artemis.core.domain.CourseRequestStatus;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseRequestService {

    private static final Logger log = LoggerFactory.getLogger(CourseRequestService.class);

    private static final int MAX_TITLE_LENGTH = 255;

    @Value("${info.contact:}")
    private String contactEmail;

    private final CourseRequestRepository courseRequestRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final CourseAccessService courseAccessService;

    private final ChannelService channelService;

    private final MailSendingService mailSendingService;

    public CourseRequestService(CourseRequestRepository courseRequestRepository, CourseRepository courseRepository, UserRepository userRepository,
            CourseAccessService courseAccessService, ChannelService channelService, MailSendingService mailSendingService) {
        this.courseRequestRepository = courseRequestRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.courseAccessService = courseAccessService;
        this.channelService = channelService;
        this.mailSendingService = mailSendingService;
    }

    /**
     * Creates a new course request for the currently logged in user.
     *
     * @param createDTO the request data
     * @return the persisted request as DTO
     */
    public CourseRequestDTO createCourseRequest(CourseRequestCreateDTO createDTO) {
        var requester = userRepository.getUserWithGroupsAndAuthorities();
        validateShortNameUniqueness(createDTO.shortName(), null);

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

        return toDto(courseRequest);
    }

    public List<CourseRequestDTO> findAll() {
        return courseRequestRepository.findAllByOrderByCreatedDateDesc().stream().map(this::toDto).toList();
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

        validateShortNameUniqueness(courseRequest.getShortName(), courseRequest.getId());

        Course createdCourse = createCourseFromRequest(courseRequest);
        courseRequest.setCreatedCourseId(createdCourse.getId());
        courseRequest.setStatus(CourseRequestStatus.ACCEPTED);
        courseRequest.setDecisionReason(null);
        courseRequest.setAdmin(SecurityUtils.getCurrentUserLogin().orElse(null));
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequestRepository.save(courseRequest);

        sendAcceptedEmail(courseRequest, createdCourse);
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
        courseRequest.setDecisionReason(decisionReason.trim());
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequest.setAdmin(SecurityUtils.getCurrentUserLogin().orElse(null));
        courseRequestRepository.save(courseRequest);
        sendRejectedEmail(courseRequest);

        return toDto(courseRequest);
    }

    private CourseRequest getRequestWithRequesterElseThrow(long requestId) {
        return courseRequestRepository.findOneWithEagerRelationshipsById(requestId).orElseThrow(() -> new EntityNotFoundException(CourseRequest.ENTITY_NAME, requestId));
    }

    private void validateShortNameUniqueness(String shortName, Long currentRequestId) {
        if (courseRepository.existsByShortNameIgnoreCase(shortName)) {
            throw new BadRequestAlertException("A course with the same short name already exists", CourseRequest.ENTITY_NAME, "courseShortNameExists");
        }
        var existingRequest = courseRequestRepository.findOneByShortNameIgnoreCase(shortName);
        if (existingRequest.isPresent() && (currentRequestId == null || !existingRequest.get().getId().equals(currentRequestId))) {
            throw new BadRequestAlertException("A course request with the same short name already exists", CourseRequest.ENTITY_NAME, "courseRequestShortNameExists");
        }
    }

    private Course createCourseFromRequest(CourseRequest request) {
        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setShortName(request.getShortName());
        course.setSemester(request.getSemester());
        course.setStartDate(request.getStartDate());
        course.setEndDate(request.getEndDate());
        course.setTestCourse(request.isTestCourse());

        // TODO: set a few default values that are currently missing

        course.validateShortName();
        course.validateStartAndEndDate();
        courseAccessService.setDefaultGroupsIfNotSet(course);

        Course createdCourse = courseRepository.save(course);
        channelService.createDefaultChannels(course);

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
        User recipient = new User();
        recipient.setEmail(contactEmail);
        recipient.setLangKey(request.getRequester() != null && request.getRequester().getLangKey() != null ? request.getRequester().getLangKey() : "en");
        recipient.setLogin("course-request-contact");
        mailSendingService.buildAndSendAsync(recipient, "email.courseRequest.contact.title", "mail/courseRequestContactEmail", Map.of("courseRequest", request));
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

    private CourseRequestDTO toDto(CourseRequest courseRequest) {
        UserDTO requesterDto = courseRequest.getRequester() != null ? new UserDTO(courseRequest.getRequester()) : null;
        Long createdCourseId = courseRequest.getCreatedCourseId();
        return new CourseRequestDTO(courseRequest.getId(), courseRequest.getTitle(), courseRequest.getShortName(), courseRequest.getSemester(), courseRequest.getStartDate(),
                courseRequest.getEndDate(), courseRequest.isTestCourse(), courseRequest.getReason(), courseRequest.getStatus(), courseRequest.getCreatedDate(),
                courseRequest.getProcessedDate(), courseRequest.getDecisionReason(), requesterDto, createdCourseId);
    }
}
