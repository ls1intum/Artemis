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
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class BonusIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private ExamRepository examRepository;

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
    public void init() {
        database.addUsers(0, 0, 0, 1);
        course = database.addEmptyCourse();
        Exam targetExam = database.addExamWithExerciseGroup(course, true);
        Exam sourceExam = database.addExamWithExerciseGroup(course, true);
        bonusToExamGradingScale = new GradingScale();
        bonusToExamGradingScale.setExam(targetExam);

        sourceExamGradingScale = new GradingScale();
        sourceExamGradingScale.setExam(sourceExam);

        courseGradingScale = new GradingScale();
        courseGradingScale.setCourse(course);

        gradingScaleRepository.saveAll(List.of(bonusToExamGradingScale, sourceExamGradingScale, courseGradingScale));

        courseBonus = ModelFactory.generateBonusSource(BonusStrategy.GRADES_CONTINUOUS, 1.0, courseGradingScale, bonusToExamGradingScale);
        examBonus = ModelFactory.generateBonusSource(BonusStrategy.GRADES_CONTINUOUS, 1.0, sourceExamGradingScale, bonusToExamGradingScale);
        bonusRepository.saveAll(List.of(examBonus, courseBonus));
        gradingScaleRepository.save(bonusToExamGradingScale);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    /**
     * Test get request for bonus source
     *
     * @throws Exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetBonusSourcesForTargetExamNotFound() throws Exception {
        bonusRepository.delete(courseBonus);
        bonusRepository.delete(examBonus);

        var result = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, String.class);

        assertThat(result).isNullOrEmpty();
    }

    private void assertBonusSourcesAreEqualIgnoringId(Bonus actualBonus, Bonus expectedBonus) {
        assertThat(actualBonus).usingRecursiveComparison().ignoringFields("id", "source", "bonusToGradingScale").isEqualTo(expectedBonus);
        assertThat(actualBonus.getSource().getId()).isEqualTo(expectedBonus.getSource().getId());
        // assertThat(actualBonusSource.getTarget().getId()).isEqualTo(expectedBonusSource.getTarget().getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetBonusForTargetExam() throws Exception {

        Bonus foundBonus = request.get("/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus",
                HttpStatus.OK, Bonus.class);

        assertThat(foundBonus.getId()).isEqualTo(examBonus.getId());
        assertBonusSourcesAreEqualIgnoringId(foundBonus, examBonus);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetBonusSource() throws Exception {

        Bonus foundBonus = request.get("/api/bonus/" + courseBonus.getId(), HttpStatus.OK, Bonus.class);

        assertThat(foundBonus.getId()).isEqualTo(courseBonus.getId());
        assertBonusSourcesAreEqualIgnoringId(foundBonus, courseBonus);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSaveBonusSourceForTargetExam() throws Exception {

        Exam newExam = database.addExamWithExerciseGroup(course, true);
        var newExamGradingScale = new GradingScale();
        newExamGradingScale.setExam(newExam);
        gradingScaleRepository.save(newExamGradingScale);

        Bonus newBonus = ModelFactory.generateBonusSource(BonusStrategy.GRADES_CONTINUOUS, -1.0, newExamGradingScale, bonusToExamGradingScale);

        Bonus savedBonus = request.postWithResponseBody(
                "/api/courses/" + bonusToExamGradingScale.getExam().getCourse().getId() + "/exams/" + bonusToExamGradingScale.getExam().getId() + "/bonus", newBonus, Bonus.class,
                HttpStatus.CREATED);

        assertThat(savedBonus.getId()).isGreaterThan(0);
        assertBonusSourcesAreEqualIgnoringId(savedBonus, newBonus);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetBonusSources() throws Exception {

        Bonus foundBonus = request.get("/api/bonus/" + courseBonus.getId(), HttpStatus.OK, Bonus.class);

        assertThat(foundBonus.getId()).isEqualTo(courseBonus.getId());
        assertBonusSourcesAreEqualIgnoringId(foundBonus, courseBonus);
    }

    // TODO: Ata: Add student tests that fail due to Unauthorized

}
