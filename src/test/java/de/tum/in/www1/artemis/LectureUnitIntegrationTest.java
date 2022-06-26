package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.*;
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

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

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

        this.textUnit = database.createTextUnit();
        this.textUnit2 = database.createTextUnit();
        AttachmentUnit attachmentUnit = database.createAttachmentUnit(false);
        OnlineUnit onlineUnit = database.createOnlineUnit();
        // textUnit3 is not one of the lecture units connected to the lecture
        this.textUnit3 = database.createTextUnit();

        this.lecture1 = database.addLectureUnitsToLecture(this.lecture1, Set.of(this.textUnit, onlineUnit, this.textUnit2, attachmentUnit));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId());
        this.textUnit = textUnitRepository.findById(this.textUnit.getId()).get();
        this.textUnit2 = textUnitRepository.findById(textUnit2.getId()).get();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureUnit() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().get(0).getId();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnitId, HttpStatus.OK);
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnitId);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureUnit_shouldRemoveCompletions() throws Exception {
        var lectureUnit = lecture1.getLectureUnits().get(0);
        var user = userRepo.findOneByLogin("student1").get();

        LectureUnitCompletion completion = new LectureUnitCompletion();
        completion.setLectureUnit(lectureUnit);
        completion.setUser(user);
        completion.setCompletedAt(ZonedDateTime.now().minusDays(1));
        lectureUnitCompletionRepository.save(completion);

        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId())).isPresent();

        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnit.getId(), HttpStatus.OK);

        this.lecture1 = lectureRepository.findByIdWithLectureUnitsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnit.getId());
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void deleteLectureUnit_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().get(0).getId();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnitId, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureUnit_notPartOfLecture_shouldReturnNotFound() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().get(0).getId();
        request.delete("/api/lectures/" + Integer.MAX_VALUE + "/lecture-units/" + lectureUnitId, HttpStatus.CONFLICT);
    }

    /**
     * We have to make sure to reorder the list of lecture units when we delete a lecture unit to prevent hibernate
     * from entering nulls into the list to keep the order of lecture units
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureUnit_FirstLectureUnit_ShouldReorderList() throws Exception {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndLearningGoalsElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits()).hasSize(4);
        LectureUnit firstLectureUnit = lecture.getLectureUnits().stream().findFirst().get();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + firstLectureUnit.getId(), HttpStatus.OK);
        lecture = lectureRepository.findByIdWithLectureUnitsAndLearningGoalsElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits()).hasSize(3);
        boolean nullFound = false;
        for (LectureUnit lectureUnit : lecture.getLectureUnits()) {
            if (Objects.isNull(lectureUnit)) {
                nullFound = true;
                break;
            }
        }
        assertThat(nullFound).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_asInstructor_shouldUpdateLectureUnitOrder() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toList());
        Collections.swap(newlyOrderedList, 0, 1);
        List<LectureUnit> returnedList = request.putWithResponseBodyList("/api/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, LectureUnit.class,
                HttpStatus.OK);
        assertThat(returnedList.get(0).getId()).isEqualTo(newlyOrderedList.get(0));
        assertThat(returnedList.get(1).getId()).isEqualTo(newlyOrderedList.get(1));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_wrongSizeOfIds_shouldReturnConflict() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toList());
        newlyOrderedList.remove(0);
        request.put("/api/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_newTextUnitInOrderedList_shouldReturnConflict() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toList());
        // textUnit3 is not in specified lecture
        newlyOrderedList.set(1, this.textUnit3.getId());
        request.put("/api/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureUnitOrder_asInstructorWithWrongLectureId_shouldReturnNotFound() throws Exception {
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
    @WithMockUser(username = "student1", roles = "USER")
    public void setLectureUnitCompletion_lectureUnitNotPartOfLecture_shouldReturnConflict() throws Exception {
        request.postWithoutLocation("/api/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit3.getId() + "/completion?completed=true", null, HttpStatus.CONFLICT,
                null);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void setLectureUnitCompletion_lectureUnitNotVisible_shouldReturnConflict() throws Exception {
        this.textUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        textUnitRepository.save(this.textUnit);
        request.postWithoutLocation("/api/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit.getId() + "/completion?completed=true", null, HttpStatus.CONFLICT,
                null);
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void setLectureUnitCompletion_shouldReturnForbidden() throws Exception {
        // User is not in same course as lecture unit
        request.postWithoutLocation("/api/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().get(0).getId() + "/completion?completed=true", null,
                HttpStatus.FORBIDDEN, null);
    }

}
