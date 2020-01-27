package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.LectureRepository;

@Service
public class LectureService {

    private LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    public LectureService(LectureRepository lectureRepository, AuthorizationCheckService authCheckService) {
        this.lectureRepository = lectureRepository;
        this.authCheckService = authCheckService;
    }

    public Set<Lecture> findAllByCourseId(Long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

    /**
     * Finds all Lectures for a given Course
     *
     * @param course corresponding course
     * @param user the user entity
     * @return a List of all Lectures for the given course
     */
    public Set<Lecture> findAllForCourse(Course course, User user) {
        Set<Lecture> lectures = lectureRepository.findAllByCourseId(course.getId());
        if (authCheckService.isOnlyStudentInCourse(course, user)) {
            lectures = filterActiveAttachments(lectures, user);
        }
        return lectures;
    }

    /**
     * For tutors, admins and instructors returns  lecture with all attachments, for students lecture with only active attachments
     *
     * @param lectureWithAttachments lecture that has attachments
     * @param user the user for which this call should filter
     * @return lecture with filtered attachments
     */
    public Lecture filterActiveAttachments(Lecture lectureWithAttachments, User user) {
        Course course = lectureWithAttachments.getCourse();
        if (user.getGroups().contains(course.getTeachingAssistantGroupName()) || user.getGroups().contains(course.getInstructorGroupName()) || authCheckService.isAdmin()) {
            return lectureWithAttachments;
        }

        HashSet<Attachment> filteredAttachments = new HashSet<>();
        for (Attachment attachment : lectureWithAttachments.getAttachments()) {
            if (attachment.getReleaseDate() == null || attachment.getReleaseDate().isBefore(ZonedDateTime.now())) {
                filteredAttachments.add(attachment);
            }
        }
        lectureWithAttachments.setAttachments(filteredAttachments);
        return lectureWithAttachments;
    }

    /**
     * Filter active attachments for a set of lectures.
     *
     * @param lecturesWithAttachments lectures that have attachments
     * @param user the user for which this call should filter
     * @return lectures with filtered attachments
     */
    public Set<Lecture> filterActiveAttachments(Set<Lecture> lecturesWithAttachments, User user) {
        Set<Lecture> lecturesWithFilteredAttachments = new HashSet<>();
        for (Lecture lecture : lecturesWithAttachments) {
            lecturesWithFilteredAttachments.add(filterActiveAttachments(lecture, user));
        }
        return lecturesWithFilteredAttachments;
    }

    public void delete(Lecture lecture) {
        lectureRepository.delete(lecture);
    }

}
