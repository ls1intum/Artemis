package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

public class LectureUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Lecture lecture1;

    private TextUnit textUnit;

    private TextUnit textUnit2;

    private TextUnit textUnit3;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/lecture-units-order", List.of(), HttpStatus.FORBIDDEN);
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/0", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(10, 10, 0, 10);
        List<Course> courses = this.database.createCoursesWithExercisesAndLectures(true);
        Course course1 = this.courseRepository.findByIdWithExercisesAndLecturesElseThrow(courses.get(0).getId());
        this.lecture1 = course1.getLectures().stream().findFirst().get();

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("student42"));
        userRepo.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor42"));

        this.textUnit = textUnitRepository.save(new TextUnit());
        this.textUnit2 = textUnitRepository.save(new TextUnit());
        // textUnit3 is not one of the lecture units connected to the lecture
        this.textUnit3 = textUnitRepository.save(new TextUnit());

        List<LectureUnit> lectureUnits = List.of(this.textUnit, this.textUnit2);

        this.lecture1 = lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId());

        for (LectureUnit lectureUnit : lectureUnits) {
            this.lecture1.addLectureUnit(lectureUnit);
        }
        this.lecture1 = lectureRepository.save(lecture1);
        this.textUnit = textUnitRepository.findById(this.textUnit.getId()).get();
        this.textUnit2 = textUnitRepository.findById(textUnit2.getId()).get();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_asInstructor_shouldUpdateLectureUnitOrder() throws Exception {
        long idOfOriginalFirstPosition = lecture1.getLectureUnits().get(0).getId();
        long idOfOriginalSecondPosition = lecture1.getLectureUnits().get(1).getId();
        List<Long> newlyOrderedList = new ArrayList<>();
        newlyOrderedList.add(idOfOriginalFirstPosition);
        newlyOrderedList.add(idOfOriginalSecondPosition);
        Collections.swap(newlyOrderedList, 0, 1);
        List<TextUnit> returnedList = request.putWithResponseBodyList("/api/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, TextUnit.class,
                HttpStatus.OK);
        assertThat(returnedList.get(0).getId()).isEqualTo(idOfOriginalSecondPosition);
        assertThat(returnedList.get(1).getId()).isEqualTo(idOfOriginalFirstPosition);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_asInstructorNewTextUnitInOrderedList_shouldReturnConflict() throws Exception {
        List<Long> newlyOrderedList = new ArrayList<>();
        newlyOrderedList.add(textUnit.getId());
        newlyOrderedList.add(textUnit2.getId());
        newlyOrderedList.add(textUnit3.getId());
        request.put("/api/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_asInstructorWithWrongLectureInd_shouldReturnNotFound() throws Exception {
        request.put("/api/lectures/" + 0L + "/lecture-units-order", List.of(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/lecture-units-order", List.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void setLectureUnitCompletion() throws Exception {
        // Set lecture unit as completed for current user
        request.postWithoutLocation("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().get(0).getId() + "/completion?completed=true", null,
                HttpStatus.OK, null);

        this.lecture1 = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsAndCompletionsElseThrow(lecture1.getId());
        LectureUnit lectureUnit = this.lecture1.getLectureUnits().get(0);

        assertThat(lectureUnit.getCompletedUsers()).isNotEmpty();
        assertThat(lectureUnit.isCompletedFor(userRepo.getUser())).isTrue();
        assertThat(lectureUnit.getCompletionDate(userRepo.getUser())).isNotNull();

        // Set lecture unit as uncompleted for user
        request.postWithoutLocation("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().get(0).getId() + "/completion?completed=false", null,
                HttpStatus.OK, null);

        this.lecture1 = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsAndCompletionsElseThrow(lecture1.getId());
        lectureUnit = this.lecture1.getLectureUnits().get(0);

        assertThat(lectureUnit.getCompletedUsers()).isEmpty();
        assertThat(lectureUnit.isCompletedFor(userRepo.getUser())).isFalse();
        assertThat(lectureUnit.getCompletionDate(userRepo.getUser())).isEmpty();
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void setLectureUnitCompletion_shouldReturnForbidden() throws Exception {
        // User is not in same course as lecture unit
        request.postWithoutLocation("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().get(0).getId() + "/completion?completed=true", null,
                HttpStatus.FORBIDDEN, null);
    }

}
