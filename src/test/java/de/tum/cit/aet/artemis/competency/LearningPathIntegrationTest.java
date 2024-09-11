package de.tum.cit.aet.artemis.competency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.StudentScoreUtilService;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseUtilService;
import de.tum.cit.aet.artemis.lecture.LectureUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.service.LectureUnitService;
import de.tum.cit.aet.artemis.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.web.rest.LearningPathResource;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyGraphNodeDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyNameDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyWithTailRelationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.LearningPathCompetencyGraphDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.LearningPathHealthDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.LearningPathInformationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.LearningPathNavigationDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.LearningPathNavigationOverviewDTO;
import de.tum.cit.aet.artemis.web.rest.dto.competency.NgxLearningPathDTO;

class LearningPathIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "learningpathintegration";

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private LearningPathRepository learningPathRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

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
    private CompetencyProgressRepository competencyProgressRepository;

    @Autowired
    private CompetencyRelationRepository competencyRelationRepository;

    @Autowired
    private StudentScoreUtilService studentScoreUtilService;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    private Course course;

    private Competency[] competencies;

    private TextExercise textExercise;

    private TextUnit textUnit;

    private Lecture lecture;

    private static final int NUMBER_OF_STUDENTS = 5;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    private static final String TUTOR_OF_COURSE = TEST_PREFIX + "tutor1";

    private static final String EDITOR_OF_COURSE = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR_OF_COURSE = TEST_PREFIX + "instructor1";

    @BeforeEach
    void setupTestScenario() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        course = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1).getFirst();
        competencies = competencyUtilService.createCompetencies(course, 5);

        // set threshold to 60, 70, 80, 90 and 100 respectively
        for (int i = 0; i < competencies.length; i++) {
            competencies[i] = competencyUtilService.updateMasteryThreshold(competencies[i], 60 + i * 10);
        }

        for (int i = 1; i < competencies.length; i++) {
            var relation = new CompetencyRelation();
            relation.setHeadCompetency(competencies[i - 1]);
            relation.setTailCompetency(competencies[i]);
            relation.setType(RelationType.EXTENDS);
            competencyRelationRepository.save(relation);
        }

        lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        lectureRepository.save(lecture);

        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();

        textUnit = createAndLinkTextUnit(student, competencies[0], true);
        textExercise = createAndLinkTextExercise(competencies[1], false);
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
        return request.postWithResponseBody("/api/courses/" + course.getId() + "/competencies/import", competencyToImport.getId(), Competency.class, HttpStatus.CREATED);
    }

    private List<CompetencyWithTailRelationDTO> importCompetenciesRESTCall(int numberOfCompetencies) throws Exception {
        final var course2 = courseUtilService.createCourse();
        for (int i = 0; i < numberOfCompetencies; i++) {
            competencyUtilService.createCompetency(course2);
        }
        return request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/import-all/" + course2.getId(), null, CompetencyWithTailRelationDTO.class,
                HttpStatus.CREATED);
    }

    private void deleteCompetencyRESTCall(Competency competency) throws Exception {
        request.delete("/api/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
        request.get("/api/courses/" + course.getId() + "/learning-path-id", HttpStatus.BAD_REQUEST, Long.class);
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
        final var updatedCourse = courseRepository.findWithEagerLearningPathsAndCompetenciesAndPrerequisitesByIdElseThrow(course.getId());
        assertThat(updatedCourse.getLearningPathsEnabled()).as("should enable LearningPaths").isTrue();
        assertThat(updatedCourse.getLearningPaths()).isNotNull();
        assertThat(updatedCourse.getLearningPaths().size()).as("should create LearningPath for each student").isEqualTo(NUMBER_OF_STUDENTS);
        updatedCourse.getLearningPaths().forEach(
                lp -> assertThat(lp.getCompetencies().size()).as("LearningPath (id={}) should have be linked to all Competencies", lp.getId()).isEqualTo(competencies.length));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPathsWithNoCompetencies() throws Exception {
        var courseWithoutCompetencies = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, false, 0).getFirst();
        enableLearningPathsRESTCall(courseWithoutCompetencies);
        final var updatedCourse = courseRepository.findWithEagerLearningPathsAndCompetenciesAndPrerequisitesByIdElseThrow(courseWithoutCompetencies.getId());
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
            assertThat(user.getLearningPaths()).hasSize(1);
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

        request.postWithResponseBody("/api/courses/" + course.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        final var user = userRepository.findOneWithLearningPathsByLogin(TEST_PREFIX + "student1337").orElseThrow();

        assertThat(user.getLearningPaths()).isNotNull();
        assertThat(user.getLearningPaths().size()).as("should create LearningPath for student").isEqualTo(1);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseLearningPathsDisabled() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.BAD_REQUEST, LearningPathInformationDTO.class,
                pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseEmpty() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var search = pageableSearchUtilService.configureSearch(STUDENT_OF_COURSE + "SuffixThatAllowsTheResultToBeEmpty");
        final var result = request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPathInformationDTO.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseExactlyStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var search = pageableSearchUtilService.configureSearch(STUDENT_OF_COURSE);
        final var result = request.getSearchResult("/api/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPathInformationDTO.class,
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
    void testAddCompetenciesToLearningPaths() throws Exception {
        final int numberOfNewCompetencies = 3;
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        final var competencyDTOs = importCompetenciesRESTCall(numberOfNewCompetencies);
        final var newCompetencies = competencyDTOs.stream().map(CompetencyWithTailRelationDTO::competency).toList();

        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPathOptional = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserId(course.getId(), student.getId());

        assertThat(newCompetencies).hasSize(numberOfNewCompetencies);
        assertThat(learningPathOptional).isPresent();
        assertThat(learningPathOptional.get().getCompetencies()).as("should contain new competencies").containsAll(newCompetencies);
        assertThat(learningPathOptional.get().getCompetencies().size()).as("should not remove old competencies").isEqualTo(competencies.length + newCompetencies.size());
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

        verify(competencyProgressService).updateProgressByCompetencyAndUsersInCourseAsync(eq(createdCompetency));
    }

    /**
     * This only tests if the end point successfully retrieves the health status. The correctness of the health status is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.cit.aet.artemis.service.LearningPathServiceTest
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetHealthStatusForCourse() throws Exception {
        request.get("/api/courses/" + course.getId() + "/learning-path-health", HttpStatus.OK, LearningPathHealthDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathWithOwner() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId(), HttpStatus.OK, NgxLearningPathDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathOfOtherUser() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var otherStudent = userRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), otherStudent.getId());
        request.get("/api/learning-path/" + learningPath.getId(), HttpStatus.FORBIDDEN, NgxLearningPathDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathCompetencyGraphOfOtherUser() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var otherStudent = userRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), otherStudent.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/competency-graph", HttpStatus.FORBIDDEN, LearningPathCompetencyGraphDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathCompetencyGraph() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        Arrays.stream(competencies).forEach(competency -> competencyProgressService.updateCompetencyProgress(competency.getId(), student));

        LearningPathCompetencyGraphDTO response = request.get("/api/learning-path/" + learningPath.getId() + "/competency-graph", HttpStatus.OK,
                LearningPathCompetencyGraphDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.nodes().stream().map(CompetencyGraphNodeDTO::id))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(competencies).map(Competency::getId).map(Object::toString).toList());
        assertThat(response.nodes()).allMatch(nodeDTO -> {
            var progress = competencyProgressRepository.findByCompetencyIdAndUserIdOrElseThrow(Long.parseLong(nodeDTO.id()), student.getId());
            var masteryProgress = CompetencyProgressService.getMasteryProgress(progress);
            return Objects.equals(nodeDTO.value(), Math.floor(masteryProgress * 100))
                    && Objects.equals(nodeDTO.valueType(), CompetencyGraphNodeDTO.CompetencyNodeValueType.MASTERY_PROGRESS);
        });

        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        assertThat(response.edges()).hasSameSizeAs(relations);
        assertThat(response.edges()).allMatch(relationDTO -> relations.stream().anyMatch(relation -> relation.getId() == Long.parseLong(relationDTO.id())
                && relation.getTailCompetency().getId() == Long.parseLong(relationDTO.target()) && relation.getHeadCompetency().getId() == Long.parseLong(relationDTO.source())));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(LearningPathResource.NgxRequestType.class)
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNgxForLearningPathsDisabled(LearningPathResource.NgxRequestType type) throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        course.setLearningPathsEnabled(false);
        courseRepository.save(course);
        request.get("/api/learning-path/" + learningPath.getId() + "/" + type, HttpStatus.BAD_REQUEST, NgxLearningPathDTO.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(LearningPathResource.NgxRequestType.class)
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetLearningPathNgxForOtherStudent(LearningPathResource.NgxRequestType type) throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/" + type, HttpStatus.FORBIDDEN, NgxLearningPathDTO.class);
    }

    /**
     * This only tests if the end point successfully retrieves the graph representation. The correctness of the response is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.cit.aet.artemis.service.LearningPathServiceTest
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(LearningPathResource.NgxRequestType.class)
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNgxAsStudent(LearningPathResource.NgxRequestType type) throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/" + type, HttpStatus.OK, NgxLearningPathDTO.class);
    }

    /**
     * This only tests if the end point successfully retrieves the graph representation. The correctness of the response is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.cit.aet.artemis.service.LearningPathServiceTest
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(LearningPathResource.NgxRequestType.class)
    @WithMockUser(username = TUTOR_OF_COURSE, roles = "TA")
    void testGetLearningPathNgxAsTutor(LearningPathResource.NgxRequestType type) throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var tutor = userRepository.findOneByLogin(TUTOR_OF_COURSE).orElseThrow();
        final var learningPath = learningPathUtilService.createLearningPathInCourseForUser(course, tutor);
        request.get("/api/learning-path/" + learningPath.getId() + "/" + type, HttpStatus.OK, NgxLearningPathDTO.class);
    }

    /**
     * This only tests if the end point successfully retrieves the graph representation. The correctness of the response is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.cit.aet.artemis.service.LearningPathServiceTest
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(LearningPathResource.NgxRequestType.class)
    @WithMockUser(username = EDITOR_OF_COURSE, roles = "EDITOR")
    void testGetLearningPathNgxAsEditor(LearningPathResource.NgxRequestType type) throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var editor = userRepository.findOneByLogin(EDITOR_OF_COURSE).orElseThrow();
        final var learningPath = learningPathUtilService.createLearningPathInCourseForUser(course, editor);
        request.get("/api/learning-path/" + learningPath.getId() + "/" + type, HttpStatus.OK, NgxLearningPathDTO.class);
    }

    /**
     * This only tests if the end point successfully retrieves the graph representation. The correctness of the response is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.cit.aet.artemis.service.LearningPathServiceTest
     */
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(LearningPathResource.NgxRequestType.class)
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathNgxAsInstructor(LearningPathResource.NgxRequestType type) throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/" + type, HttpStatus.OK, NgxLearningPathDTO.class);
    }

    @Nested
    class GetLearningPathId {

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnExistingId() throws Exception {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
            final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            final var result = request.get("/api/courses/" + course.getId() + "/learning-path-id", HttpStatus.OK, Long.class);
            assertThat(result).isEqualTo(learningPath.getId());
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnNotFoundIfNotExists() throws Exception {
            course.setLearningPathsEnabled(true);
            course = courseRepository.save(course);
            var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
            student = userRepository.findWithLearningPathsByIdElseThrow(student.getId());
            learningPathRepository.deleteAll(student.getLearningPaths());
            request.get("/api/courses/" + course.getId() + "/learning-path-id", HttpStatus.NOT_FOUND, Long.class);
        }
    }

    @Nested
    class GenerateLearningPath {

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnForbiddenIfNotEnabled() throws Exception {
            request.postWithResponseBody("/api/courses/" + course.getId() + "/learning-path", null, Long.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldReturnBadRequestIfAlreadyExists() throws Exception {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            request.postWithResponseBody("/api/courses/" + course.getId() + "/learning-path", null, Long.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
        void shouldGenerateLearningPath() throws Exception {
            course.setLearningPathsEnabled(true);
            course = courseRepository.save(course);
            final var response = request.postWithResponseBody("/api/courses/" + course.getId() + "/learning-path", null, Long.class, HttpStatus.CREATED);
            assertThat(response).isNotNull();
            final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
            final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            assertThat(learningPath).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetCompetencyProgressForLearningPathByOtherStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/competency-progress", HttpStatus.FORBIDDEN, Set.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetCompetencyProgressForLearningPathByOwner() throws Exception {
        testGetCompetencyProgressForLearningPath();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetCompetencyProgressForLearningPathByInstructor() throws Exception {
        testGetCompetencyProgressForLearningPath();
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigation() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        competencyProgressService.updateProgressByLearningObjectSync(textUnit, Set.of(student));

        final var result = request.get("/api/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);

        verifyNavigationResult(result, textUnit, textExercise, null);
        assertThat(result.progress()).isEqualTo(20);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationEmptyCompetencies() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        textExercise.setCompetencies(Set.of());
        textExercise = exerciseRepository.save(textExercise);

        TextUnit secondTextUnit = createAndLinkTextUnit(student, competencies[2], false);
        TextUnit thirdTextUnit = createAndLinkTextUnit(student, competencies[4], false);

        var result = request.get("/api/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, textUnit, secondTextUnit, thirdTextUnit);

        lectureUnitService.setLectureUnitCompletion(secondTextUnit, student, true);
        result = request.get("/api/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, secondTextUnit, thirdTextUnit, null);

        lectureUnitService.setLectureUnitCompletion(thirdTextUnit, student, true);
        result = request.get("/api/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, thirdTextUnit, null, null);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationDoesNotLeakUnreleasedLearningObjects() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        textExercise.setCompetencies(Set.of());
        textExercise = exerciseRepository.save(textExercise);

        TextUnit secondTextUnit = createAndLinkTextUnit(student, competencies[1], false);
        secondTextUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        lectureUnitRepository.save(secondTextUnit);
        TextUnit thirdTextUnit = createAndLinkTextUnit(student, competencies[2], false);
        TextUnit fourthTextUnit = createAndLinkTextUnit(student, competencies[3], false);
        fourthTextUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        lectureUnitRepository.save(fourthTextUnit);
        TextUnit fifthTextUnit = createAndLinkTextUnit(student, competencies[4], false);

        var result = request.get("/api/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, textUnit, thirdTextUnit, fifthTextUnit);
    }

    private LearningPathNavigationObjectDTO.LearningObjectType getLearningObjectType(LearningObject learningObject) {
        return switch (learningObject) {
            case LectureUnit ignored -> LearningPathNavigationObjectDTO.LearningObjectType.LECTURE;
            case Exercise ignored -> LearningPathNavigationObjectDTO.LearningObjectType.EXERCISE;
            default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        };
    }

    private void verifyNavigationResult(LearningPathNavigationDTO result, LearningObject expectedPredecessor, LearningObject expectedCurrent, LearningObject expectedSuccessor) {
        verifyNavigationObjectResult(expectedPredecessor, result.predecessorLearningObject());
        verifyNavigationObjectResult(expectedCurrent, result.currentLearningObject());
        verifyNavigationObjectResult(expectedSuccessor, result.successorLearningObject());
    }

    private void verifyNavigationObjectResult(LearningObject expectedObject, LearningPathNavigationObjectDTO actualObject) {
        if (expectedObject == null) {
            assertThat(actualObject).isNull();
        }
        else {
            assertThat(actualObject).isNotNull();
            assertThat(actualObject.type()).isEqualTo(getLearningObjectType(expectedObject));
            assertThat(actualObject.id()).isEqualTo(expectedObject.getId());
        }
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetRelativeLearningPathNavigation() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.get("/api/learning-path/" + learningPath.getId() + "/relative-navigation?learningObjectId=" + textUnit.getId() + "&learningObjectType="
                + LearningPathNavigationObjectDTO.LearningObjectType.LECTURE + "&competencyId=" + competencies[0].getId(), HttpStatus.OK, LearningPathNavigationDTO.class);

        verifyNavigationResult(result, null, textUnit, textExercise);

        assertThat(result.progress()).isEqualTo(learningPath.getProgress());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1337", roles = "USER")
    void testGetLearningPathNavigationForOtherStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.FORBIDDEN, LearningPathNavigationDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationOverview() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.get("/api/learning-path/" + learningPath.getId() + "/navigation-overview", HttpStatus.OK, LearningPathNavigationOverviewDTO.class);

        // TODO: currently learning objects connected to more than one competency are provided twice in the learning path
        // TODO: this is not a problem for the navigation overview as the duplicates are filtered out

        assertThat(result.learningObjects()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1337", roles = "USER")
    void testGetLearningPathNavigationOverviewForOtherStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/learning-path/" + learningPath.getId() + "/navigation-overview", HttpStatus.FORBIDDEN, LearningPathNavigationOverviewDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetCompetencyOrderForLearningPath() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.getList("/api/learning-path/" + learningPath.getId() + "/competencies", HttpStatus.OK, CompetencyNameDTO.class);
        assertThat(result).containsExactlyElementsOf(Arrays.stream(competencies).map(CompetencyNameDTO::of).toList());
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningObjectsForCompetency() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        var result = request.getList("/api/learning-path/" + learningPath.getId() + "/competencies/" + competencies[0].getId() + "/learning-objects", HttpStatus.OK,
                LearningPathNavigationObjectDTO.class);

        assertThat(result).containsExactly(LearningPathNavigationObjectDTO.of(textUnit, true, competencies[0].getId()));

        result = request.getList("/api/learning-path/" + learningPath.getId() + "/competencies/" + competencies[1].getId() + "/learning-objects", HttpStatus.OK,
                LearningPathNavigationObjectDTO.class);

        assertThat(result).containsExactly(LearningPathNavigationObjectDTO.of(textExercise, false, competencies[1].getId()));
    }

    @Test
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testGetLearningObjectsForCompetencyMultipleObjects() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        List<LearningObject> completedLectureUnits = List.of(createAndLinkTextUnit(student, competencies[4], true), createAndLinkTextUnit(student, competencies[4], true));
        List<LearningObject> finishedExercises = List.of(createAndLinkTextExercise(competencies[4], true), createAndLinkTextExercise(competencies[4], true),
                createAndLinkTextExercise(competencies[4], true));

        List<LearningObject> uncompletedLectureUnits = List.of(createAndLinkTextUnit(student, competencies[4], false));
        List<LearningObject> unfinishedExercises = List.of(createAndLinkTextExercise(competencies[4], false), createAndLinkTextExercise(competencies[4], false));

        int a = completedLectureUnits.size();
        int b = completedLectureUnits.size() + finishedExercises.size();
        int c = completedLectureUnits.size() + finishedExercises.size() + uncompletedLectureUnits.size();
        int d = completedLectureUnits.size() + finishedExercises.size() + uncompletedLectureUnits.size() + unfinishedExercises.size();

        var result = request.getList("/api/learning-path/" + learningPath.getId() + "/competencies/" + competencies[4].getId() + "/learning-objects", HttpStatus.OK,
                LearningPathNavigationObjectDTO.class);

        assertThat(result).hasSize(d);
        assertThat(result.subList(0, a)).containsExactlyInAnyOrderElementsOf(
                completedLectureUnits.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, true, competencies[4].getId())).toList());
        assertThat(result.subList(a, b)).containsExactlyInAnyOrderElementsOf(
                finishedExercises.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, true, competencies[4].getId())).toList());
        assertThat(result.subList(b, c)).containsExactlyInAnyOrderElementsOf(
                uncompletedLectureUnits.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, false, competencies[4].getId())).toList());
        assertThat(result.subList(c, d)).containsExactlyInAnyOrderElementsOf(
                unfinishedExercises.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, false, competencies[4].getId())).toList());
    }

    void testGetCompetencyProgressForLearningPath() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.get("/api/learning-path/" + learningPath.getId() + "/competency-progress", HttpStatus.OK, Set.class);
        assertThat(result).hasSize(5);
    }

    private TextExercise createAndLinkTextExercise(Competency competency, boolean withAssessment) {
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, past(1), future(1), future(2));
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        if (withAssessment) {
            var student = userRepository.findOneByLogin(STUDENT_OF_COURSE).orElseThrow();
            studentScoreUtilService.createStudentScore(textExercise, student, 100.0);
        }
        competencyUtilService.linkExerciseToCompetency(competency, textExercise);

        return textExercise;
    }

    private TextUnit createAndLinkTextUnit(User student, Competency competency, boolean completed) {
        TextUnit textUnit = lectureUtilService.createTextUnit();
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(textUnit));
        textUnit = (TextUnit) competencyUtilService.linkLectureUnitToCompetency(competency, textUnit);

        if (completed) {
            lectureUnitService.setLectureUnitCompletion(textUnit, student, true);
        }

        return textUnit;
    }
}
