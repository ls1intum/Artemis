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

public class TeamImportIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    TeamRepository teamRepo;

    @Autowired
    UserRepository userRepo;

    private Course course;

    private Exercise sourceExercise;

    private Exercise destinationExercise;

    private User tutor;

    private String endpointUrl() {
        return "/api/exercises/" + destinationExercise.getId() + "/teams/import-from-exercise/";
    }

    private String importFromExerciseUrl(Exercise exercise, TeamImportStrategyType importStrategyType) {
        return endpointUrl() + exercise.getId() + "?importStrategyType=" + importStrategyType;
    }

    private String importFromExerciseUrl(Exercise exercise) {
        return endpointUrl() + exercise.getId() + "?importStrategyType=" + TeamImportStrategyType.CREATE_ONLY;
    }

    private String importFromSourceExerciseUrl(TeamImportStrategyType importStrategyType) {
        return importFromExerciseUrl(sourceExercise, importStrategyType);
    }

    private String importFromSourceExerciseUrl() {
        return importFromSourceExerciseUrl(TeamImportStrategyType.CREATE_ONLY);
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

        // Select a tutor for the teams
        tutor = userRepo.findOneByLogin("tutor1").orElseThrow();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @ParameterizedTest
    @EnumSource(TeamImportStrategyType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsIntoEmptyExercise(TeamImportStrategyType importStrategyType) throws Exception {
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, 3, tutor);
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(importFromSourceExerciseUrl(importStrategyType), null, Team.class, HttpStatus.OK);
        assertCorrectnessOfImport(sourceTeams, destinationTeamsAfter);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsIntoExerciseWithNoConflictsUsingPurgeExistingStrategy() throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.PURGE_EXISTING;
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, "sourceTeam", 2, tutor);
        database.addTeamsForExercise(destinationExercise, 4, tutor);
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(importFromSourceExerciseUrl(strategyType), null, Team.class, HttpStatus.OK);
        // imported source teams = destination teams after
        assertCorrectnessOfImport(sourceTeams, destinationTeamsAfter);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsIntoExerciseWithNoConflictsUsingCreateOnlyStrategy() throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, "sourceTeam", 3, tutor);
        List<Team> destinationTeamsBefore = database.addTeamsForExercise(destinationExercise, 1, tutor);
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(importFromSourceExerciseUrl(strategyType), null, Team.class, HttpStatus.OK);
        // destination teams before + source teams = destination teams after
        assertCorrectnessOfImport(addLists(destinationTeamsBefore, sourceTeams), destinationTeamsAfter);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsIntoExerciseWithConflictsUsingPurgeExistingStrategy() throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.PURGE_EXISTING;
        List<Team> sourceTeams = database.addTeamsForExercise(sourceExercise, "sameShortName", "other", 3, tutor);
        database.addTeamsForExercise(destinationExercise, "sameShortName", 2, tutor);
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(importFromSourceExerciseUrl(strategyType), null, Team.class, HttpStatus.OK);
        // imported source teams = destination teams after
        assertCorrectnessOfImport(sourceTeams, destinationTeamsAfter);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsIntoExerciseWithTeamShortNameConflictsUsingCreateOnlyStrategy() throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> sourceTeamsWithoutConflict = database.addTeamsForExercise(sourceExercise, "sourceTeam", 1, tutor);
        List<Team> sourceTeamsWithTeamShortNameConflict = database.addTeamsForExercise(sourceExercise, "sameShortName", "other", 2, tutor);
        List<Team> destinationTeamsBefore = database.addTeamsForExercise(destinationExercise, "sameShortName", 3, tutor);
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(importFromSourceExerciseUrl(strategyType), null, Team.class, HttpStatus.OK);
        // destination teams before + conflict-free source teams = destination teams after
        assertCorrectnessOfImport(addLists(destinationTeamsBefore, sourceTeamsWithoutConflict), destinationTeamsAfter);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeamsIntoExerciseWithStudentConflictsUsingCreateOnlyStrategy() throws Exception {
        TeamImportStrategyType strategyType = TeamImportStrategyType.CREATE_ONLY;
        List<Team> sourceTeamsWithoutConflict = database.addTeamsForExercise(sourceExercise, "sourceTeamOther", 1, tutor);
        List<Team> sourceTeamsWithStudentConflict = database.addTeamsForExercise(sourceExercise, "sourceTeam", 3, tutor);
        List<Team> destinationTeamsBefore = teamRepo.saveAll(
                sourceTeamsWithStudentConflict.stream().map(team -> team.exercise(destinationExercise).shortName("other" + team.getShortName())).collect(Collectors.toList()));
        List<Team> destinationTeamsAfter = request.putWithResponseBodyList(importFromSourceExerciseUrl(strategyType), null, Team.class, HttpStatus.OK);
        // destination teams before + conflict-free source teams = destination teams after
        assertCorrectnessOfImport(addLists(destinationTeamsBefore, sourceTeamsWithoutConflict), destinationTeamsAfter);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeams_BadRequests() throws Exception {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testImportTeams_Forbidden_AsTutor() throws Exception {
        request.put(importFromSourceExerciseUrl(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImportTeams_Forbidden_AsInstructorOfOtherCourse() throws Exception {
        // If the instructor is not part of the correct course instructor group anymore, he should not be able to import teams
        course.setInstructorGroupName("Different group name");
        courseRepo.save(course);

        request.put(importFromSourceExerciseUrl(), null, HttpStatus.FORBIDDEN);
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
}
