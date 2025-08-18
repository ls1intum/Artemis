package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.SecureRandom;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

@Lazy
@Service
@Profile(PROFILE_CORE)
public class CalendarSubscriptionService {

    public enum CalendarEventFilterOption {
        LECTURES, TUTORIALS, EXAMS, EXERCISES
    }

    private final CourseRepository courseRepository;

    CalendarSubscriptionService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public String getICSFileAsString(long courseId, boolean userIsStudent, Set<CalendarEventFilterOption> filterOptions) {
        return "";
    }

    public String getCourseStaffToken(Course course) {
        String courseStaffToken = course.getCourseStaffCalendarSubscriptionToken();
        if (courseStaffToken != null) {
            return courseStaffToken;
        }
        String otherToken = course.getStudentCalendarSubscriptionToken();
        courseStaffToken = generateSubscriptionTokenUnequalToOtherToken(otherToken);
        course.setCourseStaffCalendarSubscriptionToken(courseStaffToken);
        courseRepository.save(course);
        return courseStaffToken;
    }

    public String getStudentToken(Course course) {
        String studentToken = course.getStudentCalendarSubscriptionToken();
        if (studentToken != null) {
            return studentToken;
        }
        String otherToken = course.getCourseStaffCalendarSubscriptionToken();
        studentToken = generateSubscriptionTokenUnequalToOtherToken(otherToken);
        course.setStudentCalendarSubscriptionToken(studentToken);
        courseRepository.save(course);
        return studentToken;
    }

    private String generateSubscriptionTokenUnequalToOtherToken(String otherToken) {
        String newToken;
        do {
            byte[] newTokenBytes = generateSubscriptionTokenBytes();
            newToken = convertBytesToSubscriptionToken(newTokenBytes);
        }
        while (newToken.equals(otherToken));
        return newToken;
    }

    private byte[] generateSubscriptionTokenBytes() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return bytes;
    }

    private String convertBytesToSubscriptionToken(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
