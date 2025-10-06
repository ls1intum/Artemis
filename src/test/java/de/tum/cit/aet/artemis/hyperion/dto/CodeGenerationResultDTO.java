package de.tum.cit.aet.artemis.hyperion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class CodeGenerationResultDTOTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testCodeGenerationResultDTO_Success() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(true, "Generation successful", 1);

        assertThat(dto.success()).isTrue();
        assertThat(dto.message()).isEqualTo("Generation successful");
        assertThat(dto.attempts()).isEqualTo(1);
    }

    @Test
    void testCodeGenerationResultDTO_Failure() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, "Generation failed", 3);

        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEqualTo("Generation failed");
        assertThat(dto.attempts()).isEqualTo(3);
    }

    @Test
    void testCodeGenerationResultDTO_WithNullMessage() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(true, null, 2);

        assertThat(dto.success()).isTrue();
        assertThat(dto.message()).isNull();
        assertThat(dto.attempts()).isEqualTo(2);
    }

    @Test
    void testCodeGenerationResultDTO_WithEmptyMessage() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, "", 1);

        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEmpty();
        assertThat(dto.attempts()).isEqualTo(1);
    }

    @Test
    void testCodeGenerationResultDTO_WithZeroAttempts() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(true, "Successful on first try", 0);

        assertThat(dto.success()).isTrue();
        assertThat(dto.message()).isEqualTo("Successful on first try");
        assertThat(dto.attempts()).isZero();
    }

    @Test
    void testCodeGenerationResultDTO_WithNegativeAttempts() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, "Invalid attempts", -1);

        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEqualTo("Invalid attempts");
        assertThat(dto.attempts()).isEqualTo(-1);
    }

    @Test
    void testCodeGenerationResultDTO_WithLongMessage() {
        String longMessage = "This is a very long error message that could contain detailed information about what went wrong during the code generation process, including stack traces and detailed error descriptions";
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, longMessage, 3);

        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEqualTo(longMessage);
        assertThat(dto.attempts()).isEqualTo(3);
    }

    @Test
    void testCodeGenerationResultDTO_JsonSerialization() throws JsonProcessingException {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(true, "Code generated successfully", 2);

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("success");
        assertThat(json).contains("true");
        assertThat(json).contains("message");
        assertThat(json).contains("Code generated successfully");
        assertThat(json).contains("attempts");
        assertThat(json).contains("2");
    }

    @Test
    void testCodeGenerationResultDTO_JsonDeserialization() throws JsonProcessingException {
        String json = "{\"success\":false,\"message\":\"Generation failed with errors\",\"attempts\":3}";

        CodeGenerationResultDTO dto = objectMapper.readValue(json, CodeGenerationResultDTO.class);

        assertThat(dto).isNotNull();
        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEqualTo("Generation failed with errors");
        assertThat(dto.attempts()).isEqualTo(3);
    }

    @Test
    void testCodeGenerationResultDTO_JsonIncludeNonEmpty() throws JsonProcessingException {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(true, "Success", 1);

        String json = objectMapper.writeValueAsString(dto);

        // Should include all fields since they're not empty
        assertThat(json).contains("success");
        assertThat(json).contains("message");
        assertThat(json).contains("attempts");
    }

    @Test
    void testCodeGenerationResultDTO_JsonWithEmptyMessage() throws JsonProcessingException {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, "", 2);

        String json = objectMapper.writeValueAsString(dto);

        // Empty string should be excluded due to JsonInclude.NON_EMPTY
        assertThat(json).contains("success");
        assertThat(json).doesNotContain("message");
        assertThat(json).contains("attempts");
    }

    @Test
    void testCodeGenerationResultDTO_EqualityAndHashCode() {
        CodeGenerationResultDTO dto1 = new CodeGenerationResultDTO(true, "Success", 1);
        CodeGenerationResultDTO dto2 = new CodeGenerationResultDTO(true, "Success", 1);
        CodeGenerationResultDTO dto3 = new CodeGenerationResultDTO(false, "Failure", 3);

        // Test equality
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1).isNotEqualTo(dto3);

        // Test hash code
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        assertThat(dto1.hashCode()).isNotEqualTo(dto3.hashCode());
    }

    @Test
    void testCodeGenerationResultDTO_ToString() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(true, "Generation completed", 1);

        String toString = dto.toString();

        assertThat(toString).contains("CodeGenerationResultDTO");
        assertThat(toString).contains("true");
        assertThat(toString).contains("Generation completed");
        assertThat(toString).contains("1");
    }

    @Test
    void testCodeGenerationResultDTO_WithSpecialCharacters() {
        String messageWithSpecialChars = "Generation failed: \"Invalid syntax\" & 'missing imports' <error> {code}";
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, messageWithSpecialChars, 2);

        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEqualTo(messageWithSpecialChars);
        assertThat(dto.attempts()).isEqualTo(2);
    }

    @Test
    void testCodeGenerationResultDTO_MaxAttempts() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, "Failed after max attempts", Integer.MAX_VALUE);

        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEqualTo("Failed after max attempts");
        assertThat(dto.attempts()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void testCodeGenerationResultDTO_MinAttempts() {
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(true, "Success with minimum attempts", Integer.MIN_VALUE);

        assertThat(dto.success()).isTrue();
        assertThat(dto.message()).isEqualTo("Success with minimum attempts");
        assertThat(dto.attempts()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void testCodeGenerationResultDTO_CommonSuccessScenarios() {
        // Test common success messages
        CodeGenerationResultDTO solutionSuccess = new CodeGenerationResultDTO(true, "Solution code generated successfully and compiles without errors.", 1);
        CodeGenerationResultDTO templateSuccess = new CodeGenerationResultDTO(true, "Template code generated successfully and compiles without errors.", 2);
        CodeGenerationResultDTO testSuccess = new CodeGenerationResultDTO(true, "Test code generated successfully and compiles without errors.", 1);

        assertThat(solutionSuccess.success()).isTrue();
        assertThat(templateSuccess.success()).isTrue();
        assertThat(testSuccess.success()).isTrue();

        assertThat(solutionSuccess.attempts()).isEqualTo(1);
        assertThat(templateSuccess.attempts()).isEqualTo(2);
        assertThat(testSuccess.attempts()).isEqualTo(1);
    }

    @Test
    void testCodeGenerationResultDTO_CommonFailureScenarios() {
        // Test common failure messages
        CodeGenerationResultDTO solutionFailure = new CodeGenerationResultDTO(false,
                "Solution code generation failed. The generated code contains compilation errors that could not be resolved.", 3);
        CodeGenerationResultDTO templateFailure = new CodeGenerationResultDTO(false,
                "Template code generation failed. The generated code contains compilation errors that could not be resolved.", 3);
        CodeGenerationResultDTO testFailure = new CodeGenerationResultDTO(false,
                "Test code generation failed. The generated code contains compilation errors that could not be resolved.", 3);

        assertThat(solutionFailure.success()).isFalse();
        assertThat(templateFailure.success()).isFalse();
        assertThat(testFailure.success()).isFalse();

        assertThat(solutionFailure.attempts()).isEqualTo(3);
        assertThat(templateFailure.attempts()).isEqualTo(3);
        assertThat(testFailure.attempts()).isEqualTo(3);
    }

    @Test
    void testCodeGenerationResultDTO_MaxFailureMessage() {
        String maxFailureMessage = "Code generation failed after maximum attempts. Please check the exercise configuration and try again.";
        CodeGenerationResultDTO dto = new CodeGenerationResultDTO(false, maxFailureMessage, 3);

        assertThat(dto.success()).isFalse();
        assertThat(dto.message()).isEqualTo(maxFailureMessage);
        assertThat(dto.attempts()).isEqualTo(3);
    }
}
