package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.programming.ProgrammingExerciseResourceErrorKeys;

@Profile(PROFILE_CORE)
@Service
public class AuxiliaryRepositoryService {

    private static final String AUX_REPO_ENTITY_NAME = "programmingExercise";

    private static final Pattern ALLOWED_CHECKOUT_DIRECTORY = Pattern.compile("[\\w-]+(/[\\w-]+)*$");

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    public AuxiliaryRepositoryService(AuxiliaryRepositoryRepository auxiliaryRepositoryRepository) {
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
    }

    /**
     * Validates and adds all new Auxiliary Repositories that are added to the given exercise
     *
     * @param programmingExercise      The programming exercise where the auxiliary repositories are added
     * @param newAuxiliaryRepositories The newly added auxiliary repositories
     */
    public void validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(ProgrammingExercise programmingExercise, List<AuxiliaryRepository> newAuxiliaryRepositories) {
        List<AuxiliaryRepository> auxiliaryRepositories = new ArrayList<>(Objects
                .requireNonNullElse(programmingExercise.getAuxiliaryRepositories(), new ArrayList<AuxiliaryRepository>()).stream().filter(repo -> repo.getId() != null).toList());
        for (AuxiliaryRepository repo : newAuxiliaryRepositories) {
            validateAuxiliaryRepository(programmingExercise.getProgrammingLanguage(), repo, auxiliaryRepositories, true);
            auxiliaryRepositories.add(repo);
        }
        programmingExercise.setAuxiliaryRepositories(new ArrayList<>());
        auxiliaryRepositories.forEach(programmingExercise::addAuxiliaryRepository);
    }

    /**
     * Handles an update of a programming exercises where some of the auxiliary repositories are added, changed or deleted
     *
     * @param programmingExercise The programming exercise before the update
     * @param updatedExercise     The programming exercise after the update
     */
    public void handleAuxiliaryRepositoriesWhenUpdatingExercises(ProgrammingExercise programmingExercise, ProgrammingExercise updatedExercise) {
        // Get all new (ID is still null) and changed (some string value is changed) auxiliary repositories
        List<AuxiliaryRepository> newOrEditedAuxiliaryRepositories = new ArrayList<>(updatedExercise.getAuxiliaryRepositories().stream().filter(repo -> {
            if (repo.getId() == null) {
                return true;
            }
            AuxiliaryRepository auxiliaryRepositoryBeforeUpdate = auxiliaryRepositoryRepository.findById(repo.getId())
                    .orElseThrow(() -> new IllegalStateException("Edited an existing repository that is not in the database!"));
            return !repo.containsEqualStringValues(auxiliaryRepositoryBeforeUpdate);
        }).toList());
        validateAndUpdateExistingAuxiliaryRepositoriesOfProgrammingExercise(programmingExercise, newOrEditedAuxiliaryRepositories, updatedExercise);

        List<AuxiliaryRepository> removedAuxiliaryRepositories = programmingExercise.getAuxiliaryRepositories().stream()
                .filter(repo -> updatedExercise.getAuxiliaryRepositories().stream().noneMatch(updatedRepo -> repo.getId().equals(updatedRepo.getId()))).toList();

        auxiliaryRepositoryRepository.deleteAll(removedAuxiliaryRepositories);
    }

    private void validateAndUpdateExistingAuxiliaryRepositoriesOfProgrammingExercise(ProgrammingExercise programmingExercise,
            List<AuxiliaryRepository> updatedAuxiliaryRepositories, ProgrammingExercise updatedExercise) {
        // Get all repositories that are unchanged and are still present in the updated exercise
        List<AuxiliaryRepository> auxiliaryRepositories = new ArrayList<>(
                Objects.requireNonNullElse(programmingExercise.getAuxiliaryRepositories(), new ArrayList<AuxiliaryRepository>()).stream()
                        .filter(existingRepo -> updatedAuxiliaryRepositories.stream().noneMatch((updatedRepo -> existingRepo.getId().equals(updatedRepo.getId())))
                                && updatedExercise.getAuxiliaryRepositories().stream().anyMatch(updatedRepo -> existingRepo.getId().equals(updatedRepo.getId())))
                        .toList());

        for (AuxiliaryRepository repo : updatedAuxiliaryRepositories) {
            validateAuxiliaryRepository(programmingExercise.getProgrammingLanguage(), repo, auxiliaryRepositories,
                    programmingExercise.getAuxiliaryRepositories().stream().noneMatch(existingRepo -> existingRepo.getId().equals(repo.getId())));
            auxiliaryRepositories.add(repo);
        }
        updatedExercise.setAuxiliaryRepositories(new ArrayList<>());
        auxiliaryRepositories.forEach(updatedExercise::addAuxiliaryRepository);
    }

