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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationResultDTO;
import de.tum.cit.aet.artemis.hyperion.service.codegeneration.HyperionCodeGenerationExecutionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

class HyperionCodeGenerationResourceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Mock
    private HyperionCodeGenerationExecutionService codeGenerationExecutionService;

    private HyperionCodeGenerationResource resource;

    private User testUser;

    private ProgrammingExercise testExercise;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resource = new HyperionCodeGenerationResource(userRepository, programmingExerciseRepository, codeGenerationExecutionService);

        testUser = new User();
        testUser.setLogin("testuser");

        testExercise = new ProgrammingExercise();
        testExercise.setId(1L);
        testExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        testExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
    }

    @Test
    void generateCode_withValidRequest_returnsSuccessfulResult() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);
        Result mockResult = mock(Result.class);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, RepositoryType.SOLUTION)).thenReturn(mockResult);
        when(mockResult.isSuccessful()).thenReturn(true);

        ResponseEntity<CodeGenerationResultDTO> response = resource.generateCode(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).contains("Solution code generated successfully");
        assertThat(response.getBody().attempts()).isEqualTo(1);

        verify(codeGenerationExecutionService).generateAndCompileCode(testExercise, testUser, RepositoryType.SOLUTION);
    }

    @Test
    void generateCode_withFailedResult_returnsFailureResult() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.TEMPLATE);
        Result mockResult = mock(Result.class);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, RepositoryType.TEMPLATE)).thenReturn(mockResult);
        when(mockResult.isSuccessful()).thenReturn(false);

        ResponseEntity<CodeGenerationResultDTO> response = resource.generateCode(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Template code generation failed");
        assertThat(response.getBody().attempts()).isEqualTo(3);
    }

    @Test
    void generateCode_withNullResult_returnsMaxAttemptsFailure() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.TESTS);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, RepositoryType.TESTS)).thenReturn(null);

        ResponseEntity<CodeGenerationResultDTO> response = resource.generateCode(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Code generation failed after maximum attempts");
        assertThat(response.getBody().attempts()).isEqualTo(3);
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
    void executeCodeGeneration_withSuccessfulExecution_returnsResult() {
        Result mockResult = mock(Result.class);

        when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, RepositoryType.SOLUTION)).thenReturn(mockResult);

        Result result = (Result) ReflectionTestUtils.invokeMethod(resource, "executeCodeGeneration", testExercise, testUser, RepositoryType.SOLUTION);

        assertThat(result).isEqualTo(mockResult);
    }

    @Test
    void executeCodeGeneration_withException_returnsNull() {
        when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, RepositoryType.SOLUTION)).thenThrow(new RuntimeException("Generation error"));

        Result result = (Result) ReflectionTestUtils.invokeMethod(resource, "executeCodeGeneration", testExercise, testUser, RepositoryType.SOLUTION);

        assertThat(result).isNull();
    }

    @Test
    void buildGenerationResponse_withSuccessfulSolution_returnsSuccessMessage() {
        Result mockResult = mock(Result.class);
        when(mockResult.isSuccessful()).thenReturn(true);

        CodeGenerationResultDTO result = (CodeGenerationResultDTO) ReflectionTestUtils.invokeMethod(resource, "buildGenerationResponse", mockResult, RepositoryType.SOLUTION);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Solution code generated successfully and compiles without errors.");
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Test
    void buildGenerationResponse_withSuccessfulTemplate_returnsSuccessMessage() {
        Result mockResult = mock(Result.class);
        when(mockResult.isSuccessful()).thenReturn(true);

        CodeGenerationResultDTO result = (CodeGenerationResultDTO) ReflectionTestUtils.invokeMethod(resource, "buildGenerationResponse", mockResult, RepositoryType.TEMPLATE);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Template code generated successfully and compiles without errors.");
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Test
    void buildGenerationResponse_withSuccessfulTests_returnsSuccessMessage() {
        Result mockResult = mock(Result.class);
        when(mockResult.isSuccessful()).thenReturn(true);

        CodeGenerationResultDTO result = (CodeGenerationResultDTO) ReflectionTestUtils.invokeMethod(resource, "buildGenerationResponse", mockResult, RepositoryType.TESTS);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("Test code generated successfully and compiles without errors.");
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Test
    void buildGenerationResponse_withFailedSolution_returnsFailureMessage() {
        Result mockResult = mock(Result.class);
        when(mockResult.isSuccessful()).thenReturn(false);

        CodeGenerationResultDTO result = (CodeGenerationResultDTO) ReflectionTestUtils.invokeMethod(resource, "buildGenerationResponse", mockResult, RepositoryType.SOLUTION);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Solution code generation failed. The generated code contains compilation errors that could not be resolved.");
        assertThat(result.attempts()).isEqualTo(3);
    }

    @Test
    void buildGenerationResponse_withFailedTemplate_returnsFailureMessage() {
        Result mockResult = mock(Result.class);
        when(mockResult.isSuccessful()).thenReturn(false);

        CodeGenerationResultDTO result = (CodeGenerationResultDTO) ReflectionTestUtils.invokeMethod(resource, "buildGenerationResponse", mockResult, RepositoryType.TEMPLATE);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Template code generation failed. The generated code contains compilation errors that could not be resolved.");
        assertThat(result.attempts()).isEqualTo(3);
    }

    @Test
    void buildGenerationResponse_withFailedTests_returnsFailureMessage() {
        Result mockResult = mock(Result.class);
        when(mockResult.isSuccessful()).thenReturn(false);

        CodeGenerationResultDTO result = (CodeGenerationResultDTO) ReflectionTestUtils.invokeMethod(resource, "buildGenerationResponse", mockResult, RepositoryType.TESTS);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Test code generation failed. The generated code contains compilation errors that could not be resolved.");
        assertThat(result.attempts()).isEqualTo(3);
    }

    @Test
    void buildSuccessMessage_withSolutionType_returnsCorrectMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", true, RepositoryType.SOLUTION);
        assertThat(message).isEqualTo("Solution code generated successfully and compiles without errors.");
    }

    @Test
    void buildSuccessMessage_withTemplateType_returnsCorrectMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", true, RepositoryType.TEMPLATE);
        assertThat(message).isEqualTo("Template code generated successfully and compiles without errors.");
    }

    @Test
    void buildSuccessMessage_withTestsType_returnsCorrectMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", true, RepositoryType.TESTS);
        assertThat(message).isEqualTo("Test code generated successfully and compiles without errors.");
    }

    @Test
    void buildSuccessMessage_withAuxiliaryType_returnsGenericMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", true, RepositoryType.AUXILIARY);
        assertThat(message).isEqualTo("Code generated successfully and compiles without errors.");
    }

    @Test
    void buildSuccessMessage_withFailedSolution_returnsCorrectMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", false, RepositoryType.SOLUTION);
        assertThat(message).isEqualTo("Solution code generation failed. The generated code contains compilation errors that could not be resolved.");
    }

    @Test
    void buildSuccessMessage_withFailedTemplate_returnsCorrectMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", false, RepositoryType.TEMPLATE);
        assertThat(message).isEqualTo("Template code generation failed. The generated code contains compilation errors that could not be resolved.");
    }

    @Test
    void buildSuccessMessage_withFailedTests_returnsCorrectMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", false, RepositoryType.TESTS);
        assertThat(message).isEqualTo("Test code generation failed. The generated code contains compilation errors that could not be resolved.");
    }

    @Test
    void buildSuccessMessage_withFailedAuxiliary_returnsGenericMessage() {
        String message = (String) ReflectionTestUtils.invokeMethod(resource, "buildSuccessMessage", false, RepositoryType.AUXILIARY);
        assertThat(message).isEqualTo("Code generation failed. The generated code contains compilation errors that could not be resolved.");
    }

    @Test
    void generateCode_withAllSupportedRepositoryTypes_processesCorrectly() {
        for (RepositoryType repositoryType : new RepositoryType[] { RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS }) {
            CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(repositoryType);
            Result mockResult = mock(Result.class);

            when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
            when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
            when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, repositoryType)).thenReturn(mockResult);
            when(mockResult.isSuccessful()).thenReturn(true);

            ResponseEntity<CodeGenerationResultDTO> response = resource.generateCode(1L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().success()).isTrue();
        }
    }

    @Test
    void generateCode_withExceptionDuringExecution_returnsFailureResponse() {
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, RepositoryType.SOLUTION)).thenThrow(new RuntimeException("Execution failed"));

        ResponseEntity<CodeGenerationResultDTO> response = resource.generateCode(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Code generation failed after maximum attempts");
    }

    @Test
    void generateCode_withValidExamExercise_processesSuccessfully() {
        de.tum.cit.aet.artemis.exam.domain.ExerciseGroup mockExerciseGroup = mock(de.tum.cit.aet.artemis.exam.domain.ExerciseGroup.class);
        testExercise.setExerciseGroup(mockExerciseGroup);
        CodeGenerationRequestDTO request = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);
        Result mockResult = mock(Result.class);

        when(userRepository.getUserWithGroupsAndAuthorities()).thenReturn(testUser);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(1L)).thenReturn(testExercise);
        when(codeGenerationExecutionService.generateAndCompileCode(testExercise, testUser, RepositoryType.SOLUTION)).thenReturn(mockResult);
        when(mockResult.isSuccessful()).thenReturn(true);

        ResponseEntity<CodeGenerationResultDTO> response = resource.generateCode(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
    }
}
