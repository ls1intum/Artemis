package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.competency.LearningPathUtilService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.CompetencyProgressService;
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.competency.*;

class LearningPathIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "learningpathintegration";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private LearningPathRepository learningPathRepository;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private CompetencyProgressService competencyProgressService;

    @Autowired
    private LearningPathUtilService learningPathUtilService;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Course course;

    private Competency[] competencies;

    private TextUnit textUnit;

    private static final int NUMBER_OF_STUDENTS = 5;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    private static final String TUTOR_OF_COURSE = TEST_PREFIX + "tutor1";

    private static final String EDITOR_OF_COURSE = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR_OF_COURSE = TEST_PREFIX + "instructor1";

    private User studentNotInCourse;

    @BeforeEach
    void enableLearningPathsFeatureToggle() {
        featureToggleService.enableFeature(Feature.LearningPaths);
    }

    @AfterEach
    void disableLearningPathsFeatureToggle() {
        featureToggleService.disableFeature(Feature.LearningPaths);
    }

    @BeforeEach
    void setupTestScenario() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        studentNotInCourse = userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        course = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1).get(0);
        competencies = competencyUtilService.createCompetencies(course, 5);

        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, past(1), future(1), future(2));
        List<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(textExercise, STUDENT_OF_COURSE);

        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        lectureRepository.save(lecture);

        textUnit = lectureUtilService.createTextUnit();
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(textUnit));

        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        lectureUnitService.setLectureUnitCompletion(textUnit, student, true);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private ZonedDateTime past(long days) {
        return now().minusDays(days);
    }

    private ZonedDateTime future(long days) {
        return now().plusDays(days);
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/courses/" + course.getId() + "/learning-paths/enable", null, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course.getId() + "/learning-paths/generate-missing", null, HttpStatus.FORBIDDEN);
        final var search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.FORBIDDEN, LearningPath.class, pageableSearchUtilService.searchMapping(search));
        request.get("/api/courses/" + course.getId() + "/learning-path-health", HttpStatus.FORBIDDEN, LearningPathHealthDTO.class);
    }

    private void enableLearningPathsRESTCall(Course course) throws Exception {
        request.put("/api/courses/" + course.getId() + "/learning-paths/enable", null, HttpStatus.OK);
    }

    private Competency createCompetencyRESTCall() throws Exception {
        final var competencyToCreate = new Competency();
        competencyToCreate.setTitle("CompetencyToCreateTitle");
        competencyToCreate.setCourse(course);
        competencyToCreate.setLectureUnits(Set.of(textUnit));
        return request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies", competencyToCreate, Competency.class, HttpStatus.CREATED);
    }

    private Competency importCompetencyRESTCall() throws Exception {
        final var course2 = courseUtilService.createCourse();
        final var competencyToImport = competencyUtilService.createCompetency(course2);
        return request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies/import", competencyToImport, Competency.class, HttpStatus.CREATED);
    }

    private void deleteCompetencyRESTCall(Competency competency) throws Exception {
        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TUTOR_OF_COURSE, roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = EDITOR_OF_COURSE, roles = "EDITOR")
    void testAll_asEditor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPaths() throws Exception {
        enableLearningPathsRESTCall(course);
        final var updatedCourse = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(course.getId());
        assertThat(updatedCourse.getLearningPathsEnabled()).as("should enable LearningPaths").isTrue();
        assertThat(updatedCourse.getLearningPaths()).isNotNull();
        assertThat(updatedCourse.getLearningPaths().size()).as("should create LearningPath for each student").isEqualTo(NUMBER_OF_STUDENTS);
        updatedCourse.getLearningPaths().forEach(
                lp -> assertThat(lp.getCompetencies().size()).as("LearningPath (id={}) should have be linked to all Competencies", lp.getId()).isEqualTo(competencies.length));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPathsWithNoCompetencies() throws Exception {
        var courseWithoutCompetencies = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, false, 0).get(0);
        enableLearningPathsRESTCall(courseWithoutCompetencies);
        final var updatedCourse = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseWithoutCompetencies.getId());
        assertThat(updatedCourse.getLearningPathsEnabled()).as("should enable LearningPaths").isTrue();
        assertThat(updatedCourse.getLearningPaths()).isNotNull();
        assertThat(updatedCourse.getLearningPaths().size()).as("should create LearningPath for each student").isEqualTo(NUMBER_OF_STUDENTS);
        updatedCourse.getLearningPaths().forEach(lp -> assertThat(lp.getProgress()).as("LearningPath (id={}) should have no progress", lp.getId()).isEqualTo(0));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPathsAlreadyEnabled() throws Exception {
        course.setLearningPathsEnabled(true);
        courseRepository.save(course);
        request.put("/api/courses/" + course.getId() + "/learning-paths/enable", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGenerateMissingLearningPathsForCourse() throws Exception {
        course.setLearningPathsEnabled(true);
        courseRepository.save(course);
        final var students = userRepository.getStudents(course);
        students.stream().map(User::getId).map(userRepository::findWithLearningPathsByIdElseThrow).forEach(learningPathUtilService::deleteLearningPaths);
        request.put("/api/courses/" + course.getId() + "/learning-paths/generate-missing", null, HttpStatus.OK);
        students.forEach(user -> {
            user = userRepository.findWithLearningPathsByIdElseThrow(user.getId());
            assertThat(user.getLearningPaths().size()).isEqualTo(1);
        });

    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGenerateMissingLearningPathsForCourseNotEnabled() throws Exception {
        request.put("/api/courses/" + course.getId() + "/learning-paths/generate-missing", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1337", roles = "USER")
    void testGenerateLearningPathOnEnrollment() throws Exception {
        course.setEnrollmentEnabled(true);
        course.setEnrollmentStartDate(past(1));
        course.setEnrollmentEndDate(future(1));
        course = courseRepository.save(course);
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        this.setupEnrollmentRequestMocks();

        request.postWithResponseBody("/api/courses/" + course.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        final var user = userRepository.findOneWithLearningPathsByLogin(TEST_PREFIX + "student1337").orElseThrow();

        assertThat(user.getLearningPaths()).isNotNull();
        assertThat(user.getLearningPaths().size()).as("should create LearningPath for student").isEqualTo(1);
    }

    private void setupEnrollmentRequestMocks() throws JsonProcessingException, URISyntaxException {
        jiraRequestMockProvider.enableMockingOfRequests();
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course.getStudentGroupName()));
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockUpdateUserDetails(studentNotInCourse.getLogin(), studentNotInCourse.getEmail(), studentNotInCourse.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseLearningPathsDisabled() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.BAD_REQUEST, LearningPathPageableSearchDTO.class,
                pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseEmpty() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var search = pageableSearchUtilService.configureSearch(STUDENT_OF_COURSE + "SuffixThatAllowsTheResultToBeEmpty");
        final var result = request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPathPageableSearchDTO.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseExactlyStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var search = pageableSearchUtilService.configureSearch(STUDENT_OF_COURSE);
        final var result = request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPathPageableSearchDTO.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    private static Stream<Arguments> addCompetencyToLearningPathsOnCreateAndImportCompetencyTestProvider() {
        final Function<LearningPathIntegrationTest, Competency> createCall = (reference) -> {
            try {
                return reference.createCompetencyRESTCall();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        final Function<LearningPathIntegrationTest, Competency> importCall = (reference) -> {
            try {
                return reference.importCompetencyRESTCall();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        return Stream.of(Arguments.of(createCall), Arguments.of(importCall));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    @MethodSource("addCompetencyToLearningPathsOnCreateAndImportCompetencyTestProvider")
    void addCompetencyToLearningPaths(Function<LearningPathIntegrationTest, Competency> restCall) {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        final var newCompetency = restCall.apply(this);

        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPathOptional = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserId(course.getId(), student.getId());
        assertThat(learningPathOptional).isPresent();
        assertThat(learningPathOptional.get().getCompetencies()).as("should contain new competency").contains(newCompetency);
        assertThat(learningPathOptional.get().getCompetencies().size()).as("should not remove old competencies").isEqualTo(competencies.length + 1);
        final var oldCompetencies = Set.of(competencies[0], competencies[1], competencies[2], competencies[3], competencies[4]);
        assertThat(learningPathOptional.get().getCompetencies()).as("should not remove old competencies").containsAll(oldCompetencies);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testRemoveCompetencyFromLearningPathsOnDeleteCompetency() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        deleteCompetencyRESTCall(competencies[0]);

        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPathOptional = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserId(course.getId(), student.getId());
        assertThat(learningPathOptional).isPresent();
        assertThat(learningPathOptional.get().getCompetencies()).as("should not contain deleted competency").doesNotContain(competencies[0]);
        final var nonDeletedCompetencies = Set.of(competencies[1], competencies[2], competencies[3], competencies[4]);
        assertThat(learningPathOptional.get().getCompetencies().size()).as("should contain competencies that have not been deleted").isEqualTo(nonDeletedCompetencies.size());
        assertThat(learningPathOptional.get().getCompetencies()).as("should contain competencies that have not been deleted").containsAll(nonDeletedCompetencies);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testUpdateLearningPathProgress() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        // add competency with completed learning unit
        final var createdCompetency = createCompetencyRESTCall();

        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        var learningPath = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        assertThat(learningPath.getProgress()).as("contains no completed competency").isEqualTo(0);

        // force update to avoid waiting for scheduler
        competencyProgressService.updateCompetencyProgress(createdCompetency.getId(), student);

        learningPath = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        assertThat(learningPath.getProgress()).as("contains completed competency").isNotEqualTo(0);
    }

    /**
     * This only tests if the end point successfully retrieves the health status. The correctness of the health status is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.in.www1.artemis.service.LearningPathServiceTest
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetHealthStatusForCourse() throws Exception {
        request.get("/api/courses/" + course.getId() + "/learning-path-health", HttpStatus.OK, LearningPathHealthDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNgxGraphForLearningPathsDisabled() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        course.setLearningPathsEnabled(false);
        courseRepository.save(course);
        request.get("/api/learning-path/" + learningPath.getId() + "/graph", HttpStatus.BAD_REQUEST, NgxLearningPathDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetLearningPathNgxGraphForOtherStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/graph", HttpStatus.FORBIDDEN, NgxLearningPathDTO.class);
    }

    /**
     * This only tests if the end point successfully retrieves the graph representation. The correctness of the response is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.in.www1.artemis.service.LearningPathServiceTest
     */
    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNgxGraphAsStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/graph", HttpStatus.OK, NgxLearningPathDTO.class);
    }

    /**
     * This only tests if the end point successfully retrieves the graph representation. The correctness of the response is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.in.www1.artemis.service.LearningPathServiceTest
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathNgxGraphAsInstructor() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/graph", HttpStatus.OK, NgxLearningPathDTO.class);
    }
}
