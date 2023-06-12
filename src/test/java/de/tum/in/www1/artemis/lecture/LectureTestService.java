package de.tum.in.www1.artemis.lecture;

import java.util.HashSet;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.util.ModelFactory;

@Service
public class LectureTestService {

    /**
     * Moved to {@link de.tum.in.www1.artemis.lecture.LectureTestService#createCourseWithLecture(boolean) LectureTestService}
     */
    public Lecture createCourseWithLecture(boolean saveLecture) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        courseRepo.save(course);
        if (saveLecture) {
            lectureRepo.save(lecture);
        }
        return lecture;
    }
}
