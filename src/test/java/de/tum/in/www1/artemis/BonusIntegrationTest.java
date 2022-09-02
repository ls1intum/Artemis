package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.BonusRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

class BonusIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    private Bonus courseBonus;

    private Bonus examBonus;

    private Course course;

    private GradingScale bonusToExamGradingScale;

    private GradingScale sourceExamGradingScale;

    private GradingScale courseGradingScale;

    /**
     * Initialize variables
     */
    @BeforeEach
    void init() {
        database.addUsers(0, 0, 0, 1);
        course = database.addEmptyCourse();
        Exam targetExam = database.addExamWithExerciseGroup(course, true);
        Exam sourceExam = database.addExamWithExerciseGroup(course, true);
        bonusToExamGradingScale = new GradingScale();
        bonusToExamGradingScale.setGradeType(GradeType.GRADE);
        bonusToExamGradingScale.setExam(targetExam);

        sourceExamGradingScale = new GradingScale();
        sourceExamGradingScale.setGradeType(GradeType.BONUS);
        sourceExamGradingScale.setExam(sourceExam);

        courseGradingScale = new GradingScale();
        courseGradingScale.setGradeType(GradeType.BONUS);
        courseGradingScale.setCourse(course);

        gradingScaleRepository.saveAll(List.of(bonusToExamGradingScale, sourceExamGradingScale, courseGradingScale));

        courseBonus = ModelFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, 1.0, courseGradingScale.getId(), bonusToExamGradingScale.getId());
        bonusRepository.save(courseBonus);

        examBonus = ModelFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, 1.0, sourceExamGradingScale.getId(), bonusToExamGradingScale.getId());

        bonusToExamGradingScale.setBonusStrategy(BonusStrategy.GRADES_CONTINUOUS);
        gradingScaleRepository.save(bonusToExamGradingScale);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetBonusSourcesForTargetExamNotFound() throws Exception {
        bonusRepository.delete(courseBonus);

        request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.NOT_FOUND, Bonus.class);

    }

    private void assertBonusesAreEqualIgnoringId(Bonus actualBonus, Bonus expectedBonus) {
        assertThat(actualBonus).usingRecursiveComparison().ignoringFields("id", "sourceGradingScale", "bonusToGradingScale").isEqualTo(expectedBonus);
        assertThat(actualBonus.getSourceGradingScale().getId()).isEqualTo(expectedBonus.getSourceGradingScale().getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetBonusForTargetExam() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, Bonus.class);

        assertThat(foundBonus.getId()).isEqualTo(courseBonus.getId());
        assertBonusesAreEqualIgnoringId(foundBonus, courseBonus);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetBonusSource() throws Exception {

        Bonus foundBonus = request.get("/api/bonus/" + courseBonus.getId(), HttpStatus.OK, Bonus.class);

        assertThat(foundBonus.getId()).isEqualTo(courseBonus.getId());
        assertBonusesAreEqualIgnoringId(foundBonus, courseBonus);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusSourceForTargetExam() throws Exception {
        bonusRepository.delete(courseBonus);

        Exam newExam = database.addExamWithExerciseGroup(course, true);
        var newExamGradingScale = new GradingScale();
        newExamGradingScale.setGradeType(GradeType.BONUS);
        newExamGradingScale.setExam(newExam);
        gradingScaleRepository.save(newExamGradingScale);

        Bonus newBonus = ModelFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, -1.0, newExamGradingScale.getId(), bonusToExamGradingScale.getId());

        Bonus savedBonus = request.postWithResponseBody(
                "/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus", newBonus, Bonus.class,
                HttpStatus.CREATED);

        assertThat(savedBonus.getId()).isGreaterThan(0);
        assertBonusesAreEqualIgnoringId(savedBonus, newBonus);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusSourceForTargetExamDuplicateError() throws Exception {

        Bonus savedBonus = request.postWithResponseBody(
                "/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus", examBonus, Bonus.class,
                HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetBonusSources() throws Exception {

        Bonus foundBonus = request.get("/api/bonus/" + courseBonus.getId(), HttpStatus.OK, Bonus.class);

        assertThat(foundBonus.getId()).isEqualTo(courseBonus.getId());
        assertBonusesAreEqualIgnoringId(foundBonus, courseBonus);
    }

    // TODO: Ata: Add student tests that fail due to Unauthorized

    // @Test
    // @WithMockUser(username = "student1", roles = "USER")
    // void deleteTextExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
    // final Course course = database.addCourseWithOneReleasedTextExercise();
    // TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).get(0);
    // course.setInstructorGroupName("test");
    // courseRepository.save(course);
    //
    // request.delete("/api/text-exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN);
    // }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithoutChangingSourceGradingScale() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, Bonus.class);

        BonusStrategy newBonusStrategy = BonusStrategy.POINTS;
        foundBonus.setBonusStrategy(newBonusStrategy);
        double newWeight = -foundBonus.getWeight();
        foundBonus.setWeight(newWeight);

        request.put("/api/bonus/", foundBonus, HttpStatus.OK);
        Bonus updatedBonus = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, Bonus.class);
        assertThat(updatedBonus.getId()).isEqualTo(foundBonus.getId());
        assertBonusesAreEqualIgnoringId(updatedBonus, foundBonus);
        assertThat(updatedBonus.getSourceGradingScale().getId()).isEqualTo(foundBonus.getSourceGradingScale().getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithChangingSourceGradingScale() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, Bonus.class);

        BonusStrategy newBonusStrategy = BonusStrategy.POINTS;
        foundBonus.setBonusStrategy(newBonusStrategy);
        double newWeight = -foundBonus.getWeight();
        foundBonus.setWeight(newWeight);

        assertThat(foundBonus.getSourceGradingScale().getId()).isNotEqualTo(sourceExamGradingScale.getId());
        foundBonus.setSourceGradingScale(sourceExamGradingScale);

        request.put("/api/bonus/", foundBonus, HttpStatus.OK);
        Bonus updatedBonus = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, Bonus.class);
        assertThat(updatedBonus.getId()).isEqualTo(foundBonus.getId());
        assertBonusesAreEqualIgnoringId(updatedBonus, foundBonus);
        assertThat(updatedBonus.getSourceGradingScale().getId()).isEqualTo(foundBonus.getSourceGradingScale().getId());

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteBonus() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, Bonus.class);

        request.delete("/api/bonus/" + foundBonus.getId(), HttpStatus.OK);
        request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.NOT_FOUND, Bonus.class);

    }

}
