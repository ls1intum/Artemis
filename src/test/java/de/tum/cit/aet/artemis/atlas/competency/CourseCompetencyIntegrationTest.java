package de.tum.cit.aet.artemis.atlas.competency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.glassfish.jersey.internal.util.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.UpdateCourseCompetencyRelationDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseCompetencyProgressDTO;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class CourseCompetencyIntegrationTest extends AbstractCompetencyPrerequisiteIntegrationTest {

    private static final String TEST_PREFIX = "coursecompetencyintegrationtest";

    @BeforeEach
    void setupTestScenario() {
        super.setupTestScenario(TEST_PREFIX, course -> competencyUtilService.createCompetency(course, "penguin"));

        participantScoreScheduleService.activate();
    }

    private Result createExerciseParticipationSubmissionAndResult(Exercise exercise, StudentParticipation studentParticipation, double pointsOfExercise,
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
        return createExerciseParticipationSubmissionAndResult(exercise, studentParticipation, pointsOfExercise, bonusPointsOfExercise, scoreAwarded, rated, TextSubmission::new, 1);
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
        return createExerciseParticipationSubmissionAndResult(exercise, studentParticipation, exercise.getMaxPoints(), 0, scoreAwarded, rated, ProgrammingSubmission::new,
                numberOfSubmissions);
    }

    @Override
    CourseCompetency getCall(long courseId, long competencyId, HttpStatus expectedStatus) throws Exception {
        return request.get("/api/courses/" + courseId + "/course-competencies/" + competencyId, expectedStatus, CourseCompetency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCompetencyForStudent() throws Exception {
        super.shouldReturnCompetencyForStudent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testShouldOnlySendUserSpecificData() throws Exception {
        super.testShouldOnlySendUserSpecificData(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForUserNotInCourse() throws Exception {
        super.shouldReturnForbiddenForUserNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestForWrongCourse() throws Exception {
        super.shouldReturnBadRequestForWrongCourse();
    }

    @Override
    List<? extends CourseCompetency> getAllCall(long courseId, HttpStatus expectedStatus) throws Exception {
        return request.getList("/api/courses/" + courseId + "/course-competencies", expectedStatus, CourseCompetency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCompetenciesForStudentOfCourse() throws Exception {
        super.shouldReturnCompetenciesForCourse(new Competency());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testShouldReturnForbiddenForStudentNotInCourse() throws Exception {
        super.testShouldReturnForbiddenForStudentNotInCourse();
    }

    @Override
    void deleteCall(long courseId, long competencyId, HttpStatus expectedStatus) {
        throw new UnsupportedOperationException("Not provided");
    }

    @Override
    CourseCompetency updateCall(long courseId, CourseCompetency competency, HttpStatus expectedStatus) {
        throw new UnsupportedOperationException("Not provided");
    }

    @Override
    CourseCompetency createCall(long courseId, CourseCompetency competency, HttpStatus expectedStatus) {
        throw new UnsupportedOperationException("Not provided");
    }

    @Override
    CourseCompetency importCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) {
        throw new UnsupportedOperationException("Not provided");
    }

    @Override
    List<? extends CourseCompetency> createBulkCall(long courseId, List<? extends CourseCompetency> competencies, HttpStatus expectedStatus) {
        throw new UnsupportedOperationException("Not provided");
    }

    @Override
    List<CompetencyWithTailRelationDTO> importAllCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) throws Exception {
        return request.postListWithResponseBody("/api/courses/" + courseId + "/course-competencies/import-all", importOptions, CompetencyWithTailRelationDTO.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportAllCompetencies() throws Exception {
        super.shouldImportAllCompetencies(competencyUtilService::createCompetency);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportAllExerciseAndLectureWithCompetency() throws Exception {
        super.shouldImportAllExerciseAndLectureWithCompetency();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldImportAllExerciseAndLectureWithCompetencyAndChangeDates() throws Exception {
        super.shouldImportAllExerciseAndLectureWithCompetencyAndChangeDates();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorNotInCourse() throws Exception {
        super.shouldReturnForbiddenForInstructorNotInCourse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnBadRequestForImportFromSameCourse() throws Exception {
        super.shouldReturnBadRequestForImportFromSameCourse();
    }

    @Override
    List<CompetencyImportResponseDTO> importStandardizedCall(long courseId, List<Long> idList, HttpStatus expectedStatus) {
        throw new UnsupportedOperationException("Not provided");
    }

    @Override
    List<CompetencyWithTailRelationDTO> importBulkCall(long courseId, CompetencyImportOptionsDTO importOptions, HttpStatus expectedStatus) {
        throw new UnsupportedOperationException("Not provided");
    }

    @Nested
    class GetCompetencyCourseProgress {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetCompetencyCourseProgressWhenTeamExercise() throws Exception {
            User tutor = userTestRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
            var teams = teamUtilService.addTeamsForExerciseFixedTeamSize(TEST_PREFIX, "lgi", teamTextExercise, 2, tutor, 1);

            createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.getFirst(), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
            createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.getFirst(), 10.0, 0.0, 50, false);

            createTextExerciseParticipationSubmissionAndResult(teamTextExercise, teams.get(1), 10.0, 0.0, 10, false);

            await().until(() -> participantScoreScheduleService.isIdle());

            CourseCompetencyProgressDTO courseCompetencyProgress = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + courseCompetency.getId() + "/course-progress", HttpStatus.OK, CourseCompetencyProgressDTO.class);

            assertThat(courseCompetencyProgress.averageStudentScore()).isEqualTo(30);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetCompetencyCourseProgress() throws Exception {
            User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            User student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
            User instructor1 = userTestRepository.findOneByLogin(TEST_PREFIX + "instructor1").orElseThrow();

            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 50, false);
            createTextExerciseParticipationSubmissionAndResult(textExercise, student2, 10.0, 0.0, 10, false);

            createTextExerciseParticipationSubmissionAndResult(textExercise, instructor1, 10.0, 0.0, 100, true); // will be ignored as not a student

            await().until(() -> participantScoreScheduleService.isIdle());

            CourseCompetencyProgressDTO courseCompetencyProgress = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + courseCompetency.getId() + "/course-progress", HttpStatus.OK, CourseCompetencyProgressDTO.class);

            assertThat(courseCompetencyProgress.averageStudentScore()).isEqualTo(53.3);
        }
    }

    @Nested
    class GetCompetencyStudentProgress {

        private ProgrammingExercise[] programmingExercises;

        @BeforeEach
        void setupTestScenario() {
            programmingExercises = IntStream.range(1, 4).mapToObj(i -> createProgrammingExercise(i, Set.of(courseCompetency))).toArray(ProgrammingExercise[]::new);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void getCompetencyStudentProgressShouldReturnProgress() throws Exception {
            User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            lectureUnitService.setLectureUnitCompletion(textUnitRepository.findById(textUnitOfLectureOne.getId()).orElseThrow(), student1, true);

            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 90, true);
            createTextExerciseParticipationSubmissionAndResult(textExercise, student1, 10.0, 0.0, 85, false);

            await().until(() -> participantScoreScheduleService.isIdle());

            CompetencyProgress studentCompetencyProgress1 = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + courseCompetency.getId() + "/student-progress?refresh=true", HttpStatus.OK,
                    CompetencyProgress.class);

            assertThat(studentCompetencyProgress1.getProgress()).isEqualTo(22);
            assertThat(studentCompetencyProgress1.getConfidence()).isEqualTo(0.75);

            lectureUnitService.setLectureUnitCompletion(attachmentUnitRepository.findById(attachmentUnitOfLectureOne.getId()).orElseThrow(), student1, true);

            CompetencyProgress studentCompetencyProgress2 = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + courseCompetency.getId() + "/student-progress?refresh=false", HttpStatus.OK,
                    CompetencyProgress.class);

            assertThat(studentCompetencyProgress2.getProgress()).isEqualTo(22);
            assertThat(studentCompetencyProgress2.getConfidence()).isEqualTo(0.75);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void getCompetencyStudentProgressMultipleExercises() throws Exception {
            // The scheduling for all results interferes with the competency progress calculation, since only one per second is allowed
            // Therefore creating the participant scores manually
            participantScoreScheduleService.shutdown();

            User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
            Result textResult = createTextExerciseParticipationSubmissionAndResult(textExercise, student1, textExercise.getMaxPoints(), 0.0, 90, true);
            Result programming1Result = createProgrammingExerciseParticipationSubmissionAndResult(programmingExercises[0], student1, 85, true, 10);
            Result programming2Result = createProgrammingExerciseParticipationSubmissionAndResult(programmingExercises[1], student1, 75, false, 1);
            Result programming3Result = createProgrammingExerciseParticipationSubmissionAndResult(programmingExercises[2], student1, 95, false, 1);

            studentScoreUtilService.createStudentScore(textExercise, student1, textResult);
            studentScoreUtilService.createStudentScore(programmingExercises[0], student1, programming1Result);
            studentScoreUtilService.createStudentScore(programmingExercises[1], student1, programming2Result);
            studentScoreUtilService.createStudentScore(programmingExercises[2], student1, programming3Result);

            CompetencyProgress studentCompetencyProgress = request.get(
                    "/api/courses/" + course.getId() + "/course-competencies/" + courseCompetency.getId() + "/student-progress?refresh=true", HttpStatus.OK,
                    CompetencyProgress.class);

            // No lecture units are completed and no participation in team exercise
            assertThat(studentCompetencyProgress.getProgress()).isEqualTo(54);
            // Slightly more points in an easy exercise but solved one programming exercise quickly
            assertThat(studentCompetencyProgress.getConfidence()).isCloseTo(1.327, within(0.001));
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
            final var search = pageableSearchUtilService.configureCompetencySearch(courseCompetency.getTitle(), "", course.getTitle(), "");
            var result = request.getSearchResult("/api/course-competencies/for-import", HttpStatus.OK, CourseCompetency.class, pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldGetCompetenciesAsAdmin() throws Exception {
            final var search = pageableSearchUtilService.configureCompetencySearch(courseCompetency.getTitle(), "", course.getTitle(), "");
            final var result = request.getSearchResult("/api/course-competencies/for-import", HttpStatus.OK, CourseCompetency.class,
                    pageableSearchUtilService.searchMapping(search));
            assertThat(result.getResultsOnPage()).hasSize(1);
        }
    }

    @Nested
    class GetTitle {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnCompetencyTitleWhenCompetencyExists() throws Exception {
            String title = request.get("/api/course-competencies/" + courseCompetency.getId() + "/title", HttpStatus.OK, String.class);
            assertThat(title).isEqualTo(courseCompetency.getTitle());
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
    class CreateCompetencyRelation {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldUpdateForInstructor() throws Exception {
            var headCompetency = competencyUtilService.createCompetency(course);
            var relationToCreate = new CompetencyRelation();
            relationToCreate.setTailCompetency(courseCompetency);
            relationToCreate.setHeadCompetency(headCompetency);
            relationToCreate.setType(RelationType.EXTENDS);

            request.postWithResponseBody("/api/courses/" + course.getId() + "/course-competencies/relations", CompetencyRelationDTO.of(relationToCreate), CompetencyRelation.class,
                    HttpStatus.OK);

            var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
            assertThat(relations).hasSize(1);
            var relation = relations.stream().findFirst().get();
            assertThat(relation.getType()).isEqualTo(RelationType.EXTENDS);

            request.patch("/api/courses/" + course.getId() + "/course-competencies/relations/" + relation.getId(), new UpdateCourseCompetencyRelationDTO(RelationType.MATCHES),
                    HttpStatus.OK);

            relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
            assertThat(relations).hasSize(1);
            assertThat(relations.stream().findFirst().get().getType()).isEqualTo(RelationType.MATCHES);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateForInstructor() throws Exception {
            var headCompetency = competencyUtilService.createCompetency(course);
            var relationToCreate = new CompetencyRelation();
            relationToCreate.setTailCompetency(courseCompetency);
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
            relationToCreate.setTailCompetency(courseCompetency);
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

            createRelation(courseCompetency, competency2, RelationType.EXTENDS);
            createRelation(competency2, competency3, RelationType.MATCHES);

            var relation = new CompetencyRelation();
            relation.setTailCompetency(competency3);
            relation.setHeadCompetency(courseCompetency);
            relation.setType(RelationType.ASSUMES);

            request.post("/api/courses/" + course.getId() + "/course-competencies/relations", CompetencyRelationDTO.of(relation), HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCompetencyRelationsShouldGetRelations() throws Exception {
        Competency competency2 = competencyUtilService.createCompetency(course);
        Competency competency3 = competencyUtilService.createCompetency(course);

        var relation = createRelation(courseCompetency, competency2, RelationType.EXTENDS);
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

        var relation = createRelation(courseCompetency, competency2, RelationType.EXTENDS);

        request.delete("/api/courses/" + course.getId() + "/course-competencies/relations/" + relation.getId(), HttpStatus.OK);

        var relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        assertThat(relations).isEmpty();
    }

    @Nested
    class PreAuthorize {

        private void testAllPreAuthorizeEditor() throws Exception {
            request.get("/api/course-competencies/for-import", HttpStatus.FORBIDDEN, SearchResultPageDTO.class);
        }

        private void testAllPreAuthorizeInstructor() throws Exception {
            // relations
            CompetencyRelation relation = new CompetencyRelation();
            relation.setHeadCompetency(courseCompetency);
            relation.setTailCompetency(courseCompetency);
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
