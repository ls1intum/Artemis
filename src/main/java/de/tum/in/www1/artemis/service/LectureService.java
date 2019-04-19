package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.LectureRepository;

@Service
@Transactional
public class LectureService {

    private LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    public LectureService(LectureRepository lectureRepository, UserService userService, AuthorizationCheckService authCheckService) {
        this.lectureRepository = lectureRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
    }

    public List<Lecture> findAllByCourseId(Long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

    public Lecture filterActiveAttachments(Lecture lectureWithAttachments) {
        User user = userService.getUserWithGroupsAndAuthorities();
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

    public Set<Lecture> filterActiveAttachments(Set<Lecture> lecturesWithAttachments) {
        Set<Lecture> lecturesWithFilteredAttachments = new HashSet<>();
        for (Lecture lecture : lecturesWithAttachments) {
            lecturesWithFilteredAttachments.add(filterActiveAttachments(lecture));
        }
        return lecturesWithFilteredAttachments;
    }

}
