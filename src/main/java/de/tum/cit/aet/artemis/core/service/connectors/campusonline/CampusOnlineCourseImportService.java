package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_GROUP_DEFAULT_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.CampusOnlineEnabled;
import de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseImportRequestDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineCourseMetadataResponseDTO;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCourseDTO;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponseDTO;

/**
 * Service for importing courses from CAMPUSOnline into Artemis and managing the link between
 * CAMPUSOnline courses and Artemis courses.
 * <p>
 * Provides functionality for:
 * <ul>
 * <li>Searching courses in a CAMPUSOnline organizational unit</li>
 * <li>Importing a CAMPUSOnline course as a new Artemis course</li>
 * <li>Linking/unlinking an existing Artemis course to a CAMPUSOnline course</li>
 * <li>Searching courses by name (typeahead)</li>
 * </ul>
 */
@Lazy
@Service
@Conditional(CampusOnlineEnabled.class)
@Profile(PROFILE_CORE)
public class CampusOnlineCourseImportService {

    private static final Logger log = LoggerFactory.getLogger(CampusOnlineCourseImportService.class);

    private static final String ENTITY_NAME = "campusOnline";

    private final CampusOnlineClientService campusOnlineClient;

    private final CourseRepository courseRepository;

    /**
     * Constructs the course import service with the required dependencies.
     *
     * @param campusOnlineClient the client service for CAMPUSOnline API calls
     * @param courseRepository   the repository for Artemis course persistence
     */
    public CampusOnlineCourseImportService(CampusOnlineClientService campusOnlineClient, CourseRepository courseRepository) {
        this.campusOnlineClient = campusOnlineClient;
        this.courseRepository = courseRepository;
    }

    /**
     * Searches for courses in a CAMPUSOnline organizational unit within a date range.
     *
     * @param orgUnitId the organizational unit ID
     * @param from      the start date (format: YYYY-MM-DD)
     * @param until     the end date (format: YYYY-MM-DD)
     * @return list of courses with import status
     */
    public List<CampusOnlineCourseDTO> searchCourses(String orgUnitId, String from, String until) {
        CampusOnlineOrgCoursesResponseDTO response = campusOnlineClient.fetchCoursesForOrg(orgUnitId, from, until);
        if (response.courses() == null) {
            return Collections.emptyList();
        }

        Set<String> alreadyImportedIds = getAlreadyImportedCourseIds();
        return response.courses().stream().map(c -> mapToDTO(c, alreadyImportedIds.contains(c.courseId()))).toList();
    }

    /**
     * Imports a course from CAMPUSOnline into Artemis.
     *
     * @param request the import request containing the CAMPUSOnline course ID and desired short name
     * @return a DTO representing the created course
     */
    public CampusOnlineCourseDTO importCourse(CampusOnlineCourseImportRequestDTO request) {
        checkDuplicateLink(request.campusOnlineCourseId());

        CampusOnlineCourseMetadataResponseDTO metadata = campusOnlineClient.fetchCourseMetadata(request.campusOnlineCourseId());

        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId(request.campusOnlineCourseId());

        Course course = new Course();
        course.setTitle(metadata.courseName());
        course.setShortName(request.shortName());
        course.setSemester(metadata.teachingTerm());
        course.setCampusOnlineConfiguration(config);
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        course.setMaxComplaints(3);
        course.setMaxComplaintTimeDays(7);
        course.setMaxComplaintTextLimit(2000);
        course.setMaxComplaintResponseTextLimit(2000);
        course.setAccuracyOfScores(1);

        // Build user group names from the short name, using Locale.ROOT to avoid locale-sensitive behavior
        String groupPrefix = ARTEMIS_GROUP_DEFAULT_PREFIX + request.shortName().toLowerCase(Locale.ROOT);
        course.setStudentGroupName(groupPrefix + "-students");
        course.setTeachingAssistantGroupName(groupPrefix + "-tutors");
        course.setEditorGroupName(groupPrefix + "-editors");
        course.setInstructorGroupName(groupPrefix + "-instructors");

        try {
            course = courseRepository.save(course);
        }
        catch (DataIntegrityViolationException e) {
            throw new BadRequestAlertException("This CAMPUSOnline course is already linked to an Artemis course", ENTITY_NAME, "alreadyLinked");
        }
        log.info("Imported CAMPUSOnline course '{}' (ID: {}) as Artemis course '{}' (ID: {})", metadata.courseName(), request.campusOnlineCourseId(), course.getTitle(),
                course.getId());
        return new CampusOnlineCourseDTO(config.getCampusOnlineCourseId(), course.getTitle(), course.getSemester(), null, null, null, null, false);
    }

