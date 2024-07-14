package de.tum.in.www1.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.exercise.text.TextExerciseFactory;
import de.tum.in.www1.artemis.repository.AttachmentUnitRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ExerciseUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.PrerequisiteRepository;
import de.tum.in.www1.artemis.repository.TextUnitRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class CourseCompetencyIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "coursecompetencyintegrationtest";

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private PrerequisiteRepository prerequisiteRepository;

    private Course course;

    private Competency competency;

    private Prerequisite prerequisite;

    private Lecture lecture;

    private TextExercise teamTextExercise;

    private TextExercise textExercise;

    @BeforeEach
    void setupTestScenario() {
        participantScoreScheduleService.activate();

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        // creating course
        course = courseUtilService.createCourse();

        competency = createCompetency(course);
        prerequisite = createPrerequisite(course);
        lecture = createLecture(course);

        textExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(competency), false);
        teamTextExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(prerequisite), true);

        creatingLectureUnitsOfLecture(competency);
        creatingLectureUnitsOfLecture(prerequisite);
    }

    private Competency createCompetency(Course course) {
        Competency competency = new Competency();
        competency.setTitle("Competency" + course.getId());
        competency.setDescription("This is an example competency");
        competency.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
        competency.setCourse(course);
        competency = competencyRepository.save(competency);

        return competency;
    }

    private Prerequisite createPrerequisite(Course course) {
        Prerequisite prerequisite = new Prerequisite();
        prerequisite.setTitle("Prerequisite" + course.getId());
        prerequisite.setDescription("This is an example prerequisite");
        prerequisite.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
        prerequisite.setCourse(course);
        prerequisite = prerequisiteRepository.save(prerequisite);

        return prerequisite;
    }

    private void creatingLectureUnitsOfLecture(CourseCompetency competency) {
        // creating lecture units for lecture one

        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureOne");
        textUnit.setCompetencies(Set.of(competency));
        textUnit = textUnitRepository.save(textUnit);

        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setName("AttachmentUnitOfLectureOne");
        attachmentUnit.setCompetencies(Set.of(competency));
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);

        ExerciseUnit textExerciseUnit = new ExerciseUnit();
        textExerciseUnit.setExercise(textExercise);
        exerciseUnitRepository.save(textExerciseUnit);

        ExerciseUnit teamTextExerciseUnit = new ExerciseUnit();
        teamTextExerciseUnit.setExercise(teamTextExercise);
        exerciseUnitRepository.save(teamTextExerciseUnit);

        for (LectureUnit lectureUnit : List.of(textUnit, attachmentUnit, textExerciseUnit, teamTextExerciseUnit)) {
            lecture.addLectureUnit(lectureUnit);
        }

        lectureRepository.save(lecture);
    }

    private Lecture createLecture(Course course) {
        Lecture lecture = new Lecture();
        lecture.setTitle("LectureOne");
        lecture.setCourse(course);
        lectureRepository.save(lecture);

        return lecture;
    }

    private TextExercise createTextExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assassmentDueDate, Set<CourseCompetency> competencies,
            boolean isTeamExercise) {
        // creating text exercise with Result
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(releaseDate, dueDate, assassmentDueDate, course);

        if (isTeamExercise) {
            textExercise.setMode(ExerciseMode.TEAM);
        }

        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setCompetencies(competencies);

        return exerciseRepository.save(textExercise);
    }

    @Nested
    class GetCompetenciesOfCourse {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnCompetenciesForStudentOfCourse() throws Exception {
            TextUnit unreleasedLectureUnit = new TextUnit();
            unreleasedLectureUnit.setName("TextUnitOfLectureOne");
            unreleasedLectureUnit.setReleaseDate(ZonedDateTime.now().plusDays(5));
            unreleasedLectureUnit = textUnitRepository.save(unreleasedLectureUnit);
            lecture.addLectureUnit(unreleasedLectureUnit);
            lectureRepository.save(lecture);

            Competency newCompetency = new Competency();
            newCompetency.setTitle("Title");
            newCompetency.setDescription("Description");
            newCompetency.setCourse(course);
            newCompetency.setLectureUnits(new HashSet<>(List.of(unreleasedLectureUnit)));
            newCompetency = competencyRepository.save(newCompetency);

            List<CourseCompetency> competenciesOfCourse = request.getList("/api/courses/" + course.getId() + "/course-competencies", HttpStatus.OK, CourseCompetency.class);

            assertThat(competenciesOfCourse).map(CourseCompetency::getId).containsExactlyInAnyOrder(competency.getId(), prerequisite.getId(), newCompetency.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
        void testShouldReturnForbiddenForStudentNotInCourse() throws Exception {
            request.getList("/api/courses/" + course.getId() + "/course-competencies", HttpStatus.FORBIDDEN, Competency.class);
        }
    }
}
