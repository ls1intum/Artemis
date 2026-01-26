package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.LearningPathApi;
import de.tum.cit.aet.artemis.communication.service.ConductAgreementService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseUpdateDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupChannelManagementApi;

/**
 * REST controller for updating a course.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class CourseUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(CourseUpdateResource.class);

    private static final int MAX_TITLE_LENGTH = 255;

    private final AuthorizationCheckService authCheckService;

    private final FileService fileService;

    private final ConductAgreementService conductAgreementService;

    private final Optional<LtiApi> ltiApi;

    private final Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi;

    private final Optional<AthenaApi> athenaApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<LearningPathApi> learningPathApi;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public CourseUpdateResource(Optional<LtiApi> ltiApi, AuthorizationCheckService authCheckService, FileService fileService,
            Optional<TutorialGroupChannelManagementApi> tutorialGroupChannelManagementApi, Optional<LearningPathApi> learningPathApi,
            ConductAgreementService conductAgreementService, Optional<AthenaApi> athenaApi, Optional<LearnerProfileApi> learnerProfileApi, CourseRepository courseRepository,
            UserRepository userRepository) {
        this.ltiApi = ltiApi;
        this.authCheckService = authCheckService;
        this.fileService = fileService;
        this.tutorialGroupChannelManagementApi = tutorialGroupChannelManagementApi;
        this.learningPathApi = learningPathApi;
        this.conductAgreementService = conductAgreementService;
        this.athenaApi = athenaApi;
        this.learnerProfileApi = learnerProfileApi;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    private static Set<String> getChangedGroupNames(CourseUpdateDTO courseUpdateDTO, Course existingCourse) {
        Set<String> existingGroupNames = new HashSet<>(List.of(existingCourse.getStudentGroupName(), existingCourse.getTeachingAssistantGroupName(),
                existingCourse.getEditorGroupName(), existingCourse.getInstructorGroupName()));
        Set<String> newGroupNames = new HashSet<>(List.of(courseUpdateDTO.studentGroupName(), courseUpdateDTO.teachingAssistantGroupName(), courseUpdateDTO.editorGroupName(),
                courseUpdateDTO.instructorGroupName()));
        Set<String> changedGroupNames = new HashSet<>(newGroupNames);
        changedGroupNames.removeAll(existingGroupNames);
        return changedGroupNames;
    }

    /**
     * PUT /courses/:courseId : Updates an existing course.
     *
     * @param courseId        the id of the course to update
     * @param courseUpdateDTO the DTO containing the course update data
     * @param file            the optional course icon file
     * @return the ResponseEntity with status 200 (OK) and with body the updated course
     */
    @PutMapping(value = "courses/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastInstructor
    public ResponseEntity<Course> updateCourse(@PathVariable Long courseId, @RequestPart("course") CourseUpdateDTO courseUpdateDTO,
            @RequestPart(required = false) MultipartFile file) throws URISyntaxException {
        log.debug("REST request to update Course : {}", courseUpdateDTO);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        var existingCourse = courseRepository.findByIdForUpdateElseThrow(courseUpdateDTO.id());

        if (existingCourse.getTimeZone() != null && courseUpdateDTO.timeZone() == null) {
            throw new IllegalArgumentException("You can not remove the time zone of a course");
        }

        var timeZoneChanged = (existingCourse.getTimeZone() != null && courseUpdateDTO.timeZone() != null && !existingCourse.getTimeZone().equals(courseUpdateDTO.timeZone()));

        var athenaModuleAccessChanged = existingCourse.getRestrictedAthenaModulesAccess() != courseUpdateDTO.restrictedAthenaModulesAccess();

        if (!Objects.equals(existingCourse.getShortName(), courseUpdateDTO.shortName())) {
            throw new BadRequestAlertException("The course short name cannot be changed", Course.ENTITY_NAME, "shortNameCannotChange", true);
        }

        // only allow admins or instructors of the existing course to change it
        // this is important, otherwise someone could put himself into the instructor group of the updated course
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingCourse, user);

        if (!authCheckService.isAdmin(user)) {
            // this means the user must be an instructor, who has NO Admin rights.
            // instructors are not allowed to change group names, because this would lead to security problems
            final var changedGroupNames = getChangedGroupNames(courseUpdateDTO, existingCourse);
            if (!changedGroupNames.isEmpty()) {
                throw new BadRequestAlertException("You are not allowed to change the group names of a course", Course.ENTITY_NAME, "groupNamesCannotChange", true);
            }
            // instructors are not allowed to change the access to restricted Athena modules
            if (athenaModuleAccessChanged) {
                throw new BadRequestAlertException("You are not allowed to change the access to restricted Athena modules of a course", Course.ENTITY_NAME,
                        "restrictedAthenaModulesAccessCannotChange", true);
            }
            // instructors are not allowed to change the dashboard settings
            if (existingCourse.getStudentCourseAnalyticsDashboardEnabled() != courseUpdateDTO.studentCourseAnalyticsDashboardEnabled()) {
                throw new BadRequestAlertException("You are not allowed to change the dashboard settings of a course", Course.ENTITY_NAME, "dashboardSettingsCannotChange", true);
            }
        }

        if (courseUpdateDTO.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestAlertException("The course title is too long", Course.ENTITY_NAME, "courseTitleTooLong");
        }

        // Save the existing course icon path before applying DTO changes
        String existingCourseIcon = existingCourse.getCourseIcon();

        // Apply DTO values to the existing course entity - this preserves all relationships
        courseUpdateDTO.applyTo(existingCourse);
        existingCourse.setId(courseId); // Ensure the ID is correct

        existingCourse.validateEnrollmentConfirmationMessage();
        existingCourse.validateComplaintsAndRequestMoreFeedbackConfig();
        existingCourse.validateOnlineCourseAndEnrollmentEnabled();
        existingCourse.validateShortName();
        existingCourse.validateAccuracyOfScores();
        existingCourse.validateStartAndEndDate();
        existingCourse.validateEnrollmentStartAndEndDate();
        existingCourse.validateUnenrollmentEndDate();
        if (file != null) {
            Path basePath = FilePathConverter.getCourseIconFilePath();
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.COURSE_ICON, false);
            existingCourse.setCourseIcon(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.COURSE_ICON, courseId).toString());
            if (existingCourseIcon != null) {
                // delete old course icon
                fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(new URI(existingCourseIcon), FilePathType.COURSE_ICON), 0);
            }
        }
        else if (courseUpdateDTO.courseIcon() == null && existingCourseIcon != null) {
            // delete old course icon
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(new URI(existingCourseIcon), FilePathType.COURSE_ICON), 0);
        }

        boolean wasOnlineCourse = existingCourse.getOnlineCourseConfiguration() != null;
        if (courseUpdateDTO.onlineCourse() != null && courseUpdateDTO.onlineCourse() != wasOnlineCourse) {
            if (courseUpdateDTO.onlineCourse() && ltiApi.isPresent()) {
                ltiApi.get().createOnlineCourseConfiguration(existingCourse);
            }
            else {
                existingCourse.setOnlineCourseConfiguration(null);
            }
        }

        if (!Objects.equals(courseUpdateDTO.courseInformationSharingMessagingCodeOfConduct(), existingCourse.getCourseInformationSharingMessagingCodeOfConduct())) {
            conductAgreementService.resetUsersAgreeToCodeOfConductInCourse(existingCourse);
        }

        Course result = courseRepository.save(existingCourse);

        // if learning paths got enabled, generate learning paths for students
        if (!existingCourse.getLearningPathsEnabled() && courseUpdateDTO.learningPathsEnabled() && learningPathApi.isPresent()) {
            Course courseWithCompetencies = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(result.getId());
            Set<User> students = userRepository.getStudentsWithLearnerProfile(courseWithCompetencies);
            learnerProfileApi.ifPresent(api -> api.createCourseLearnerProfiles(courseWithCompetencies, students));
            learningPathApi.ifPresent(api -> api.generateLearningPaths(courseWithCompetencies));
        }

        // if access to restricted athena modules got disabled for the course, we need to set all exercises that use restricted modules to null
        if (athenaModuleAccessChanged && !courseUpdateDTO.restrictedAthenaModulesAccess()) {
            athenaApi.ifPresent(api -> api.revokeAccessToRestrictedFeedbackSuggestionModules(result));
        }

        if (timeZoneChanged && tutorialGroupChannelManagementApi.isPresent()) {
            tutorialGroupChannelManagementApi.get().onTimeZoneUpdate(result);
        }
        return ResponseEntity.ok(result);
    }

}
