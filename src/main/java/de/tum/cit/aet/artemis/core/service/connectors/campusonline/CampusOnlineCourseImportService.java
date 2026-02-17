package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.CampusOnlineEnabled;
import de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseImportRequestDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineCourseMetadataResponse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCourse;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCoursesResponse;

@Lazy
@Service
@Conditional(CampusOnlineEnabled.class)
@Profile(PROFILE_CORE)
public class CampusOnlineCourseImportService {

    private static final Logger log = LoggerFactory.getLogger(CampusOnlineCourseImportService.class);

    @Value("${artemis.campus-online.default-org-unit-id:}")
    private String defaultOrgUnitId;

    private final CampusOnlineClient campusOnlineClient;

    private final CourseRepository courseRepository;

    public CampusOnlineCourseImportService(CampusOnlineClient campusOnlineClient, CourseRepository courseRepository) {
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
        CampusOnlineOrgCoursesResponse response = campusOnlineClient.fetchCoursesForOrg(orgUnitId, from, until);
        if (response.courses() == null) {
            return Collections.emptyList();
        }

        Set<String> alreadyImportedIds = courseRepository.findAllWithCampusOnlineConfiguration().stream().map(c -> c.getCampusOnlineConfiguration().getCampusOnlineCourseId())
                .collect(Collectors.toSet());

        return response.courses().stream().map(c -> mapToDTO(c, alreadyImportedIds.contains(c.courseId()))).toList();
    }

    /**
     * Imports a course from CAMPUSOnline into Artemis.
     *
     * @param request the import request containing the CAMPUSOnline course ID and desired short name
     * @return the created Artemis course
     */
    public Course importCourse(CampusOnlineCourseImportRequestDTO request) {
        CampusOnlineCourseMetadataResponse metadata = campusOnlineClient.fetchCourseMetadata(request.campusOnlineCourseId());

        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId(request.campusOnlineCourseId());

        Course course = new Course();
        course.setTitle(metadata.courseName());
        course.setShortName(request.shortName());
        course.setSemester(metadata.teachingTerm());
        course.setCampusOnlineConfiguration(config);

        String groupPrefix = "artemis-" + request.shortName().toLowerCase();
        course.setStudentGroupName(groupPrefix + "-students");
        course.setTeachingAssistantGroupName(groupPrefix + "-tutors");
        course.setEditorGroupName(groupPrefix + "-editors");
        course.setInstructorGroupName(groupPrefix + "-instructors");

        course = courseRepository.save(course);
        log.info("Imported CAMPUSOnline course '{}' (ID: {}) as Artemis course '{}' (ID: {})", metadata.courseName(), request.campusOnlineCourseId(), course.getTitle(),
                course.getId());
        return course;
    }

    /**
     * Searches for courses by name within the default organizational unit.
     * Used by the typeahead in the course form to find matching CAMPUSOnline courses.
     *
     * @param query    the course name query
     * @param semester the semester filter (e.g. "2025W"), can be null
     * @return list of matching courses with metadata
     */
    public List<CampusOnlineCourseDTO> searchCoursesByName(String query, String semester) {
        if (defaultOrgUnitId == null || defaultOrgUnitId.isBlank()) {
            log.warn("Cannot search CAMPUSOnline courses: default org unit ID not configured");
            return Collections.emptyList();
        }

        // Use a broad date range for the current academic period
        String from = semester != null ? semesterToFromDate(semester) : LocalDate.now().minusYears(1).toString();
        String until = semester != null ? semesterToUntilDate(semester) : LocalDate.now().plusYears(1).toString();

        CampusOnlineOrgCoursesResponse response = campusOnlineClient.fetchCoursesForOrg(defaultOrgUnitId, from, until);
        if (response.courses() == null) {
            return Collections.emptyList();
        }

        Set<String> alreadyImportedIds = courseRepository.findAllWithCampusOnlineConfiguration().stream().map(c -> c.getCampusOnlineConfiguration().getCampusOnlineCourseId())
                .collect(Collectors.toSet());

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
     * @return the updated course
     */
    public Course linkCourse(long courseId, String campusOnlineCourseId, String responsibleInstructor, String department, String studyProgram) {
        if (campusOnlineCourseId == null || campusOnlineCourseId.isBlank()) {
            throw new IllegalArgumentException("CAMPUSOnline course ID must not be blank");
        }
        Course course = courseRepository.findByIdForUpdateElseThrow(courseId);

        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId(campusOnlineCourseId);
        config.setResponsibleInstructor(responsibleInstructor);
        config.setDepartment(department);
        config.setStudyProgram(studyProgram);

        course.setCampusOnlineConfiguration(config);
        return courseRepository.save(course);
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

    private String semesterToFromDate(String semester) {
        // semester format: "2025W" (winter) or "2025S" (summer)
        try {
            String year = semester.substring(0, 4);
            if (semester.endsWith("W")) {
                return year + "-10-01";
            }
            return year + "-04-01";
        }
        catch (StringIndexOutOfBoundsException | NumberFormatException e) {
            return LocalDate.now().minusYears(1).toString();
        }
    }

    private String semesterToUntilDate(String semester) {
        try {
            String year = semester.substring(0, 4);
            if (semester.endsWith("W")) {
                return (Integer.parseInt(year) + 1) + "-03-31";
            }
            return year + "-09-30";
        }
        catch (StringIndexOutOfBoundsException | NumberFormatException e) {
            return LocalDate.now().plusYears(1).toString();
        }
    }

    private CampusOnlineCourseDTO mapToDTO(CampusOnlineOrgCourse orgCourse, boolean alreadyImported) {
        return new CampusOnlineCourseDTO(orgCourse.courseId(), orgCourse.courseName(), orgCourse.teachingTerm(), null, null, null, null, alreadyImported);
    }
}
