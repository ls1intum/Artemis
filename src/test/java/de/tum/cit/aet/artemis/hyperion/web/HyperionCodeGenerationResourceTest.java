package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationExecutionService;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationJobService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class HyperionCodeGenerationResourceTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private HyperionCodeGenerationExecutionService codeGenerationExecutionService;

    @Mock
    private HyperionCodeGenerationJobService codeGenerationJobService;

    private HyperionCodeGenerationResource resource;

    private User testUser;

    private ProgrammingExercise testExercise;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resource = new HyperionCodeGenerationResource(userRepository, programmingExerciseRepository, codeGenerationExecutionService, codeGenerationJobService);

        testUser = new User();
        testUser.setLogin("testuser");

        testExercise = new ProgrammingExercise();
        testExercise.setId(1L);
        testExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        testExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
    }

    @Test
    void generateCode_withValidRequest_returnsJobId() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationJobService.startJob(testUser, testExercise, RepositoryType.SOLUTION)).thenReturn("job-123");

        ResponseEntity<CodeGenerationJobStartDTO> response = resource.generateCode(1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-123");
        verify(codeGenerationJobService).startJob(testUser, testExercise, RepositoryType.SOLUTION);
    }

    @Test
    void generateCode_withTemplateType_returnsJobId() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.TEMPLATE);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationJobService.startJob(testUser, testExercise, RepositoryType.TEMPLATE)).thenReturn("job-456");

        ResponseEntity<CodeGenerationJobStartDTO> response = resource.generateCode(1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-456");
        verify(codeGenerationJobService).startJob(testUser, testExercise, RepositoryType.TEMPLATE);
    }

    @Test
    void generateCode_withTestsType_returnsJobId() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.TESTS);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationJobService.startJob(testUser, testExercise, RepositoryType.TESTS)).thenReturn("job-789");

        ResponseEntity<CodeGenerationJobStartDTO> response = resource.generateCode(1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-789");
        verify(codeGenerationJobService).startJob(testUser, testExercise, RepositoryType.TESTS);
    }

    @Test
    void validateGenerationRequest_withNegativeExerciseId_throwsException() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "validateGenerationRequest", -1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Exercise ID must be positive");
    }

    @Test
    void validateGenerationRequest_withZeroExerciseId_throwsException() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "validateGenerationRequest", 0L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Exercise ID must be positive");
    }

    @Test
    void validateGenerationRequest_withNullRepositoryType_throwsException() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "validateGenerationRequest", 1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Repository type is required");
    }

    @Test
    void validateGenerationRequest_withUnsupportedRepositoryType_throwsException() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.AUXILIARY);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "validateGenerationRequest", 1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Repository type not supported for code generation");
    }

    @Test
    void validateGenerationRequest_withValidInput_passesValidation() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        // Should not throw any exception
        ReflectionTestUtils.invokeMethod(resource, "validateGenerationRequest", 1L, request);
    }

    @Test
    void isSupportedRepositoryType_withSolutionType_returnsTrue() {
        boolean result = (boolean) ReflectionTestUtils.invokeMethod(resource, "isSupportedRepositoryType", RepositoryType.SOLUTION);
        assertThat(result).isTrue();
    }

    @Test
    void isSupportedRepositoryType_withTemplateType_returnsTrue() {
        boolean result = (boolean) ReflectionTestUtils.invokeMethod(resource, "isSupportedRepositoryType", RepositoryType.TEMPLATE);
        assertThat(result).isTrue();
    }

    @Test
    void isSupportedRepositoryType_withTestsType_returnsTrue() {
        boolean result = (boolean) ReflectionTestUtils.invokeMethod(resource, "isSupportedRepositoryType", RepositoryType.TESTS);
        assertThat(result).isTrue();
    }

    @Test
    void isSupportedRepositoryType_withAuxiliaryType_returnsFalse() {
        boolean result = (boolean) ReflectionTestUtils.invokeMethod(resource, "isSupportedRepositoryType", RepositoryType.AUXILIARY);
        assertThat(result).isFalse();
    }

    @Test
    void loadProgrammingExercise_withValidId_returnsExercise() {
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);

        ProgrammingExercise result = (ProgrammingExercise) ReflectionTestUtils.invokeMethod(resource, "loadProgrammingExercise", 1L);

        assertThat(result).isEqualTo(testExercise);
        verify(programmingExerciseRepository).findByIdWithTemplateAndSolutionParticipationElseThrow(1L);
    }

    @Test
    void validateExerciseForGeneration_withExamExercise_passes() {
        de.tum.cit.aet.artemis.exam.domain.ExerciseGroup mockExerciseGroup = mock(de.tum.cit.aet.artemis.exam.domain.ExerciseGroup.class);
        testExercise.setExerciseGroup(mockExerciseGroup);

        // Should not throw any exception
        ReflectionTestUtils.invokeMethod(resource, "validateExerciseForGeneration", testExercise);
    }

    @Test
    void validateExerciseForGeneration_withNullBuildConfig_throwsException() {
        testExercise.setBuildConfig(null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(resource, "validateExerciseForGeneration", testExercise)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Exercise must have build configuration for code generation");
    }

    @Test
    void generateCode_withValidExamExercise_returnsJobId() {
        de.tum.cit.aet.artemis.exam.domain.ExerciseGroup mockExerciseGroup = mock(de.tum.cit.aet.artemis.exam.domain.ExerciseGroup.class);
        testExercise.setExerciseGroup(mockExerciseGroup);
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationJobService.startJob(testUser, testExercise, RepositoryType.SOLUTION)).thenReturn("job-exam-1");

        ResponseEntity<CodeGenerationJobStartDTO> response = resource.generateCode(1L, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo("job-exam-1");
        verify(codeGenerationJobService).startJob(testUser, testExercise, RepositoryType.SOLUTION);
    }
}
