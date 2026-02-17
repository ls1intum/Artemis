package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private CampusOnlineCourseDTO mapToDTO(CampusOnlineOrgCourse orgCourse, boolean alreadyImported) {
        return new CampusOnlineCourseDTO(orgCourse.courseId(), orgCourse.courseName(), orgCourse.teachingTerm(), null, alreadyImported);
    }
}
