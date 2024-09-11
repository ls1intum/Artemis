package de.tum.cit.aet.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.glassfish.jersey.internal.util.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.StudentScoreUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.PrerequisiteRepository;
import de.tum.cit.aet.artemis.course.CourseUtilService;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Lecture;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.DifficultyLevel;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseMode;
import de.tum.cit.aet.artemis.domain.enumeration.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseFactory;
import de.tum.cit.aet.artemis.lecture.LectureUtilService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.ExerciseUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.participation.ParticipationFactory;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.service.LectureUnitService;
import de.tum.cit.aet.artemis.service.ParticipationService;
import de.tum.cit.aet.artemis.team.TeamUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.user.UserUtilService;
import de.tum.cit.aet.artemis.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;

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

    @Autowired
    private CompetencyRelationRepository competencyRelationRepository;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private StudentScoreUtilService studentScoreUtilService;

    @Autowired
    private CourseCompetencyRepository courseCompetencyRepository;

    @Autowired
    private PrerequisiteUtilService prerequisiteUtilService;

    @Autowired
    protected CompetencyProgressUtilService competencyProgressUtilService;

    @Autowired
    protected LectureUtilService lectureUtilService;

    private Course course;

    private Competency competency;

    private Prerequisite prerequisite;

    private Lecture lecture;

    private long idOfTextUnitOfLectureOne;

    private long idOfAttachmentUnitOfLectureOne;

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

        creatingLectureUnitsOfLecture(prerequisite);
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
        idOfTextUnitOfLectureOne = textUnit.getId();

        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setName("AttachmentUnitOfLectureOne");
        attachmentUnit.setCompetencies(Set.of(competency));
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        idOfAttachmentUnitOfLectureOne = attachmentUnit.getId();

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

    private Result createExerciseParticipationSubmissionAndResult(Exercise exercise, StudentParticipation studentParticipation, Participant participant, double pointsOfExercise,
            double bonusPointsOfExercise, long scoreAwarded, boolean rated, Producer<Submission> submissionConstructor, int numberOfSubmissions) {
        if (!exercise.getMaxPoints().equals(pointsOfExercise)) {
            exercise.setMaxPoints(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exerciseRepository.save(exercise);

        Submission submission = null;

        for (int i = 0; i < numberOfSubmissions; i++) {
            submission = submissionConstructor.call();
            submission.setType(SubmissionType.MANUAL);
            submission.setParticipation(studentParticipation);
            submission = submissionRepository.save(submission);
        }

        // result
        Result result = ParticipationFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setCompletionDate(ZonedDateTime.now());
        result = resultRepository.save(result);

        submission.addResult(result);
        result.setSubmission(submission);
        submissionRepository.save(submission);

        return result;
    }

    private Result createTextExerciseParticipationSubmissionAndResult(TextExercise exercise, Participant participant, double pointsOfExercise, double bonusPointsOfExercise,
            long scoreAwarded, boolean rated) {
        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);
        return createExerciseParticipationSubmissionAndResult(exercise, studentParticipation, participant, pointsOfExercise, bonusPointsOfExercise, scoreAwarded, rated,
                TextSubmission::new, 1);
    }

    private ProgrammingExercise createProgrammingExercise(int i, Set<CourseCompetency> competencies) {
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(null, null, course);

        programmingExercise.setMaxPoints(i * 10.0);
        programmingExercise.setCompetencies(competencies);
        programmingExercise.setDifficulty(i == 1 ? DifficultyLevel.EASY : i == 2 ? DifficultyLevel.MEDIUM : DifficultyLevel.HARD);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        return programmingExerciseRepository.save(programmingExercise);
    }

    private Result createProgrammingExerciseParticipationSubmissionAndResult(ProgrammingExercise exercise, Participant participant, long scoreAwarded, boolean rated,
            int numberOfSubmissions) {
        StudentParticipation studentParticipation = participationUtilService.createAndSaveParticipationForExercise(exercise, participant.getParticipantIdentifier());
        return createExerciseParticipationSubmissionAndResult(exercise, studentParticipation, participant, exercise.getMaxPoints(), 0, scoreAwarded, rated,
                ProgrammingSubmission::new, numberOfSubmissions);
    }

    CompetencyRelation createRelation(CourseCompetency tail, CourseCompetency head, RelationType type) {
        CompetencyRelation relation = new CompetencyRelation();
        relation.setHeadCompetency(head);
        relation.setTailCompetency(tail);
        relation.setType(type);
        return competencyRelationRepository.save(relation);
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
            request.getList("/api/courses/" + course.getId() + "/course-competencies", HttpStatus.FORBIDDEN, CourseCompetency.class);
        }
    }

    @Nested
    class GetCompetencyCourseProgress {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetCompetencyCourseProgressWhenTeamExercise() throws Exception {
            User tutor = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
            var teams = teamUtilService.addTeamsForExerciseFixedTeamSize(TEST_PREFIX, "lgi", teamTextExercise, 2, tutor, 1);

            createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.getFirst(), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
            createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.getFirst(), 10.0, 0.0, 50, false);

            createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.get(1), 10.0, 0.0, 10, false);

            await().until(() -> participantScoreScheduleService.isIdle());

            CourseCompetencyProgressDTO courseCompetencyProgress = request
                    .get("/api/courses/" + course.getId() + "/course-competencies/" + prerequisite.getId() + "/course-progress", HttpStatus.OK, CourseCompetencyProgressDTO.class);

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

            CourseCompetencyProgressDTO courseCompetencyProgress = request.get("/api/courses/" + course.getId() + "/course-competencies/" + competency.getId() + "/course-progress",
                    HttpStatus.OK, CourseCompetencyProgressDTO.class);

            assertThat(courseCompetencyProgress.averageStudentScore()).isEqualTo(53.3);
        }
    }

    @Nested
    class GetCompetencyStudentProgress {

        private ProgrammingExercise[] programmingExercises;

        @BeforeEach
        void setupTestScenario() {
            programmingExercises = IntStream.range(1, 4).mapToObj(i -> createProgrammingExercise(i, Set.of(competency))).toArray(ProgrammingExercise[]::new);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void getCompetencyStudentProgressShouldReturnProgress() throws Exception {
            User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            lectureUnitService.setLectureUnitCompletion(textUnitRepository.findById(idOfTextUnitOfLectureOne).orElseThrow(), student1, true);

            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 90, true);
            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 85, false);

            await().until(() -> participantScoreScheduleService.isIdle());

            CompetencyProgress studentCompetencyProgress1 = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + competency.getId() + "/student-progress?refresh=true", HttpStatus.OK, CompetencyProgress.class);

            assertThat(studentCompetencyProgress1.getProgress()).isEqualTo(25);
            assertThat(studentCompetencyProgress1.getConfidence()).isEqualTo(0.75);

            lectureUnitService.setLectureUnitCompletion(attachmentUnitRepository.findById(idOfAttachmentUnitOfLectureOne).orElseThrow(), student1, true);

            CompetencyProgress studentCompetencyProgress2 = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + competency.getId() + "/student-progress?refresh=false", HttpStatus.OK, CompetencyProgress.class);

            assertThat(studentCompetencyProgress2.getProgress()).isEqualTo(25);
            assertThat(studentCompetencyProgress2.getConfidence()).isEqualTo(0.75);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void getCompetencyStudentProgressMultipleExercises() throws Exception {
            // The scheduling for all results interferes with the competency progress calculation, since only one per second is allowed
            // Therefore creating the participant scores manually
            participantScoreScheduleService.shutdown();

            User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            Result textResult = createTextExerciseParticipationSubmissionAndResult(textExercise, student1, textExercise.getMaxPoints(), 0.0, 90, true);
            Result programming1Result = createProgrammingExerciseParticipationSubmissionAndResult(programmingExercises[0], student1, 85, true, 10);
            Result programming2Result = createProgrammingExerciseParticipationSubmissionAndResult(programmingExercises[1], student1, 75, false, 1);
            Result programming3Result = createProgrammingExerciseParticipationSubmissionAndResult(programmingExercises[2], student1, 95, false, 1);

            studentScoreUtilService.createStudentScore(textExercise, student1, textResult);
            studentScoreUtilService.createStudentScore(programmingExercises[0], student1, programming1Result);
            studentScoreUtilService.createStudentScore(programmingExercises[1], student1, programming2Result);
            studentScoreUtilService.createStudentScore(programmingExercises[2], student1, programming3Result);

            CompetencyProgress studentCompetencyProgress = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + competency.getId() + "/student-progress?refresh=true", HttpStatus.OK, CompetencyProgress.class);

            // No lecture units are completed and no participation in team exercise
            assertThat(studentCompetencyProgress.getProgress()).isEqualTo(58);
            // Slightly more points in an easy exercise but solved one programming exercise quickly
            assertThat(studentCompetencyProgress.getConfidence()).isCloseTo(1.292, within(0.001));
        }
    }

    @Nested
    class GetCompetenciesForImport {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldNotGetCompetenciesForInstructorOfOtherCourse() throws Exception {
            // configure search so all competencies would get returned
            final var search = pageableSearchUtilService.configureCompetencySearch("", "", "", "");
            var result = request.getSearchResult("/api/course-competencies/for-import", HttpStatus.OK, CourseCompetency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetCompetenciesFromOwnCourses() throws Exception {
            final var search = pageableSearchUtilService.configureCompetencySearch(competency.getTitle(), "", "", "");
            var result = request.getSearchResult("/api/course-competencies/for-import", HttpStatus.OK, CourseCompetency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetCompetenciesAsAdmin() throws Exception {
            final var search = pageableSearchUtilService.configureCompetencySearch(competency.getTitle(), "", "", "");
            final var result = request.getSearchResult("/api/course-competencies/for-import", HttpStatus.OK, CourseCompetency.class,
                    pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }
    }

    @Nested
    class ImportAll {

        List<CompetencyWithTailRelationDTO> importAllCall(long courseId, long sourceCourseId, boolean importRelations, HttpStatus expectedStatus) throws Exception {
            return request.postListWithResponseBody(
                    "/api/courses/" + courseId + "/course-competencies/import-all/" + sourceCourseId + (importRelations ? "?importRelations=true" : ""), null,
                    CompetencyWithTailRelationDTO.class, expectedStatus);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldImportAllCompetencies() throws Exception {
            var course3 = courseUtilService.createCourse();

            var competencyDTOList = importAllCall(course.getId(), course3.getId(), false, HttpStatus.CREATED);

            assertThat(competencyDTOList).isEmpty();

            CourseCompetency head = competencyUtilService.createCompetency(course3);
            CourseCompetency tail = prerequisiteUtilService.createPrerequisite(course3);
            createRelation(tail, head, RelationType.ASSUMES);

            competencyDTOList = importAllCall(course.getId(), course3.getId(), true, HttpStatus.CREATED);

            assertThat(competencyDTOList).hasSize(2);
            // assert that only one of the DTOs has the relation connected
            if (competencyDTOList.getFirst().tailRelations() == null) {
                assertThat(competencyDTOList.get(1).tailRelations()).hasSize(1);
            }
            else {
                assertThat(competencyDTOList.get(1).tailRelations()).isNull();
            }

            competencyDTOList = importAllCall(course.getId(), course3.getId(), false, HttpStatus.CREATED);
            assertThat(competencyDTOList).hasSize(2);
            // relations should be empty when not importing them
            assertThat(competencyDTOList.getFirst().tailRelations()).isNull();
            assertThat(competencyDTOList.get(1).tailRelations()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestForImportFromSameCourse() throws Exception {
            importAllCall(course.getId(), course.getId(), false, HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class GetTitle {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnCompetencyTitleWhenCompetencyExists() throws Exception {
            String title = request.get("/api/course-competencies/" + competency.getId() + "/title", HttpStatus.OK, String.class);
            assertThat(title).isEqualTo(competency.getTitle());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnNotFoundWhenCompetencyNotExists() throws Exception {
            request.get("/api/course-competencies/12312321321/title", HttpStatus.NOT_FOUND, String.class);
        }
    }

    @Nested
    class GetCourseCompetencyTitles {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldGetCourseCompetencyTitles() throws Exception {
            competencyUtilService.createCompetencies(course, 5);
            var competencyTitles = courseCompetencyRepository.findAllForCourse(course.getId()).stream().map(CourseCompetency::getTitle).toList();
            prerequisiteUtilService.createPrerequisites(course, 5);
            var prerequisiteTitles = prerequisiteRepository.findAllByCourseIdOrderById(course.getId()).stream().map(CourseCompetency::getTitle).toList();
            var expectedTitles = Stream.concat(competencyTitles.stream(), prerequisiteTitles.stream()).toList();

            final var actualTitles = request.getList("/api/courses/" + course.getId() + "/course-competencies/titles", HttpStatus.OK, String.class);

            assertThat(actualTitles).containsAll(expectedTitles);
        }
    }

    @Nested
    class GetAllOfCourse {

        List<? extends CourseCompetency> getAllCall(long courseId, HttpStatus expectedStatus) throws Exception {
            return request.getList("/api/courses/" + courseId + "/course-competencies", expectedStatus, CourseCompetency.class);
        }

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
            courseCompetencyRepository.save(newCompetency);

            List<? extends CourseCompetency> competenciesOfCourse = getAllCall(course.getId(), HttpStatus.OK);

            assertThat(competenciesOfCourse).anyMatch(c -> c.getId().equals(competency.getId()));
            assertThat(competenciesOfCourse.stream().filter(l -> l.getId().equals(newCompetency.getId())).findFirst().orElseThrow().getLectureUnits()).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
        void testShouldReturnForbiddenForStudentNotInCourse() throws Exception {
            getAllCall(course.getId(), HttpStatus.FORBIDDEN);
        }
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

            request.postWithResponseBody("/api/courses/" + course.getId() + "/course-competencies/relations", CompetencyRelationDTO.of(relationToCreate), CompetencyRelation.class,
                    HttpStatus.OK);

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

            request.post("/api/courses/" + course.getId() + "/course-competencies/relations/", relationHeadNotPers, HttpStatus.NOT_FOUND);

            var relationTailNotPers = new CompetencyRelation();
            relationTailNotPers.setHeadCompetency(persistedCompetency);
            relationTailNotPers.setTailCompetency(notPersCompetency);

            request.post("/api/courses/" + course.getId() + "/course-competencies/relations/", relationTailNotPers, HttpStatus.NOT_FOUND);
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

            request.post("/api/courses/" + course.getId() + "/course-competencies/relations", CompetencyRelationDTO.of(relationToCreate), HttpStatus.BAD_REQUEST);
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

            request.post("/api/courses/" + course.getId() + "/course-competencies/relations", CompetencyRelationDTO.of(relation), HttpStatus.BAD_REQUEST);
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

        var actualRelations = request.getSet("/api/courses/" + course.getId() + "/course-competencies/relations", HttpStatus.OK, CompetencyRelationDTO.class);

        assertThat(actualRelations).hasSize(2);
        assertThat(actualRelations).isEqualTo(expectedRelations);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteCompetencyRelationShouldDeleteRelation() throws Exception {
        Competency competency2 = competencyUtilService.createCompetency(course);

        var relation = createRelation(competency, competency2, RelationType.EXTENDS);

        request.delete("/api/courses/" + course.getId() + "/course-competencies/relations/" + relation.getId(), HttpStatus.OK);

        var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        assertThat(relations).isEmpty();
    }

    @Nested
    class GetCourseCompetency {

        CourseCompetency getCall(long courseId, long competencyId, HttpStatus expectedStatus) throws Exception {
            return request.get("/api/courses/" + courseId + "/course-competencies/" + competencyId, expectedStatus, CourseCompetency.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnCompetencyForStudent() throws Exception {
            CourseCompetency response = getCall(course.getId(), competency.getId(), HttpStatus.OK);
            assertThat(response.getId()).isEqualTo(competency.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testShouldOnlySendUserSpecificData() throws Exception {
            User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
            competencyProgressUtilService.createCompetencyProgress(competency, student1, 0, 1);

            User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
            competencyProgressUtilService.createCompetencyProgress(competency, student2, 10, 1);

            final var textUnit = textUnitRepository.findById(idOfTextUnitOfLectureOne).get();
            lectureUtilService.completeLectureUnitForUser(textUnit, student2);

            CourseCompetency response = getCall(course.getId(), competency.getId(), HttpStatus.OK);
            assertThat(response.getId()).isEqualTo(competency.getId());

            // only progress of student1 is fetched
            assertThat(response.getUserProgress()).hasSize(1);

            // only student2 has completed the textUnit
            assertThat(response.getLectureUnits().stream().findFirst().get().getCompletedUsers()).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
        void shouldReturnForbiddenForUserNotInCourse() throws Exception {
            getCall(course.getId(), competency.getId(), HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class PreAuthorize {

        private void testAllPreAuthorizeEditor() throws Exception {
            request.get("/api/course-competencies/for-import", HttpStatus.FORBIDDEN, SearchResultPageDTO.class);
        }

        private void testAllPreAuthorizeInstructor() throws Exception {
            // relations
            CompetencyRelation relation = new CompetencyRelation();
            relation.setHeadCompetency(competency);
            relation.setTailCompetency(competency);
            relation.setType(RelationType.EXTENDS);
            request.post("/api/courses/" + course.getId() + "/course-competencies/relations", CompetencyRelationDTO.of(relation), HttpStatus.FORBIDDEN);
            request.getSet("/api/courses/" + course.getId() + "/course-competencies/relations", HttpStatus.FORBIDDEN, CompetencyRelationDTO.class);
            request.delete("/api/courses/" + course.getId() + "/course-competencies/relations/1", HttpStatus.FORBIDDEN);
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
}