    /**
     * Searches for courses by name within the specified organizational unit.
     * Used by the typeahead in the course form to find matching CAMPUSOnline courses.
     *
     * @param query     the course name query
     * @param orgUnitId the organizational unit ID to search in
     * @param semester  the semester filter (e.g. "2025W"), can be null
     * @return list of matching courses with metadata
     */
    public List<CampusOnlineCourseDTO> searchCoursesByName(String query, String orgUnitId, String semester) {
        if (query == null || query.isBlank()) {
            log.warn("Cannot search CAMPUSOnline courses: search query is empty");
            return Collections.emptyList();
        }
        if (orgUnitId == null || orgUnitId.isBlank()) {
            log.warn("Cannot search CAMPUSOnline courses: org unit ID not provided");
            return Collections.emptyList();
        }

        // Use a broad date range for the current academic period
        String from = semester != null ? semesterToFromDate(semester) : LocalDate.now().minusYears(1).toString();
        String until = semester != null ? semesterToUntilDate(semester) : LocalDate.now().plusYears(1).toString();

        CampusOnlineOrgCoursesResponseDTO response = campusOnlineClient.fetchCoursesForOrg(orgUnitId, from, until);
        if (response.courses() == null) {
            return Collections.emptyList();
        }

        Set<String> alreadyImportedIds = getAlreadyImportedCourseIds();

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return response.courses().stream().filter(c -> c.courseName() != null && c.courseName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .map(c -> mapToDTO(c, alreadyImportedIds.contains(c.courseId()))).toList();
    }

    /**
     * Links a CAMPUSOnline configuration to an existing course.
     *
     * @param courseId              the Artemis course ID
     * @param campusOnlineCourseId  the CAMPUSOnline course ID
     * @param responsibleInstructor the responsible instructor name
     * @param department            the department name
     * @param studyProgram          the study program name
     * @return a DTO with the linked course info
     */
    public CampusOnlineCourseDTO linkCourse(long courseId, String campusOnlineCourseId, String responsibleInstructor, String department, String studyProgram) {
        if (campusOnlineCourseId == null || campusOnlineCourseId.isBlank()) {
            throw new BadRequestAlertException("CAMPUSOnline course ID must not be blank", ENTITY_NAME, "campusOnlineCourseIdRequired");
        }
        checkDuplicateLink(campusOnlineCourseId);

        Course course = courseRepository.findByIdForUpdateElseThrow(courseId);

        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId(campusOnlineCourseId);
        config.setResponsibleInstructor(responsibleInstructor);
        config.setDepartment(department);
        config.setStudyProgram(studyProgram);

        course.setCampusOnlineConfiguration(config);
        try {
            course = courseRepository.save(course);
        }
        catch (DataIntegrityViolationException e) {
            throw new BadRequestAlertException("This CAMPUSOnline course is already linked to an Artemis course", ENTITY_NAME, "alreadyLinked");
        }
        return new CampusOnlineCourseDTO(campusOnlineCourseId, course.getTitle(), course.getSemester(), null, responsibleInstructor, department, studyProgram, false);
    }

    /**
     * Removes the CAMPUSOnline configuration from a course.
     *
     * @param courseId the Artemis course ID
     */
    public void unlinkCourse(long courseId) {
        Course course = courseRepository.findByIdForUpdateElseThrow(courseId);
        course.setCampusOnlineConfiguration(null);
        courseRepository.save(course);
    }

    /**
     * Validates that a CAMPUSOnline course ID is not already linked to any Artemis course.
     *
     * @param campusOnlineCourseId the CAMPUSOnline course ID to check
     * @throws BadRequestAlertException if the course ID is already linked
     */
    private void checkDuplicateLink(String campusOnlineCourseId) {
        if (courseRepository.existsByCampusOnlineCourseId(campusOnlineCourseId)) {
            throw new BadRequestAlertException("This CAMPUSOnline course is already linked to an Artemis course", ENTITY_NAME, "alreadyLinked");
        }
    }

    /**
     * Retrieves the set of CAMPUSOnline course IDs that are already imported into Artemis.
     * Used to mark courses as "already imported" in the search results.
     *
     * @return set of CAMPUSOnline course IDs that have a corresponding Artemis course
     */
    private Set<String> getAlreadyImportedCourseIds() {
        return courseRepository.findAllWithCampusOnlineConfiguration().stream().map(c -> c.getCampusOnlineConfiguration().getCampusOnlineCourseId()).collect(Collectors.toSet());
    }

    /**
     * Converts a semester string (e.g. "2025W" or "2025S") to the start date of that semester.
     * Winter semesters start on October 1st, summer semesters on April 1st.
     * Falls back to one year ago if the semester format is invalid.
     *
     * @param semester the semester identifier (format: YYYYW or YYYYS)
     * @return the start date as a string in YYYY-MM-DD format
     */
    private String semesterToFromDate(String semester) {
        try {
            String year = semester.substring(0, 4);
            // Validate that the year portion is actually numeric
            Integer.parseInt(year);
            if (semester.endsWith("W")) {
                return year + "-10-01";
            }
            return year + "-04-01";
        }
        catch (StringIndexOutOfBoundsException | NumberFormatException e) {
            return LocalDate.now().minusYears(1).toString();
        }
    }

    /**
     * Converts a semester string (e.g. "2025W" or "2025S") to the end date of that semester.
     * Winter semesters end on March 31st of the following year, summer semesters on September 30th.
     * Falls back to one year from now if the semester format is invalid.
     *
     * @param semester the semester identifier (format: YYYYW or YYYYS)
     * @return the end date as a string in YYYY-MM-DD format
     */
    private String semesterToUntilDate(String semester) {
        try {
            String year = semester.substring(0, 4);
            if (semester.endsWith("W")) {
                return (Integer.parseInt(year) + 1) + "-03-31";
            }
            // For summer semester, validate that year is numeric
            Integer.parseInt(year);
            return year + "-09-30";
        }
        catch (StringIndexOutOfBoundsException | NumberFormatException e) {
            return LocalDate.now().plusYears(1).toString();
        }
    }

    /**
     * Maps a CAMPUSOnline organization course DTO to the internal course DTO used by the REST API.
     *
     * @param orgCourse       the CAMPUSOnline course data from the organization endpoint
     * @param alreadyImported whether this course is already linked to an Artemis course
     * @return the mapped course DTO
     */
    private CampusOnlineCourseDTO mapToDTO(CampusOnlineOrgCourseDTO orgCourse, boolean alreadyImported) {
        return new CampusOnlineCourseDTO(orgCourse.courseId(), orgCourse.courseName(), orgCourse.teachingTerm(), null, null, null, null, alreadyImported);
    }
}