    private void validateAuxiliaryRepositoryId(AuxiliaryRepository auxiliaryRepository) {
        if (auxiliaryRepository.getId() != null) {
            throw new BadRequestAlertException("Auxiliary repositories must not have an id.", AUX_REPO_ENTITY_NAME,
                    ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_ID);
        }
    }

    private void validateAuxiliaryRepositoryNameExists(AuxiliaryRepository auxiliaryRepository) {
        if (auxiliaryRepository.getName() == null || auxiliaryRepository.getName().isEmpty()) {
            throw new BadRequestAlertException("Cannot set empty name for auxiliary repositories!", AUX_REPO_ENTITY_NAME,
                    ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_NAME);
        }
        auxiliaryRepository.setName(auxiliaryRepository.getName().toLowerCase());
    }

    private void validateAuxiliaryRepositoryNameLength(AuxiliaryRepository auxiliaryRepository) {
        if (auxiliaryRepository.getName().length() > AuxiliaryRepository.MAX_NAME_LENGTH) {
            throw new BadRequestAlertException("The name of an auxiliary repository must not be longer than 100 characters!", AUX_REPO_ENTITY_NAME,
                    ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_NAME);
        }
    }

    private void validateAuxiliaryRepositoryNameDuplication(AuxiliaryRepository auxiliaryRepository, List<AuxiliaryRepository> otherRepositories) {
        for (AuxiliaryRepository existingRepository : otherRepositories) {
            if (existingRepository.getName().equals(auxiliaryRepository.getName())) {
                throw new BadRequestAlertException("The name '" + auxiliaryRepository.getName() + "' is not allowed for auxiliary repositories!", AUX_REPO_ENTITY_NAME,
                        ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_NAME);
            }
        }
    }

