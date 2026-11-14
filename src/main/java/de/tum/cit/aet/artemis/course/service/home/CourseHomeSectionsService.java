package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeCommunicationDTO;
import de.tum.cit.aet.artemis.communication.dto.CourseHomeQuestionsDTO;
import de.tum.cit.aet.artemis.communication.service.CourseHomeCommunicationService;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;
import de.tum.cit.aet.artemis.exam.api.ExamCourseHomeApi;
import de.tum.cit.aet.artemis.exam.dto.ExamCourseHomeDTO;
import de.tum.cit.aet.artemis.iris.api.IrisCourseHomeApi;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseHomeSessionsDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureCourseHomeApi;
import de.tum.cit.aet.artemis.lecture.dto.LectureCourseHomeDTO;

/** Isolates optional section availability from the core Home snapshot assembly. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeSectionsService {

    private final CourseHomeCommunicationService communication;

    private final Optional<LectureCourseHomeApi> lectureApi;

    private final Optional<ExamCourseHomeApi> examApi;

    private final Optional<IrisCourseHomeApi> irisApi;

    public CourseHomeSectionsService(CourseHomeCommunicationService communication, Optional<LectureCourseHomeApi> lectureApi, Optional<ExamCourseHomeApi> examApi,
            Optional<IrisCourseHomeApi> irisApi) {
        this.communication = communication;
        this.lectureApi = lectureApi;
        this.examApi = examApi;
        this.irisApi = irisApi;
    }

    /**
     * Loads bounded lectures metadata, keeping database outages local to this section.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated student
     * @param now      the snapshot timestamp
     * @param since    the preserved publication baseline
     * @return the section summary with an explicit availability state
     */
    public LectureCourseHomeDTO lectures(long courseId, User user, Instant now, Instant since) {
        if (lectureApi.isEmpty()) {
            return new LectureCourseHomeDTO(CourseHomeSectionStatus.DISABLED, List.of());
        }
        try {
            return lectureApi.get().load(courseId, user, now, since);
        }
        catch (DataAccessException ex) {
            return new LectureCourseHomeDTO(CourseHomeSectionStatus.UNAVAILABLE, List.of());
        }
    }

    /**
     * Loads bounded exams metadata, keeping database outages local to this section.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated student
     * @param now      the snapshot timestamp
     * @param since    the preserved publication baseline
     * @return the section summary with an explicit availability state
     */
    public ExamCourseHomeDTO exams(long courseId, User user, Instant now, Instant since) {
        if (examApi.isEmpty()) {
            return new ExamCourseHomeDTO(CourseHomeSectionStatus.DISABLED, List.of());
        }
        try {
            return examApi.get().load(courseId, user, now, since);
        }
        catch (DataAccessException ex) {
            return new ExamCourseHomeDTO(CourseHomeSectionStatus.UNAVAILABLE, List.of());
        }
    }

    /**
     * Loads bounded discussions metadata, keeping database outages local to this section.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated student
     * @param now      the snapshot timestamp
     * @param since    the preserved publication baseline
     * @return the section summary with an explicit availability state
     */
    public CourseHomeCommunicationDTO discussions(long courseId, User user, Instant now, Instant since) {
        try {
            return communication.load(courseId, user, now, since);
        }
        catch (DataAccessException ex) {
            return new CourseHomeCommunicationDTO(new CourseHomeQuestionsDTO(CourseHomeSectionStatus.UNAVAILABLE, List.of()), null, List.of());
        }
    }

    /**
     * Loads bounded chats metadata, keeping database outages local to this section.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated student
     * @param now      the snapshot timestamp
     * @param since    the preserved publication baseline
     * @param enabled  whether the course exposes Iris
     * @return the section summary with an explicit availability state
     */
    public IrisCourseHomeSessionsDTO chats(long courseId, User user, Instant now, Instant since, boolean enabled) {
        if (irisApi.isEmpty() || !enabled) {
            return new IrisCourseHomeSessionsDTO(CourseHomeSectionStatus.DISABLED, List.of());
        }
        try {
            return irisApi.get().load(courseId, user, now, since);
        }
        catch (DataAccessException ex) {
            return new IrisCourseHomeSessionsDTO(CourseHomeSectionStatus.UNAVAILABLE, List.of());
        }
    }

}
