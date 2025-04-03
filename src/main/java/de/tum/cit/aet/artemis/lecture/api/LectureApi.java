package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseContentCount;
import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureService;

/**
 * API for managing lectures.
 */
@Profile(PROFILE_CORE)
@Controller
public class LectureApi extends AbstractLectureApi {

    private final LectureImportService lectureImportService;

    private final LectureService lectureService;

    private final LectureRepository lectureRepository;

    public LectureApi(LectureImportService lectureImportService, LectureService lectureService, LectureRepository lectureRepository) {
        this.lectureImportService = lectureImportService;
        this.lectureService = lectureService;
        this.lectureRepository = lectureRepository;
    }

    public Optional<Lecture> findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow(String title, long courseId) throws NoUniqueQueryException {
        return lectureRepository.findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow(title, courseId);
    }

    public void filterVisibleLecturesWithActiveAttachments(Course course, Set<Lecture> lecturesWithAttachments, User user) {
        lectureService.filterVisibleLecturesWithActiveAttachments(course, lecturesWithAttachments, user);
    }

    public Set<CourseContentCount> countVisibleLectures(Set<Long> courseIds, ZonedDateTime now) {
        return lectureRepository.countVisibleLectures(courseIds, now);
    }

    public Set<Lecture> findAllByCourseId(long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

    public Optional<Lecture> findById(Long lectureId) {
        return lectureRepository.findById(lectureId);
    }

    public Lecture findByIdWithLectureUnitsElseThrow(Long lectureId) {
        return lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId);
    }

    public long countByCourseId(long courseId) {
        return lectureRepository.countByCourse_Id(courseId);
    }

    public Lecture findByIdElseThrow(long lectureId) {
        return lectureRepository.findByIdElseThrow(lectureId);
    }

    public String getLectureTitle(long lectureId) {
        return lectureRepository.getLectureTitle(lectureId);
    }

    public void saveAll(Collection<Lecture> lectures) {
        lectureRepository.saveAll(lectures);
    }

    public Lecture importLecture(final Lecture importedLecture, final Course course, boolean importLectureUnits) {
        return lectureImportService.importLecture(importedLecture, course, importLectureUnits);
    }

    public void delete(Lecture lecture, boolean updateCompetencyProgress) {
        lectureService.delete(lecture, updateCompetencyProgress);
    }
}