    private void validateAuxiliaryRepositoryNameRestricted(AuxiliaryRepository auxiliaryRepository) {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            String repositoryName = repositoryType.getName();
            if (auxiliaryRepository.getName().equals(repositoryName)) {
                throw new BadRequestAlertException("The name '" + repositoryName + "' is not allowed for auxiliary repositories!", AUX_REPO_ENTITY_NAME,
                        ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_NAME);
            }
        }
    }

    private void validateAuxiliaryRepositoryCheckoutDirectoryValid(ProgrammingLanguage programmingLanguage, AuxiliaryRepository auxiliaryRepository) {
        Matcher ciCheckoutDirectoryMatcher = ALLOWED_CHECKOUT_DIRECTORY.matcher(auxiliaryRepository.getCheckoutDirectory());
        String assignmentCheckoutPath = ContinuousIntegrationService.RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);
        if (!ciCheckoutDirectoryMatcher.matches() || auxiliaryRepository.getCheckoutDirectory().equals(assignmentCheckoutPath)) {
            throw new BadRequestAlertException("The checkout directory '" + auxiliaryRepository.getCheckoutDirectory() + "' is invalid!", AUX_REPO_ENTITY_NAME,
                    ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_CHECKOUT_DIRECTORY);
        }
    }

    private void validateAuxiliaryRepositoryCheckoutDirectoryLength(AuxiliaryRepository auxiliaryRepository) {
        if (auxiliaryRepository.getCheckoutDirectory().length() > AuxiliaryRepository.MAX_CHECKOUT_DIRECTORY_LENGTH) {
            throw new BadRequestAlertException("The checkout directory path '" + auxiliaryRepository.getCheckoutDirectory() + "' is too long!", AUX_REPO_ENTITY_NAME,
                    ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_CHECKOUT_DIRECTORY);
        }
    }

    private void validateAuxiliaryRepositoryCheckoutDirectoryDuplication(AuxiliaryRepository auxiliaryRepository, List<AuxiliaryRepository> otherRepositories) {
        for (AuxiliaryRepository repo : otherRepositories) {
            if (repo.getCheckoutDirectory() != null && repo.getCheckoutDirectory().equals(auxiliaryRepository.getCheckoutDirectory())) {
                throw new BadRequestAlertException("The checkout directory path is already defined for another additional repository!", AUX_REPO_ENTITY_NAME,
                        ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_CHECKOUT_DIRECTORY);
            }
        }
    }

    private void validateAuxiliaryRepositoryDescriptionLength(AuxiliaryRepository auxiliaryRepository) {
        if (auxiliaryRepository.getDescription() != null && auxiliaryRepository.getDescription().length() > AuxiliaryRepository.MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestAlertException("The provided description is too long!", AUX_REPO_ENTITY_NAME,
                    ProgrammingExerciseResourceErrorKeys.INVALID_AUXILIARY_REPOSITORY_DESCRIPTION, Map.of("maxLength", AuxiliaryRepository.MAX_DESCRIPTION_LENGTH));
        }
    }

    private void validateAuxiliaryRepository(ProgrammingLanguage programmingLanguage, AuxiliaryRepository auxiliaryRepository, List<AuxiliaryRepository> otherRepositories,
            boolean checkID) {
        if (checkID) {
            // ID of the auxiliary repository must not be set, because the id is set by the database.
            validateAuxiliaryRepositoryId(auxiliaryRepository);
        }

        // We want to force the user to set a name of the auxiliary repository, otherwise we
        // cannot determine which name we should use for setting up the repo on the VCS.
        validateAuxiliaryRepositoryNameExists(auxiliaryRepository);

        // The name must not be longer than 100 characters, since the database column is
        // limited to 100 characters.
        validateAuxiliaryRepositoryNameLength(auxiliaryRepository);

        // We want to avoid using the same auxiliary repository name multiple times
        validateAuxiliaryRepositoryNameDuplication(auxiliaryRepository, otherRepositories);

        // The name must not match any of the names of the already present repositories, otherwise
        // we get an undefined state.
        // Currently, the names "exercise", "solution", "tests" and "auxiliary" as specified in RepositoryType are restricted.
        validateAuxiliaryRepositoryNameRestricted(auxiliaryRepository);

        if (auxiliaryRepository.getCheckoutDirectory() != null) {

            if (auxiliaryRepository.getCheckoutDirectory().isBlank()) {
                auxiliaryRepository.setCheckoutDirectory(null);
            }
            else {

                // We want to make sure, that the checkout directory path is valid.
                validateAuxiliaryRepositoryCheckoutDirectoryValid(programmingLanguage, auxiliaryRepository);

                // The checkout directory path must not be longer than 100 characters, since the database column is
                // limited to 100 characters.
                validateAuxiliaryRepositoryCheckoutDirectoryLength(auxiliaryRepository);

                // Multiple auxiliary repositories should not share one checkout directory
                validateAuxiliaryRepositoryCheckoutDirectoryDuplication(auxiliaryRepository, otherRepositories);
            }
        }

        // The description must not be longer than 100 characters, since the database column is
        // limited to 500 characters.
        validateAuxiliaryRepositoryDescriptionLength(auxiliaryRepository);
    }

    /**
     * Checks if the given repository is an auxiliary repository of the given exercise.
     *
     * @param repositoryName the name of the repository to check.
     * @param exercise       the exercise to check.
     * @return true if the repository is an auxiliary repository of the exercise, false otherwise.
     */
    public boolean isAuxiliaryRepositoryOfExercise(String repositoryName, ProgrammingExercise exercise) {
        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findByExerciseId(exercise.getId());
        for (AuxiliaryRepository repo : auxiliaryRepositories) {
            if (repo.getName().equals(repositoryName)) {
                return true;
            }
        }
        return false;
    }
}
