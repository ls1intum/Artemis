package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.course.service.CourseService;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.AuxiliaryRepositoryDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;

class ProgrammingExerciseUpdateMappingServiceTest {

    private ProgrammingExerciseUpdateMappingService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        service = new ProgrammingExerciseUpdateMappingService(mock(ModuleFeatureService.class), mock(CourseService.class), mock(ProgrammingExerciseValidationService.class),
                mock(CompetencyExerciseLinkService.class), mock(ExerciseVariantGroupService.class));
        exercise = new ProgrammingExercise();
        exercise.setAuxiliaryRepositories(new LinkedHashSet<>());
        exercise.getAuxiliaryRepositories().add(stored(1L, "docs", "docs-dir", "the documents"));
        exercise.getAuxiliaryRepositories().add(stored(2L, "data", "data-dir", null));
    }

    private static AuxiliaryRepository stored(Long id, String name, String checkoutDirectory, String description) {
        var repository = new AuxiliaryRepository();
        repository.setId(id);
        repository.setName(name);
        repository.setCheckoutDirectory(checkoutDirectory);
        repository.setDescription(description);
        repository.setRepositoryUri("https://vcs.example/git/PROJ/proj-" + name + ".git");
        return repository;
    }

    private static UpdateProgrammingExerciseDTO requestWith(List<AuxiliaryRepositoryDTO> auxiliaryRepositories) {
        var dto = mock(UpdateProgrammingExerciseDTO.class);
        when(dto.auxiliaryRepositories()).thenReturn(auxiliaryRepositories);
        return dto;
    }

    @Test
    void aRequestWithoutAListLeavesTheRepositoriesAlone() {
        assertThatCode(() -> service.validateAuxiliaryRepositoriesUnchanged(requestWith(null), exercise)).doesNotThrowAnyException();
    }

    @Test
    void aRequestThatRepeatsTheStoredListIsAccepted() {
        // the address is set by the server, so what the client sends for it does not matter, and neither does the order
        var request = requestWith(List.of(new AuxiliaryRepositoryDTO(2L, "data", "somewhere-else", "data-dir", null),
                new AuxiliaryRepositoryDTO(1L, "docs", "https://vcs.example/git/PROJ/proj-docs.git", "docs-dir", "the documents")));

        assertThatCode(() -> service.validateAuxiliaryRepositoriesUnchanged(request, exercise)).doesNotThrowAnyException();
    }

    @Test
    void anExerciseWithoutRepositoriesAcceptsAnEmptyList() {
        exercise.setAuxiliaryRepositories(new LinkedHashSet<>());

        assertThatCode(() -> service.validateAuxiliaryRepositoriesUnchanged(requestWith(List.of()), exercise)).doesNotThrowAnyException();
    }

    @Test
    void aNewRepositoryIsRefused() {
        var request = requestWith(List.of(new AuxiliaryRepositoryDTO(1L, "docs", null, "docs-dir", "the documents"), new AuxiliaryRepositoryDTO(2L, "data", null, "data-dir", null),
                new AuxiliaryRepositoryDTO(null, "extra", null, "extra-dir", null)));

        assertRefused(request);
    }

    @Test
    void aRemovedRepositoryIsRefused() {
        assertRefused(requestWith(List.of(new AuxiliaryRepositoryDTO(1L, "docs", null, "docs-dir", "the documents"))));
        assertRefused(requestWith(List.of()));
    }

    @Test
    void anEditedRepositoryIsRefused() {
        assertRefused(
                requestWith(List.of(new AuxiliaryRepositoryDTO(1L, "docs", null, "docs-dir", "the documents"), new AuxiliaryRepositoryDTO(2L, "renamed", null, "data-dir", null))));
        assertRefused(requestWith(
                List.of(new AuxiliaryRepositoryDTO(1L, "docs", null, "../elsewhere", "the documents"), new AuxiliaryRepositoryDTO(2L, "data", null, "data-dir", null))));
        assertRefused(requestWith(List.of(new AuxiliaryRepositoryDTO(1L, "docs", null, "docs-dir", "other text"), new AuxiliaryRepositoryDTO(2L, "data", null, "data-dir", null))));
    }

    @Test
    void aRepeatedEntryDoesNotHideAMissingRepository() {
        var docs = new AuxiliaryRepositoryDTO(1L, "docs", null, "docs-dir", "the documents");

        assertRefused(requestWith(List.of(docs, docs)));
    }

    @Test
    void aNewRepositoryThatTakesTheValuesOfAStoredOneIsRefused() {
        // without an id it would be created, not matched with the stored one
        assertRefused(
                requestWith(List.of(new AuxiliaryRepositoryDTO(null, "docs", null, "docs-dir", "the documents"), new AuxiliaryRepositoryDTO(2L, "data", null, "data-dir", null))));
    }

    private void assertRefused(UpdateProgrammingExerciseDTO request) {
        assertThatThrownBy(() -> service.validateAuxiliaryRepositoriesUnchanged(request, exercise)).isInstanceOfSatisfying(BadRequestAlertException.class,
                exception -> assertThat(exception.getErrorKey()).isEqualTo("auxiliaryRepositoriesChanged"));
    }
}
