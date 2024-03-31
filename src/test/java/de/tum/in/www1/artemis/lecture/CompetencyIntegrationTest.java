package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.competency.CompetencyProgressUtilService;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.competency.RelationType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ExerciseUnitRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TextUnitRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyRelationDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;

class CompetencyIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

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

    @Autowired
    private CompetencyProgressUtilService competencyProgressUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

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

        competency = createCompetency(course);
        createPrerequisiteForCourse2();
        lecture = createLecture(course);

        textExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(competency), false);
        teamTextExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(competency), true);

        creatingLectureUnitsOfLecture(competency);
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

    CompetencyRelation createRelation(Competency tail, Competency head, RelationType type) {
        CompetencyRelation relation = new CompetencyRelation();
        relation.setHeadCompetency(head);
        relation.setTailCompetency(tail);
        relation.setType(type);
        return competencyRelationRepository.save(relation);
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

    @Nested
    class PreAuthorize {

        private void testAllPreAuthorizeEditor() throws Exception {
            request.get("/api/competencies/for-import", HttpStatus.FORBIDDEN, SearchResultPageDTO.class);
            request.post("/api/courses/" + course.getId() + "/competencies/import/bulk", Collections.emptyList(), HttpStatus.FORBIDDEN);
        }

        private void testAllPreAuthorizeInstructor() throws Exception {
            request.put("/api/courses/" + course.getId() + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
            request.post("/api/courses/" + course.getId() + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
            request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/course-progress", HttpStatus.FORBIDDEN, CourseCompetencyProgressDTO.class);
            request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.FORBIDDEN);
            request.post("/api/courses/" + course.getId() + "/competencies/bulk", Collections.emptyList(), HttpStatus.FORBIDDEN);
            // import
            request.post("/api/courses/" + course.getId() + "/competencies/import-all/1", null, HttpStatus.FORBIDDEN);
            request.post("/api/courses/" + course.getId() + "/competencies/import", competency, HttpStatus.FORBIDDEN);
            // prerequisites
            request.post("/api/courses/" + course.getId() + "/prerequisites/1", null, HttpStatus.FORBIDDEN);
            request.delete("/api/courses/" + course.getId() + "/prerequisites/1", HttpStatus.FORBIDDEN);
            // relations
            request.post("/api/courses/" + course.getId() + "/competencies/relations", new CompetencyRelation(), HttpStatus.FORBIDDEN);
            request.getSet("/api/courses/" + course.getId() + "/competencies/relations", HttpStatus.FORBIDDEN, CompetencyRelationDTO.class);
            request.delete("/api/courses/" + course.getId() + "/competencies/relations/1", HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldFailAsTutor() throws Exception {
            this.testAllPreAuthorizeInstructor();
            this.testAllPreAuthorizeEditor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldFailAsStudent() throws Exception {
            this.testAllPreAuthorizeInstructor();
            this.testAllPreAuthorizeEditor();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldFailAsEditor() throws Exception {
            this.testAllPreAuthorizeInstructor();
            // do not call testAllPreAuthorizeEditor, as these methods should succeed
        }
    }

    @Nested
    class GetTitle {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnCompetencyTitleWhenCompetencyExists() throws Exception {
            String title = request.get("/api/competencies/" + competency.getId() + "/title", HttpStatus.OK, String.class);
            assertThat(title).isEqualTo(competency.getTitle());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnNotFoundWhenCompetencyNotExists() throws Exception {
            request.get("/api/competencies/12312321321/title", HttpStatus.NOT_FOUND, String.class);
        }
    }

    @Nested
    class GetCompetency {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnCompetencyForStudent() throws Exception {
            Competency response = request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK, Competency.class);
            assertThat(response.getId()).isEqualTo(competency.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testShouldOnlySendUserSpecificData() throws Exception {

            User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            competencyProgressUtilService.createCompetencyProgress(competency, student1, 0, 0);

            User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            competencyProgressUtilService.createCompetencyProgress(competency, student2, 1, 1);

            final var textUnit = textUnitRepository.findById(idOfTextUnitOfLectureOne).get();
            lectureUtilService.completeLectureUnitForUser(textUnit, student2);

            Competency response = request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK, Competency.class);
            assertThat(response.getId()).isEqualTo(competency.getId());

            // only progress of student1 is fetched
            assertThat(response.getUserProgress()).hasSize(1);

            // only student2 has completed the textUnit
            assertThat(response.getLectureUnits().stream().findFirst().get().getCompletedUsers()).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForUserNotInCourse() throws Exception {
            request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.FORBIDDEN, Competency.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnBadRequestForWrongCourse() throws Exception {
            request.get("/api/courses/" + course2.getId() + "/competencies/" + competency.getId(), HttpStatus.BAD_REQUEST, Competency.class);
        }
    }

    @Nested
    class GetCompetenciesOfCourse {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnCompetenciesForStudentOfCourse() throws Exception {
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

            assertThat(competenciesOfCourse).anyMatch(c -> c.getId().equals(competency.getId()));
            assertThat(competenciesOfCourse.stream().filter(l -> l.getId().equals(newCompetency.getId())).findFirst().orElseThrow().getLectureUnits()).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
        void testShouldReturnForbiddenForStudentNotInCourse() throws Exception {
            request.getList("/api/courses/" + course.getId() + "/competencies", HttpStatus.FORBIDDEN, Competency.class);
        }
    }

    @Nested
    class DeleteCompetency {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldDeleteCompetencyWhenInstructor() throws Exception {
            request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK);
            request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.NOT_FOUND, Competency.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldDeleteCompetencyAndRelations() throws Exception {
            Competency competency2 = competencyUtilService.createCompetency(course);
            createRelation(competency, competency2, RelationType.EXTENDS);

            request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK);

            Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
            assertThat(relations).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorOfOtherCourse() throws Exception {
            request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.FORBIDDEN);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourseShouldAlsoDeleteCompetency() throws Exception {
        request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);
        request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.NOT_FOUND, Competency.class);
    }

    @Nested
    class CreateCompetencyRelation {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateForInstructor() throws Exception {
            var headCompetency = competencyUtilService.createCompetency(course);
            var relationToCreate = new CompetencyRelation();
            relationToCreate.setTailCompetency(competency);
            relationToCreate.setHeadCompetency(headCompetency);
            relationToCreate.setType(RelationType.EXTENDS);

            request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies/relations", relationToCreate, CompetencyRelation.class, HttpStatus.OK);

            var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
            assertThat(relations).hasSize(1);
            assertThat(relations.stream().findFirst().get().getType()).isEqualTo(RelationType.EXTENDS);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenCompetencyNotExists() throws Exception {
            var notPersCompetency = new Competency();
            notPersCompetency.setId(1337L);
            var persistedCompetency = competencyUtilService.createCompetency(course);
            var relationHeadNotPers = new CompetencyRelation();
            relationHeadNotPers.setHeadCompetency(notPersCompetency);
            relationHeadNotPers.setTailCompetency(persistedCompetency);

            request.post("/api/courses/" + course.getId() + "/competencies/relations/", relationHeadNotPers, HttpStatus.NOT_FOUND);

            var relationTailNotPers = new CompetencyRelation();
            relationTailNotPers.setHeadCompetency(persistedCompetency);
            relationTailNotPers.setTailCompetency(notPersCompetency);

            request.post("/api/courses/" + course.getId() + "/competencies/relations/", relationTailNotPers, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenTypeNotPresent() throws Exception {
            var headCompetency = competencyUtilService.createCompetency(course);
            var relationToCreate = new CompetencyRelation();
            relationToCreate.setTailCompetency(competency);
            relationToCreate.setHeadCompetency(headCompetency);
            // relation type must be set
            relationToCreate.setType(null);

            request.post("/api/courses/" + course.getId() + "/competencies/relations", relationToCreate, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestForCircularRelations() throws Exception {
            Competency competency2 = competencyUtilService.createCompetency(course);
            Competency competency3 = competencyUtilService.createCompetency(course);

            createRelation(competency, competency2, RelationType.EXTENDS);
            createRelation(competency2, competency3, RelationType.MATCHES);

            var relation = new CompetencyRelation();
            relation.setTailCompetency(competency3);
            relation.setHeadCompetency(competency);
            relation.setType(RelationType.ASSUMES);

            request.post("/api/courses/" + course.getId() + "/competencies/relations", relation, HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCompetencyRelationsShouldGetRelations() throws Exception {
        Competency competency2 = competencyUtilService.createCompetency(course);
        Competency competency3 = competencyUtilService.createCompetency(course);

        var relation = createRelation(competency, competency2, RelationType.EXTENDS);
        var relation2 = createRelation(competency2, competency3, RelationType.EXTENDS);
        var expectedRelations = Set.of(new CompetencyRelationDTO(relation.getId(), relation.getTailCompetency().getId(), relation.getHeadCompetency().getId(), relation.getType()),
                new CompetencyRelationDTO(relation2.getId(), relation2.getTailCompetency().getId(), relation2.getHeadCompetency().getId(), relation2.getType()));

        var actualRelations = request.getSet("/api/courses/" + course.getId() + "/competencies/relations", HttpStatus.OK, CompetencyRelationDTO.class);

        assertThat(actualRelations).hasSize(2);
        assertThat(actualRelations).isEqualTo(expectedRelations);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteCompetencyRelationShouldDeleteRelation() throws Exception {
        Competency competency2 = competencyUtilService.createCompetency(course);

        var relation = createRelation(competency, competency2, RelationType.EXTENDS);

        request.delete("/api/courses/" + course.getId() + "/competencies/relations/" + relation.getId(), HttpStatus.OK);

        var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        assertThat(relations).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureShouldUpdateCompetency() throws Exception {
        request.delete("/api/lectures/" + lecture.getId(), HttpStatus.OK);
        Competency competency = request.get("/api/courses/" + course.getId() + "/competencies/" + this.competency.getId(), HttpStatus.OK, Competency.class);
        assertThat(competency.getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnitShouldUpdateCompetency() throws Exception {
        request.delete("/api/lectures/" + lecture.getId() + "/lecture-units/" + idOfTextUnitOfLectureOne, HttpStatus.OK);
        Competency competency = request.get("/api/courses/" + course.getId() + "/competencies/" + this.competency.getId(), HttpStatus.OK, Competency.class);
        assertThat(competency.getLectureUnits()).isEmpty();
    }

    @Nested
    class GetCompetencyCourseProgress {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetCompetencyCourseProgressWhenTeamExercise() throws Exception {
            User tutor = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
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
        void shouldGetCompetencyCourseProgress() throws Exception {
            User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            User student2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
            User instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").orElseThrow();

            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 50, false);
            createTextExerciseParticipationSubmissionAndResult(textExercise, student2, 10.0, 0.0, 10, false);

            createTextExerciseParticipationSubmissionAndResult(textExercise, instructor1, 10.0, 0.0, 100, true); // will be ignored as not a student

            await().until(() -> participantScoreScheduleService.isIdle());

            CourseCompetencyProgressDTO courseCompetencyProgress = request.get("/api/courses/" + course.getId() + "/competencies/" + competency.getId() + "/course-progress",
                    HttpStatus.OK, CourseCompetencyProgressDTO.class);

            assertThat(courseCompetencyProgress.averageStudentScore()).isEqualTo(53.3);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCompetencyStudentProgressShouldReturnProgress() throws Exception {
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        lectureUnitService.setLectureUnitCompletion(textUnitRepository.findById(idOfTextUnitOfLectureOne).orElseThrow(), student1, true);

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

    @Nested
    class UpdateCompetency {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldUpdateCompetency() throws Exception {
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
        void shouldReturnBadRequestForCompetencyWithoutId() throws Exception {
            competency.setId(null);
            request.putWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
        }

        @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
        @EnumSource(IncludedInOverallScore.class)
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldUpdateCompetencyToOptionalWhenSettingOptional(IncludedInOverallScore includedInOverallScore) throws Exception {
            Competency newCompetency = new Competency();
            newCompetency.setTitle("Title");
            newCompetency.setDescription("Description");
            newCompetency.setCourse(course);
            newCompetency = competencyRepository.save(newCompetency);

            TextExercise exercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now(), ZonedDateTime.now(), ZonedDateTime.now(), course);
            exercise.setMaxPoints(1.0);
            exercise.setIncludedInOverallScore(includedInOverallScore);
            exercise.setCompetencies(Set.of(newCompetency));
            exerciseRepository.save(exercise);

            newCompetency.setOptional(true);
            request.put("/api/courses/" + course.getId() + "/competencies", newCompetency, HttpStatus.OK);

            Competency savedCompetency = competencyRepository.findByIdElseThrow(newCompetency.getId());
            assertThat(savedCompetency.isOptional()).isTrue();
        }
    }

    @Nested
    class CreateCompetency {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateValidCompetency() throws Exception {
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

        @Nested
        class ShouldReturnBadRequest {

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void forCompetencyWithNoTitle() throws Exception {
                Competency competency = new Competency(); // no title
                request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
            }

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void forCompetencyWithEmptyTitle() throws Exception {
                Competency competency = new Competency();
                competency.setTitle(" "); // empty title
                request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
            }

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void forCompetencyWithId() throws Exception {
                Competency competency = new Competency();
                competency.setTitle("Hello");
                competency.setId(5L); // id is set
                request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorOfOtherCourse() throws Exception {
            Competency competency = new Competency();
            competency.setTitle("Example Title");
            request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competency, Competency.class, HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class ImportCompetency {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldImportCompetency() throws Exception {
            Competency importedCompetency = request.postWithResponseBody("/api/courses/" + course2.getId() + "/competencies/import", competency, Competency.class,
                    HttpStatus.CREATED);

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
        void shouldReturnForbiddenForInstructorOfOtherCourse() throws Exception {
            Competency competency = new Competency();
            competency.setTitle("Example Title");
            request.postWithResponseBody("/api/courses/" + course2.getId() + "/competencies/import", competency, Competency.class, HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class GetCompetencies {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldNotGetResultsFromCoursesForInstructorNotInCourses() throws Exception {
            final var search = pageableSearchUtilService.configureSearch("");
            final var result = request.getSearchResult("/api/competencies", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).isNullOrEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetResultsFromCoursesForInstructor() throws Exception {
            final var search = pageableSearchUtilService.configureSearch(competency.getTitle());
            final var result = request.getSearchResult("/api/competencies", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetResultsFromAllCoursesForAdmin() throws Exception {
            final var search = pageableSearchUtilService.configureSearch(competency.getTitle());
            final var result = request.getSearchResult("/api/competencies", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPrerequisitesShouldReturnPrerequisites() throws Exception {
        List<Competency> prerequisites = request.getList("/api/courses/" + course2.getId() + "/prerequisites", HttpStatus.OK, Competency.class);
        assertThat(prerequisites).containsExactly(competency);
    }

    @Nested
    class AddPrerequisite {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldAddPrerequisite() throws Exception {
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
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldNotAddPrerequisiteWhenAlreadyCompetencyInCourse() throws Exception {
            // Test that a competency of a course can not be a prerequisite to the same course
            request.postWithResponseBody("/api/courses/" + course.getId() + "/prerequisites/" + competency.getId(), competency, Competency.class, HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class RemovePrerequisite {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRemovePrerequisite() throws Exception {
            request.delete("/api/courses/" + course2.getId() + "/prerequisites/" + competency.getId(), HttpStatus.OK);
            Course course = courseRepository.findWithEagerCompetenciesById(course2.getId()).orElseThrow();
            assertThat(course.getPrerequisites()).doesNotContain(competency);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenPrerequisiteNotExists() throws Exception {
            request.delete("/api/courses/" + course.getId() + "/prerequisites/" + competency.getId(), HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldNotRemovePrerequisiteOfAnotherCourse() throws Exception {
            Course anotherCourse = courseUtilService.createCourse();
            anotherCourse.addPrerequisite(competency);
            anotherCourse = courseRepository.save(anotherCourse);

            request.delete("/api/courses/" + course2.getId() + "/prerequisites/" + competency.getId(), HttpStatus.OK);
            anotherCourse = courseRepository.findWithEagerCompetenciesById(anotherCourse.getId()).orElseThrow();
            assertThat(anotherCourse.getPrerequisites()).contains(competency);
        }
    }

    @Nested
    class CreateCompetencies {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateCompetencies() throws Exception {
            var competency1 = new Competency();
            competency1.setTitle("Competency1");
            competency1.setDescription("This is an example competency");
            competency1.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
            competency1.setCourse(course);
            var competency2 = new Competency();
            competency2.setTitle("Competency2");
            competency2.setDescription("This is another example competency");
            competency2.setTaxonomy(CompetencyTaxonomy.REMEMBER);
            competency2.setCourse(course);

            var competenciesToCreate = List.of(competency1, competency2);

            var persistedCompetencies = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/bulk", competenciesToCreate, Competency.class,
                    HttpStatus.CREATED);
            assertThat(persistedCompetencies).usingRecursiveFieldByFieldElementComparatorOnFields("title", "description", "taxonomy").isEqualTo(competenciesToCreate);
            assertThat(persistedCompetencies).extracting("id").isNotNull();
        }

        @Nested
        class ShouldReturnBadRequest {

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void forCompetencyWithNoTitle() throws Exception {
                Competency competency = new Competency(); // no title
                request.post("/api/courses/" + course.getId() + "/competencies/bulk", List.of(competency), HttpStatus.BAD_REQUEST);
            }

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void forCompetencyWithEmptyTitle() throws Exception {
                Competency competency = new Competency();
                competency.setTitle(" "); // empty title
                request.post("/api/courses/" + course.getId() + "/competencies/bulk", List.of(competency), HttpStatus.BAD_REQUEST);
            }

            @Test
            @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
            void forCompetencyWithId() throws Exception {
                Competency competency = new Competency();
                competency.setTitle("Title");
                competency.setId(1L); // id is set
                request.post("/api/courses/" + course.getId() + "/competencies/bulk", List.of(competency), HttpStatus.BAD_REQUEST);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorOfOtherCourse() throws Exception {
            request.post("/api/courses/" + course.getId() + "/competencies/bulk", Collections.emptyList(), HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class ImportCompetencies {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldImportCompetencies() throws Exception {
            var course3 = courseUtilService.createCourse();

            var competencyDTOList = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/import-all/" + course3.getId(), null,
                    CompetencyWithTailRelationDTO.class, HttpStatus.CREATED);
            assertThat(competencyDTOList).isEmpty();

            Competency head = createCompetency(course3);
            Competency tail = createCompetency(course3);
            createRelation(tail, head, RelationType.RELATES);

            competencyDTOList = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/import-all/" + course3.getId() + "?importRelations=true", null,
                    CompetencyWithTailRelationDTO.class, HttpStatus.CREATED);

            assertThat(competencyDTOList).hasSize(2);
            // competency 2 should be the tail of one relation
            assertThat(competencyDTOList.get(0).tailRelations()).isNull();
            assertThat(competencyDTOList.get(1).tailRelations()).hasSize(1);

            competencyDTOList = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/import-all/" + course3.getId(), null,
                    CompetencyWithTailRelationDTO.class, HttpStatus.CREATED);
            assertThat(competencyDTOList).hasSize(2);
            // relations should be empty when not importing them
            assertThat(competencyDTOList.get(0).tailRelations()).isNull();
            assertThat(competencyDTOList.get(1).tailRelations()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorNotInCourse() throws Exception {
            request.post("/api/courses/" + course.getId() + "/competencies/import-all/" + course2.getId(), null, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestForImportFromSameCourse() throws Exception {
            request.post("/api/courses/" + course.getId() + "/competencies/import-all/" + course.getId(), null, HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class BulkImport {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorOfOtherCourse() throws Exception {
            request.post("/api/courses/" + course.getId() + "/competencies/import/bulk", Collections.emptyList(), HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldImportCompetencies() throws Exception {
            var competencyDTOList = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/import/bulk", Collections.emptyList(),
                    CompetencyWithTailRelationDTO.class, HttpStatus.CREATED);
            assertThat(competencyDTOList).isEmpty();

            Competency head = createCompetency(course2);
            Competency tail = createCompetency(course2);
            createRelation(tail, head, RelationType.RELATES);
            var competencyList = List.of(head, tail);

            competencyDTOList = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/import/bulk?importRelations=true", competencyList,
                    CompetencyWithTailRelationDTO.class, HttpStatus.CREATED);

            assertThat(competencyDTOList).hasSize(2);
            // competency 2 should be the tail of one relation
            if (competencyDTOList.get(0).tailRelations() != null) {
                assertThat(competencyDTOList.get(0).tailRelations()).hasSize(1);
                assertThat(competencyDTOList.get(1).tailRelations()).isNull();
            }
            else {
                assertThat(competencyDTOList.get(0).tailRelations()).isNull();
                assertThat(competencyDTOList.get(1).tailRelations()).hasSize(1);
            }

            competencyDTOList = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/import/bulk", competencyList,
                    CompetencyWithTailRelationDTO.class, HttpStatus.CREATED);
            assertThat(competencyDTOList).hasSize(2);
            // relations should be empty when not importing them
            assertThat(competencyDTOList.get(0).tailRelations()).isNull();
            assertThat(competencyDTOList.get(1).tailRelations()).isNull();
        }
    }

    @Nested
    class GetCompetenciesForImport {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldNotGetCompetenciesForInstructorOfOtherCourse() throws Exception {
            // configure search so all competencies would get returned
            final var search = pageableSearchUtilService.configureCompetencySearch("", "", "", "");
            var result = request.getSearchResult("/api/competencies/for-import", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetCompetenciesFromOwnCourses() throws Exception {
            final var search = pageableSearchUtilService.configureCompetencySearch(competency.getTitle(), "", "", "");
            var result = request.getSearchResult("/api/competencies/for-import", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetCompetenciesAsAdmin() throws Exception {
            final var search = pageableSearchUtilService.configureCompetencySearch(competency.getTitle(), "", "", "");
            final var result = request.getSearchResult("/api/competencies/for-import", HttpStatus.OK, Competency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }
    }
}
