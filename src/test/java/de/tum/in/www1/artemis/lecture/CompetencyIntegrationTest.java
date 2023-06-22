package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CourseCompetencyProgressDTO;

class CompetencyIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "competencyintegrationtest";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompetencyRelationRepository competencyRelationRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    private Course course;

    private Course course2;

    private Competency competency;

    private Lecture lecture;

    private Long idOfTextUnitOfLectureOne;

    private Exercise teamTextExercise;

    private Exercise textExercise;

    @BeforeEach
    void setupTestScenario() {
        participantScoreScheduleService.activate();

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        // creating course
        course = courseUtilService.createCourse();

        course2 = courseUtilService.createCourse();

        competency = createCompetency();
        createPrerequisiteForCourse2();
        lecture = createLecture(course);

        textExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(competency), false);
        teamTextExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(competency), true);

        creatingLectureUnitsOfLecture(competency);
    }

    private Competency createCompetency() {
        Competency competency = new Competency();
        competency.setTitle("Competency" + course.getId());
        competency.setDescription("This is an example competency");
        competency.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
        competency.setCourse(course);
        competency = competencyRepository.save(competency);

        return competency;
    }

    private void createPrerequisiteForCourse2() {
        // Add the first competency as a prerequisite to the other course
        course2.addPrerequisite(competency);
        courseRepository.save(course2);
    }

    private void creatingLectureUnitsOfLecture(Competency competency) {
        // creating lecture units for lecture one

        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureOne");
        textUnit.setCompetencies(Set.of(competency));
        textUnit = textUnitRepository.save(textUnit);
        idOfTextUnitOfLectureOne = textUnit.getId();

        ExerciseUnit textExerciseUnit = new ExerciseUnit();
        textExerciseUnit.setExercise(textExercise);
        exerciseUnitRepository.save(textExerciseUnit);

        ExerciseUnit teamTextExerciseUnit = new ExerciseUnit();
        teamTextExerciseUnit.setExercise(teamTextExercise);
        exerciseUnitRepository.save(teamTextExerciseUnit);

        for (LectureUnit lectureUnit : List.of(textUnit, textExerciseUnit, teamTextExerciseUnit)) {
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

    private TextExercise createTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp, Set<Competency> competencies,
            boolean isTeamExercise) {
        // creating text exercise with Result
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);

        if (isTeamExercise) {
            textExercise.setMode(ExerciseMode.TEAM);
        }

        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setCompetencies(competencies);
        exerciseRepository.save(textExercise);

        return textExercise;
    }

    private void createTextExerciseParticipationSubmissionAndResult(Exercise exercise, Participant participant, Double pointsOfExercise, Double bonusPointsOfExercise,
            long scoreAwarded, boolean rated) {
        if (!exercise.getMaxPoints().equals(pointsOfExercise)) {
            exercise.setMaxPoints(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exercise = exerciseRepository.save(exercise);

        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);

        Submission submission = new TextSubmission();

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.save(submission);

        // result
        Result result = ParticipationFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setCompletionDate(ZonedDateTime.now());
        result = resultRepository.save(result);

        submission.addResult(result);
        result.setSubmission(submission);
        submissionRepository.save(submission);
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/courses/" + course.getId() + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course.getId() + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/course-progress", HttpStatus.FORBIDDEN, CourseCompetencyProgressDTO.class);
        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAll_asEditor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTitle_ForCompetency() throws Exception {
        String title = request.get("/api/competencies/" + competency.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(competency.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTitle_ForNonExistingCompetency() throws Exception {
        request.get("/api/competencies/12312321321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCompetency_asStudent_shouldReturnCompetency() throws Exception {
        Competency competency = request.get("/api/courses/" + course.getId() + "/competencies/" + this.competency.getId(), HttpStatus.OK, Competency.class);
        assertThat(competency.getId()).isEqualTo(this.competency.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void getCompetency_asUserNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.FORBIDDEN, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCompetency_asStudent_wrongCourse() throws Exception {
        request.get("/api/courses/" + course2.getId() + "/competencies/" + competency.getId(), HttpStatus.CONFLICT, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCompetenciesOfCourse_asStudent1_shouldReturnCompetencies() throws Exception {
        TextUnit unreleasedLectureUnit = new TextUnit();
        unreleasedLectureUnit.setName("TextUnitOfLectureOne");
        unreleasedLectureUnit.setReleaseDate(ZonedDateTime.now().plus(5, ChronoUnit.DAYS));
        unreleasedLectureUnit = textUnitRepository.save(unreleasedLectureUnit);
        lecture.addLectureUnit(unreleasedLectureUnit);
        lectureRepository.save(lecture);

        Competency newCompetency = new Competency();
        newCompetency.setTitle("Title");
        newCompetency.setDescription("Description");
        newCompetency.setCourse(course);
        newCompetency.setLectureUnits(new HashSet<>(List.of(unreleasedLectureUnit)));
        competencyRepository.save(newCompetency);

        List<Competency> competenciesOfCourse = request.getList("/api/courses/" + course.getId() + "/competencies", HttpStatus.OK, Competency.class);

        assertThat(competenciesOfCourse.stream().filter(l -> l.getId().equals(competency.getId())).findFirst()).isPresent();
        assertThat(competenciesOfCourse.stream().filter(l -> l.getId().equals(newCompetency.getId())).findFirst().get().getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void getCompetenciesOfCourse_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.getList("/api/courses/" + course.getId() + "/competencies", HttpStatus.FORBIDDEN, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteCompetency_asInstructor_shouldDeleteCompetency() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK);
        request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.NOT_FOUND, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteCompetency_witRelatedCompetencies_shouldReturnBadRequest() throws Exception {
        Competency competency1 = competencyUtilService.createCompetency(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(competency);
        relation.setHeadCompetency(competency1);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        competencyRelationRepository.save(relation);

        // Should return bad request, as the competency still has relations
        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void deleteCompetency_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourse_asAdmin_shouldAlsoDeleteCompetency() throws Exception {
        request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);
        request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.NOT_FOUND, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createCompetencyRelation() throws Exception {
        Long idOfOtherCompetency = competencyUtilService.createCompetency(course).getId();

        request.postWithoutResponseBody("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/relations/" + idOfOtherCompetency + "?type="
                + CompetencyRelation.RelationType.EXTENDS.name(), HttpStatus.OK, new LinkedMultiValueMap<>());

        var relations = competencyRelationRepository.findAllByCompetencyId(competency.getId());
        assertThat(relations).hasSize(1);
        assertThat(relations.stream().findFirst().get().getType()).isEqualTo(CompetencyRelation.RelationType.EXTENDS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createCompetencyRelation_shouldReturnBadRequest() throws Exception {
        Long idOfOtherCompetency = competencyUtilService.createCompetency(course).getId();

        request.post("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/relations/" + idOfOtherCompetency + "?type=" + "abc123xyz", null,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createCompetencyRelation_shouldReturnBadRequest_ForCircularRelations() throws Exception {
        Long idOfOtherCompetency1 = competencyUtilService.createCompetency(course).getId();
        Competency otherCompetency1 = competencyRepository.findByIdElseThrow(idOfOtherCompetency1);
        Long idOfOtherCompetency2 = competencyUtilService.createCompetency(course).getId();
        Competency otherCompetency2 = competencyRepository.findByIdElseThrow(idOfOtherCompetency1);

        var relation1 = new CompetencyRelation();
        relation1.setTailCompetency(competency);
        relation1.setHeadCompetency(otherCompetency1);
        relation1.setType(CompetencyRelation.RelationType.EXTENDS);
        competencyRelationRepository.save(relation1);

        var relation2 = new CompetencyRelation();
        relation2.setTailCompetency(otherCompetency1);
        relation2.setHeadCompetency(otherCompetency2);
        relation2.setType(CompetencyRelation.RelationType.MATCHES);
        competencyRelationRepository.save(relation2);

        request.post("/api/courses/" + course.getId() + "/competencies/" + idOfOtherCompetency2 + "/relations/" + competency.getId() + "?type="
                + CompetencyRelation.RelationType.ASSUMES.name(), null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void createCompetencyRelation_shouldReturnForbidden() throws Exception {
        Long idOfOtherCompetency = competencyUtilService.createCompetency(course).getId();

        request.post("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/relations/" + idOfOtherCompetency + "?type="
                + CompetencyRelation.RelationType.EXTENDS.name(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCompetencyRelations() throws Exception {
        Competency otherCompetency = competencyUtilService.createCompetency(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(competency);
        relation.setHeadCompetency(otherCompetency);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        relation = competencyRelationRepository.save(relation);

        var relations = request.getList("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/relations", HttpStatus.OK, CompetencyRelation.class);

        assertThat(relations).hasSize(1);
        assertThat(relations.get(0)).isEqualTo(relation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteCompetencyRelation() throws Exception {
        Competency otherCompetency = competencyUtilService.createCompetency(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(competency);
        relation.setHeadCompetency(otherCompetency);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        relation = competencyRelationRepository.save(relation);

        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/relations/" + relation.getId(), HttpStatus.OK);

        var relations = competencyRelationRepository.findAllByCompetencyId(competency.getId());
        assertThat(relations).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteCompetencyRelation_shouldReturnBadRequest() throws Exception {
        Competency otherCompetency = competencyUtilService.createCompetency(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(otherCompetency); // invalid
        relation.setHeadCompetency(competency);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        relation = competencyRelationRepository.save(relation);

        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/relations/" + relation.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_asInstructor_shouldUpdateCompetency() throws Exception {
        request.delete("/api/lectures/" + lecture.getId(), HttpStatus.OK);
        Competency competency = request.get("/api/courses/" + course.getId() + "/competencies/" + this.competency.getId(), HttpStatus.OK, Competency.class);
        assertThat(competency.getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_asInstructor_shouldUpdateCompetency() throws Exception {
        request.delete("/api/lectures/" + lecture.getId() + "/lecture-units/" + idOfTextUnitOfLectureOne, HttpStatus.OK);
        Competency competency = request.get("/api/courses/" + course.getId() + "/competencies/" + this.competency.getId(), HttpStatus.OK, Competency.class);
        assertThat(competency.getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCompetencyCourseProgressTeamsTest_asInstructorOne() throws Exception {
        User tutor = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").get();
        var teams = teamUtilService.addTeamsForExerciseFixedTeamSize(TEST_PREFIX, "lgi", teamTextExercise, 2, tutor, 1);

        createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.get(0), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.get(0), 10.0, 0.0, 50, false);

        createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.get(1), 10.0, 0.0, 10, false);

        await().until(() -> participantScoreScheduleService.isIdle());

        CourseCompetencyProgressDTO courseCompetencyProgress = request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/course-progress",
                HttpStatus.OK, CourseCompetencyProgressDTO.class);

        assertThat(courseCompetencyProgress.averageStudentScore()).isEqualTo(30);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCompetencyCourseProgressIndividualTest_asInstructorOne() throws Exception {
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        User student2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        User instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();

        createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 50, false);
        createTextExerciseParticipationSubmissionAndResult(textExercise, student2, 10.0, 0.0, 10, false);

        createTextExerciseParticipationSubmissionAndResult(textExercise, instructor1, 10.0, 0.0, 100, true); // will be ignored as not a student

        await().until(() -> participantScoreScheduleService.isIdle());

        CourseCompetencyProgressDTO courseCompetencyProgress = request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/course-progress",
                HttpStatus.OK, CourseCompetencyProgressDTO.class);

        assertThat(courseCompetencyProgress.averageStudentScore()).isEqualTo(53.3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCompetencyStudentProgressTest() throws Exception {
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        lectureUnitService.setLectureUnitCompletion(textUnitRepository.findById(idOfTextUnitOfLectureOne).get(), student1, true);

        createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 90, true);  // will be ignored in favor of last submission from team
        createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 85, false);

        await().until(() -> participantScoreScheduleService.isIdle());

        CompetencyProgress studentCompetencyProgress1 = request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/student-progress?refresh=true",
                HttpStatus.OK, CompetencyProgress.class);

        assertThat(studentCompetencyProgress1.getProgress()).isEqualTo(66.7);
        assertThat(studentCompetencyProgress1.getConfidence()).isEqualTo(85.0);

        CompetencyProgress studentCompetencyProgress2 = request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/student-progress?refresh=false",
                HttpStatus.OK, CompetencyProgress.class);

        assertThat(studentCompetencyProgress2.getProgress()).isEqualTo(66.7);
        assertThat(studentCompetencyProgress2.getConfidence()).isEqualTo(85.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCompetency_asInstructor_shouldUpdateCompetency() throws Exception {
        LectureUnit textLectureUnit = lectureUnitRepository.findByIdWithCompetenciesBidirectionalElseThrow(idOfTextUnitOfLectureOne);
        competency.setTitle("Updated");
        competency.removeLectureUnit(textLectureUnit);
        competency.setDescription("Updated Description");

        Competency updatedCompetency = request.putWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.OK);

        assertThat(updatedCompetency.getTitle()).isEqualTo("Updated");
        assertThat(updatedCompetency.getDescription()).isEqualTo("Updated Description");
        assertThat(updatedCompetency.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toSet())).doesNotContain(textLectureUnit.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCompetency_asInstructor_badRequest() throws Exception {
        competency.setId(null);
        request.putWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createCompetency_asInstructor_shouldCreateCompetency() throws Exception {
        Competency competency = new Competency();
        competency.setTitle("FreshlyCreatedCompetency");
        competency.setDescription("This is an example of a freshly created competency");
        competency.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        competency.setLectureUnits(connectedLectureUnits);

        var persistedCompetency = request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.CREATED);
        assertThat(persistedCompetency.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createCompetency_asInstructor_badRequest() throws Exception {
        Competency competency = new Competency(); // no title
        request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
        competency.setTitle(" "); // empty title
        request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
        competency.setTitle("Hello");
        competency.setId(5L); // id is set
        request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createCompetency_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        Competency competency = new Competency();
        competency.setTitle("Example Title");
        request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importCompetency_asInstructor_shouldImportCompetency() throws Exception {
        Competency importedCompetency = request.postWithResponseBody("/api/courses/" + course2.getId() + "/competencies/import", competency, Competency.class, HttpStatus.CREATED);

        assertThat(competencyRepository.findById(importedCompetency.getId())).isNotEmpty();
        assertThat(importedCompetency.getTitle()).isEqualTo(competency.getTitle());
        assertThat(importedCompetency.getDescription()).isEqualTo(competency.getDescription());
        assertThat(importedCompetency.getMasteryThreshold()).isEqualTo(competency.getMasteryThreshold());
        assertThat(importedCompetency.getTaxonomy()).isEqualTo(competency.getTaxonomy());
        assertThat(importedCompetency.getExercises()).isEmpty();
        assertThat(importedCompetency.getLectureUnits()).isEmpty();
        assertThat(importedCompetency.getUserProgress()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void importCompetency_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        Competency competency = new Competency();
        competency.setTitle("Example Title");
        request.postWithResponseBody("/api/courses/" + course2.getId() + "/competencies/import", competency, Competency.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/competencies/", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = pageableSearchUtilService.configureSearch(competency.getTitle());
        final var result = request.getSearchResult("/api/competencies/", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        final var search = pageableSearchUtilService.configureSearch(competency.getTitle());
        final var result = request.getSearchResult("/api/competencies/", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPrerequisites() throws Exception {
        List<Competency> prerequisites = request.getList("/api/courses/" + course2.getId() + "/prerequisites", HttpStatus.OK, Competency.class);
        assertThat(prerequisites).containsExactly(competency);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addPrerequisite() throws Exception {
        Competency competency = new Competency();
        competency.setTitle("CompetencyTwo");
        competency.setDescription("This is an example competency");
        competency.setCourse(course2);
        competency = competencyRepository.save(competency);

        Competency prerequisite = request.postWithResponseBody("/api/courses/" + course.getId() + "/prerequisites/" + competency.getId(), competency, Competency.class,
                HttpStatus.OK);

        assertThat(prerequisite).isNotNull();
        assertThat(prerequisite.getConsecutiveCourses()).contains(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void addPrerequisite_unauthorized() throws Exception {
        request.postWithResponseBody("/api/courses/" + course.getId() + "/prerequisites/99", null, Competency.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removePrerequisite() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/prerequisites/" + competency.getId(), HttpStatus.OK);

        Course course = courseRepository.findWithEagerCompetenciesById(course2.getId()).orElseThrow();
        assertThat(course.getPrerequisites()).doesNotContain(competency);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removePrerequisite_conflict() throws Exception {
        request.delete("/api/courses/" + course.getId() + "/prerequisites/" + competency.getId(), HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void removePrerequisite_unauthorized() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/prerequisites/" + competency.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addPrerequisite_doNotAllowCycle() throws Exception {
        // Test that a competency of a course can not be a prerequisite to the same course
        request.postWithResponseBody("/api/courses/" + course.getId() + "/prerequisites/" + competency.getId(), competency, Competency.class, HttpStatus.CONFLICT);
    }

}
