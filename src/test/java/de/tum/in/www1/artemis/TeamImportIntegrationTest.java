package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.TeamImportStrategyType;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TeamImportIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    TeamRepository teamRepo;

    @Autowired
    UserRepository userRepo;

    private enum ImportType {
        FROM_EXERCISE, FROM_FILE
    }

    private Course course;

    private Exercise sourceExercise;

    private Exercise destinationExercise;

    private List<Team> importedTeams;

    private List<Team> importedTeamsWithOnlyRegistrationNumbers;

    private User tutor;

    private String fromExerciseEndpointUrl() {
        return "/api/exercises/" + destinationExercise.getId() + "/teams/import-from-exercise/";
    }

    private String importFromExerciseUrl(Exercise exercise, TeamImportStrategyType importStrategyType) {
        return fromExerciseEndpointUrl() + exercise.getId() + "?importStrategyType=" + importStrategyType;
    }

    private String importFromExerciseUrl(Exercise exercise) {
        return fromExerciseEndpointUrl() + exercise.getId() + "?importStrategyType=" + TeamImportStrategyType.CREATE_ONLY;
    }

    private String importFromSourceExerciseUrl(TeamImportStrategyType importStrategyType) {
        return importFromExerciseUrl(sourceExercise, importStrategyType);
    }

    private String importFromSourceExerciseUrl() {
        return importFromSourceExerciseUrl(TeamImportStrategyType.CREATE_ONLY);
    }

    private String withRegistrationNumberEndpointUrl() {
        return "/api/exercises/" + destinationExercise.getId() + "/teams/import-with-registration-numbers";
    }

    private String importWithRegistrationNumberUrl(TeamImportStrategyType importStrategyType) {
        return withRegistrationNumberEndpointUrl() + "?importStrategyType=" + importStrategyType;
    }

    private String importWithRegistrationNumberUrl() {
        return withRegistrationNumberEndpointUrl() + "?importStrategyType=" + TeamImportStrategyType.CREATE_ONLY;
    }

    @BeforeEach
    public void initTestCase() {
        database.addUsers(0, 5, 1);
        course = database.addCourseWithModelingAndTextExercise();

        // Make both source and destination exercise team exercises
        sourceExercise = database.findModelingExerciseWithTitle(course.getExercises(), "Modeling");
        sourceExercise = exerciseRepo.save(sourceExercise.mode(ExerciseMode.TEAM));
        destinationExercise = database.findTextExerciseWithTitle(course.getExercises(), "Text");
        destinationExercise = exerciseRepo.save(destinationExercise.mode(ExerciseMode.TEAM));
        importedTeams = ModelFactory.generateTeamsForExercise(destinationExercise, "import", "student", 3, null, "instructor1");
        userRepo.saveAll(importedTeams.stream().map(Team::getStudents).flatMap(Collection::stream).collect(Collectors.toList()));
        importedTeamsWithOnlyRegistrationNumbers = getTeamsIntoRegistrationNumberOnlyTeams(importedTeams);
        // Select a tutor for the teams
        tutor = userRepo.findOneByLogin("tutor1").orElseThrow();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    private void testImportTeamsIntoExercise(ImportType type, TeamImportStrategyType importStrategyType, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType importStrategy = TeamImportStrategyType.CREATE_ONLY;
        if (importStrategyType != null) {
            importStrategy = importStrategyType;
        }
        String url = importFromSourceExerciseUrl(importStrategy);
        if (type == ImportType.FROM_FILE) {
            url = importWithRegistrationNumberUrl(importStrategy);
        }
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(url, body, Team.class, HttpStatus.OK);
        assertCorrectnessOfImport(addedTeams, destinationTeamsAfter);
    }

    @ParameterizedTest
    @EnumSource(TeamImportStrategyType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseIntoEmptyExercise(TeamImportStrategyType importStrategyType) throws Exception {
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, 3, tutor);
        testImportTeamsIntoExercise(ImportType.FROM_EXERCISE, importStrategyType, null, sourceTeams);
    }

    @ParameterizedTest
    @EnumSource(TeamImportStrategyType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersIntoEmptyExercise(TeamImportStrategyType importStrategyType) throws Exception {
        testImportTeamsIntoExercise(ImportType.FROM_FILE, importStrategyType, importedTeamsWithOnlyRegistrationNumbers, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithNoConflictsUsingPurgeExistingStrategy(ImportType type, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.PURGE_EXISTING;
        database.addTeamsForExercise(destinationExercise, 4, tutor);
        testImportTeamsIntoExercise(type, strategyType, body, addedTeams);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseIntoExerciseWithNoConflictsUsingPurgeExistingStrategy() throws Exception {
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, "sourceTeam", 2, tutor);
        testImportTeamsIntoExerciseWithNoConflictsUsingPurgeExistingStrategy(ImportType.FROM_EXERCISE, null, sourceTeams);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersWithNoConflictsUsingPurgeExistingStrategy() throws Exception {
        testImportTeamsIntoExerciseWithNoConflictsUsingPurgeExistingStrategy(ImportType.FROM_FILE, importedTeamsWithOnlyRegistrationNumbers, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithNoConflictsUsingCreateOnlyStrategy(ImportType type, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> destinationTeamsBefore = database.addTeamsForExercise(destinationExercise, 1, tutor);
        // destination teams before + source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addLists(destinationTeamsBefore, addedTeams));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseIntoExerciseWithNoConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, "sourceTeam", 3, tutor);
        // destination teams before + source teams = destination teams after
        testImportTeamsIntoExerciseWithNoConflictsUsingCreateOnlyStrategy(ImportType.FROM_EXERCISE, null, sourceTeams);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersIntoExerciseWithNoConflictsUsingCreateOnlyStrategy() throws Exception {
        // destination teams before + imported teams = destination teams after
        testImportTeamsIntoExerciseWithNoConflictsUsingCreateOnlyStrategy(ImportType.FROM_FILE, importedTeamsWithOnlyRegistrationNumbers, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithConflictsUsingPurgeExistingStrategy(ImportType type, List<Team> body, List<Team> addedTeams) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.PURGE_EXISTING;
        database.addTeamsForExercise(destinationExercise, "sameShortName", 2, tutor);
        // imported source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addedTeams);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseIntoExerciseWithConflictsUsingPurgeExistingStrategy() throws Exception {
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, "sameShortName", "other", 3, tutor);
        testImportTeamsIntoExerciseWithConflictsUsingPurgeExistingStrategy(ImportType.FROM_EXERCISE, null, sourceTeams);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersIntoExerciseWithConflictsUsingPurgeExistingStrategy() throws Exception {
        importedTeams = ModelFactory.generateTeamsForExercise(destinationExercise, "sameShortName", "other", 3, null, "instructor1");
        userRepo.saveAll(importedTeams.stream().map(Team::getStudents).flatMap(Collection::stream).collect(Collectors.toList()));
        importedTeamsWithOnlyRegistrationNumbers = getTeamsIntoRegistrationNumberOnlyTeams(importedTeams);
        testImportTeamsIntoExerciseWithConflictsUsingPurgeExistingStrategy(ImportType.FROM_FILE, importedTeamsWithOnlyRegistrationNumbers, importedTeams);
    }

    private void testImportTeamsIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy(ImportType type, List<Team> body, List<Team> teamsWithoutConflict) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> destinationTeamsBefore = database.addTeamsForExercise(destinationExercise, "sameShortName", 3, tutor);
        // destination teams before + conflict-free source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addLists(destinationTeamsBefore, teamsWithoutConflict));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> sourceTeamsWithoutConflict = database.addTeamsForExercise(sourceExercise, "sourceTeam", 1, tutor);
        List<Team> sourceTeamsWithTeamShortNameConflict = database.addTeamsForExercise(sourceExercise, "sameShortName", "other", 2, tutor);
        testImportTeamsIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy(ImportType.FROM_EXERCISE, null, sourceTeamsWithoutConflict);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> importedTeamsWithConflict = ModelFactory.generateTeamsForExercise(destinationExercise, "sameShortName", "other", 3, null, "instructor1");
        userRepo.saveAll(importedTeamsWithConflict.stream().map(Team::getStudents).flatMap(Collection::stream).collect(Collectors.toList()));
        List<Team> importedTeamsWithOnlyRegistrationNumbersWithConflict = getTeamsIntoRegistrationNumberOnlyTeams(importedTeamsWithConflict);
        testImportTeamsIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy(ImportType.FROM_FILE,
                addLists(importedTeamsWithOnlyRegistrationNumbersWithConflict, importedTeamsWithOnlyRegistrationNumbers), importedTeams);
    }

    private void testImportTeamsIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy(ImportType type, List<Team> body, List<Team> teamsWithoutConflict,
            List<Team> teamsWithConflicts) throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> destinationTeamsBefore = teamRepo
                .saveAll(teamsWithConflicts.stream().map(team -> team.exercise(destinationExercise).shortName("other" + team.getShortName())).collect(Collectors.toList()));
        // destination teams before + conflict-free source teams = destination teams after
        testImportTeamsIntoExercise(type, strategyType, body, addLists(destinationTeamsBefore, teamsWithoutConflict));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> sourceTeamsWithoutConflict = database.addTeamsForExercise(sourceExercise, "sourceTeamOther", 1, tutor);
        List<Team> sourceTeamsWithStudentConflict = database.addTeamsForExercise(sourceExercise, "sourceTeam", 3, tutor);
        // destination teams before + conflict-free source teams = destination teams after
        testImportTeamsIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy(ImportType.FROM_EXERCISE, null, sourceTeamsWithoutConflict, sourceTeamsWithStudentConflict);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy() throws Exception {
        List<Team> importedTeamsWithStudentConflict = ModelFactory.generateTeamsForExercise(destinationExercise, "withConflict", "import", 3, null, "instructor1");
        userRepo.saveAll(importedTeamsWithStudentConflict.stream().map(Team::getStudents).flatMap(Collection::stream).collect(Collectors.toList()));
        List<Team> importedTeamsWithOnlyRegistrationNumbersWithStudentConflict = getTeamsIntoRegistrationNumberOnlyTeams(importedTeamsWithStudentConflict);
        // destination teams before + conflict-free imported teams = destination teams after
        testImportTeamsIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy(ImportType.FROM_FILE,
                addLists(importedTeamsWithOnlyRegistrationNumbersWithStudentConflict, importedTeamsWithOnlyRegistrationNumbers), importedTeams, importedTeamsWithStudentConflict);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseBadRequests() throws Exception {
        // Specifying the destination exercise to also be the source exercise should fail
        request.put(importFromExerciseUrl(destinationExercise), null, HttpStatus.BAD_REQUEST);

        // If the destination exercise is not a team exercise, the request should fail
        exerciseRepo.save(destinationExercise.mode(ExerciseMode.INDIVIDUAL));
        request.put(importFromSourceExerciseUrl(), null, HttpStatus.BAD_REQUEST);
        exerciseRepo.save(destinationExercise.mode(ExerciseMode.TEAM));

        // If the source exercise is not a team exercise, the request should fail
        exerciseRepo.save(sourceExercise.mode(ExerciseMode.INDIVIDUAL));
        request.put(importFromSourceExerciseUrl(), null, HttpStatus.BAD_REQUEST);
        exerciseRepo.save(sourceExercise.mode(ExerciseMode.TEAM));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersBadRequests() throws Exception {
        // If the destination exercise is not a team exercise, the request should fail
        exerciseRepo.save(destinationExercise.mode(ExerciseMode.INDIVIDUAL));
        request.put(importWithRegistrationNumberUrl(), importedTeamsWithOnlyRegistrationNumbers, HttpStatus.BAD_REQUEST);
        exerciseRepo.save(destinationExercise.mode(ExerciseMode.TEAM));

        // If user with given registration number does not exist, the request should fail
        List<Team> teams = new ArrayList<>();
        Team team = ModelFactory.generateTeamForExercise(destinationExercise, "failTeam", "fail", 1, null);
        // If students not added with user repo then they do not exist so it should fail
        teams.add(team);
        request.put(importWithRegistrationNumberUrl(), getTeamsIntoRegistrationNumberOnlyTeams(teams), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testImportTeamsFromExerciseForbiddenAsTutor() throws Exception {
        request.put(importFromSourceExerciseUrl(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testImportTeamsWithRegistrationNumbersForbiddenAsTutor() throws Exception {
        request.put(importWithRegistrationNumberUrl(), importedTeamsWithOnlyRegistrationNumbers, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsFromExerciseForbiddenAsInstructorOfOtherCourse() throws Exception {
        // If the instructor is not part of the correct course instructor group anymore, he should not be able to import teams
        course.setInstructorGroupName("Different group name");
        courseRepo.save(course);

        request.put(importFromSourceExerciseUrl(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsWithRegistrationNumbersForbiddenAsInstructorOfOtherCourse() throws Exception {
        // If the instructor is not part of the correct course instructor group anymore, he should not be able to import teams
        course.setInstructorGroupName("Different group name");
        courseRepo.save(course);

        request.put(importWithRegistrationNumberUrl(), importedTeamsWithOnlyRegistrationNumbers, HttpStatus.FORBIDDEN);
    }

    /**
     * Verifies that the teams including their students were correctly imported in the destination exercise according to the expectations
     *
     * @param expectedTeamsAfterImport List of teams that are expected to be in the destination exercise now after the import
     * @param actualTeamsAfterImport   List of teams that are actually in the destination exercise after the import according to response
     */
    private void assertCorrectnessOfImport(List<Team> expectedTeamsAfterImport, List<Team> actualTeamsAfterImport) {
        List<Team> destinationTeamsInDatabase = teamRepo.findAllByExerciseId(destinationExercise.getId());
        assertThat(actualTeamsAfterImport).as("Imported teams were persisted into destination exercise.").isEqualTo(destinationTeamsInDatabase);

        assertThat(actualTeamsAfterImport).as("Teams were correctly imported.").usingRecursiveComparison().ignoringFields("id", "exercise", "createdDate", "lastModifiedDate")
                .usingOverriddenEquals().ignoringOverriddenEqualsForTypes(Team.class).isEqualTo(expectedTeamsAfterImport);
    }

    static <T> List<T> addLists(List<T> a, List<T> b) {
        return Stream.of(a, b).flatMap(Collection::stream).collect(Collectors.toList());
    }

    static List<Team> getTeamsIntoRegistrationNumberOnlyTeams(List<Team> teams) {
        return teams.stream().map(team -> {
            Team newTeam = new Team();
            newTeam.setName(team.getName());
            newTeam.setShortName(team.getShortName());
            newTeam.setOwner(team.getOwner());
            List<User> newStudents = team.getStudents().stream().map(student -> {
                User newStudent = new User();
                newStudent.setFirstName(student.getFirstName());
                newStudent.setLastName(student.getLastName());
                newStudent.setVisibleRegistrationNumber(student.getRegistrationNumber());
                return newStudent;
            }).collect(Collectors.toList());
            newTeam.setStudents(new HashSet<>(newStudents));
            return newTeam;
        }).collect(Collectors.toList());
    }
}
