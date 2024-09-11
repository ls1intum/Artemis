package de.tum.cit.aet.artemis.exercise.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;

class AuxiliaryRepositoryServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_INVALID_LENGTH_STRING = "a".repeat(AuxiliaryRepository.MAX_NAME_LENGTH + 1);

    private static final String TEST_INVALID_DESCRIPTION_LENGTH_STRING = "a".repeat(AuxiliaryRepository.MAX_DESCRIPTION_LENGTH + 1);

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private AuxiliaryRepositoryService auxiliaryRepositoryService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private static ProgrammingExercise programmingExerciseBeforeUpdate;

    private static ProgrammingExercise updatedProgrammingExercise;

    @BeforeEach
    void setUp() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseBeforeUpdate = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExerciseBeforeUpdate.setReleaseDate(null);
        programmingExerciseBeforeUpdate.setAuxiliaryRepositories(new ArrayList<>());
        programmingExerciseRepository.save(programmingExerciseBeforeUpdate);

        updatedProgrammingExercise = programmingExerciseRepository.findById(programmingExerciseBeforeUpdate.getId()).orElseThrow();
        updatedProgrammingExercise.setAuxiliaryRepositories(new ArrayList<>());
        auxiliaryRepositoryRepository.deleteAll();
    }

    @Test
    void shouldReturnTrueIfAuxiliaryRepositoryOfExercise() {
        auxiliaryRepositoryRepository.save(createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, null));

        assertThat(auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise("test", programmingExerciseBeforeUpdate)).isTrue();
    }

    @Test
    void shouldReturnFalseIfNotAuxiliaryRepositoryOfExercise() {
        auxiliaryRepositoryRepository.save(createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, null));

        assertThat(auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise("asd", programmingExerciseBeforeUpdate)).isFalse();
    }

    @Test
    void shouldAddAuxiliaryRepositoriesSuccessfully() {
        AuxiliaryRepository auxiliaryRepository = createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, null);
        AuxiliaryRepository auxiliaryRepository2 = createAuxiliaryRepository("test2", "test2", "test2", programmingExerciseBeforeUpdate, null);
        auxiliaryRepositoryRepository.save(auxiliaryRepository);
        programmingExerciseBeforeUpdate.setAuxiliaryRepositories(List.of(auxiliaryRepository));

        auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, List.of(auxiliaryRepository2));

        assertThat(programmingExerciseBeforeUpdate.getAuxiliaryRepositories()).containsExactly(auxiliaryRepository, auxiliaryRepository2);
    }

    @Test
    void shouldThrowErrorWhenNotNullId() {
        List<AuxiliaryRepository> newAuxiliaryRepos = List.of(createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, 1L));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("Auxiliary repositories must not have an id.");
    }

    @Test
    void shouldThrowErrorWhenEmptyName() {
        List<AuxiliaryRepository> newAuxiliaryRepos = List.of(createAuxiliaryRepository("", "test", "test", programmingExerciseBeforeUpdate, null));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("Cannot set empty name for auxiliary repositories!");

    }

    @Test
    void shouldThrowErrorWhenNameLongerThan100Characters() {
        List<AuxiliaryRepository> newAuxiliaryRepos = List.of(createAuxiliaryRepository(TEST_INVALID_LENGTH_STRING, "test", "test", programmingExerciseBeforeUpdate, null));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("The name of an auxiliary repository must not be longer than 100 characters!");
    }

    @Test
    void shouldThrowErrorWhenDuplicateName() {
        AuxiliaryRepository auxiliaryRepository = createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, null);
        List<AuxiliaryRepository> newAuxiliaryRepos = List.of(createAuxiliaryRepository("test", "test2", "test2", programmingExerciseBeforeUpdate, null));
        auxiliaryRepositoryRepository.save(auxiliaryRepository);
        programmingExerciseBeforeUpdate.setAuxiliaryRepositories(List.of(auxiliaryRepository));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("The name 'test' is not allowed for auxiliary repositories!");
    }

    @Test
    void shouldThrowErrorWhenRestrictedName() {
        List<AuxiliaryRepository> newAuxiliaryRepos = List.of(createAuxiliaryRepository("tests", "test", "test", programmingExerciseBeforeUpdate, null));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("The name 'tests' is not allowed for auxiliary repositories!");
    }

    @Test
    void shouldThrowErrorWhenInvalidDirectory() {
        List<AuxiliaryRepository> newAuxiliaryRepos = List.of(createAuxiliaryRepository("test", "123vg-q@", "test", "test", programmingExerciseBeforeUpdate, null));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("The checkout directory '123vg-q@' is invalid!");
    }

    @Test
    void shouldSetDirectoryToNullWhenEmptyDirectory() {
        AuxiliaryRepository repo = createAuxiliaryRepository("test", "", "test", "test", programmingExerciseBeforeUpdate, null);

        auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, List.of(repo));
        assertThat(repo.getCheckoutDirectory()).isNull();
    }

    @Test
    void shouldThrowErrorWhenInvalidDirectoryLength() {
        List<AuxiliaryRepository> newAuxiliaryRepos = List.of(createAuxiliaryRepository("test", TEST_INVALID_LENGTH_STRING, "test", programmingExerciseBeforeUpdate, null));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("The checkout directory path '" + TEST_INVALID_LENGTH_STRING + "' is too long!");
    }

    @Test
    void shouldThrowErrorWhenInvalidDescriptionLength() {
        List<AuxiliaryRepository> newAuxiliaryRepos = List
                .of(createAuxiliaryRepository("test", "test", TEST_INVALID_DESCRIPTION_LENGTH_STRING, "test", programmingExerciseBeforeUpdate, null));

        assertThatThrownBy(() -> auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExerciseBeforeUpdate, newAuxiliaryRepos))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("The provided description is too long!");
    }

    @Test
    void shouldChangeRepositorySuccessfully() {
        AuxiliaryRepository auxiliaryRepository = createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, null);
        AuxiliaryRepository auxiliaryRepository2 = createAuxiliaryRepository("test2", "test2", "test2", updatedProgrammingExercise, null);
        auxiliaryRepositoryRepository.save(auxiliaryRepository);
        programmingExerciseBeforeUpdate.setAuxiliaryRepositories(List.of(auxiliaryRepository));
        updatedProgrammingExercise.setAuxiliaryRepositories(List.of(auxiliaryRepository2));

        // Check if the repository was created
        assertThat(auxiliaryRepositoryRepository.findAll()).containsExactly(auxiliaryRepository);

        auxiliaryRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        // Check if the repository was updated, deletes the old repository
        assertThat(auxiliaryRepositoryRepository.findAll()).isEmpty();
    }

    @Test
    void shouldThrowErrorWhenEditingExistingRepositoryNotInDatabase() {
        AuxiliaryRepository auxiliaryRepository = createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, null);
        AuxiliaryRepository auxiliaryRepository2 = createAuxiliaryRepository("test2", "test2", "test2", updatedProgrammingExercise, 2L);
        auxiliaryRepositoryRepository.save(auxiliaryRepository);
        programmingExerciseBeforeUpdate.setAuxiliaryRepositories(List.of(auxiliaryRepository));
        updatedProgrammingExercise.setAuxiliaryRepositories(List.of(auxiliaryRepository2));

        assertThatThrownBy(() -> auxiliaryRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(programmingExerciseBeforeUpdate, updatedProgrammingExercise))
                .isInstanceOf(IllegalStateException.class).hasMessage("Edited an existing repository that is not in the database!");
    }

    @Test
    void shouldThrowErrorWhenInvalidRepository() {
        AuxiliaryRepository auxiliaryRepository = createAuxiliaryRepository("test", "test", "test", programmingExerciseBeforeUpdate, null);
        AuxiliaryRepository auxiliaryRepository2 = createAuxiliaryRepository("", "test", "test", updatedProgrammingExercise, null);
        auxiliaryRepositoryRepository.save(auxiliaryRepository);
        auxiliaryRepository2.setId(auxiliaryRepository.getId());
        programmingExerciseBeforeUpdate.setAuxiliaryRepositories(List.of(auxiliaryRepository));
        updatedProgrammingExercise.setAuxiliaryRepositories(List.of(auxiliaryRepository2));

        assertThatThrownBy(() -> auxiliaryRepositoryService.handleAuxiliaryRepositoriesWhenUpdatingExercises(programmingExerciseBeforeUpdate, updatedProgrammingExercise))
                .isInstanceOf(BadRequestAlertException.class).hasMessage("Cannot set empty name for auxiliary repositories!");
    }

    private AuxiliaryRepository createAuxiliaryRepository(String name, String checkoutDirectory, String repositoryUri, ProgrammingExercise exercise, Long id) {
        return createAuxiliaryRepository(name, checkoutDirectory, "test", repositoryUri, exercise, id);
    }

    private static AuxiliaryRepository createAuxiliaryRepository(String name, String checkoutDirectory, String description, String repositoryUri, ProgrammingExercise exercise,
            Long id) {
        AuxiliaryRepository auxiliaryRepository = new AuxiliaryRepository();
        auxiliaryRepository.setName(name);
        auxiliaryRepository.setDescription(description);
        auxiliaryRepository.setCheckoutDirectory(checkoutDirectory);
        auxiliaryRepository.setRepositoryUri(repositoryUri);
        auxiliaryRepository.setExercise(exercise);
        if (id != null) {
            auxiliaryRepository.setId(id);
        }
        return auxiliaryRepository;
    }

}
