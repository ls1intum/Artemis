package de.tum.cit.aet.artemis.hyperion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.dto.CodeGenerationRequestDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class CodeGenerationRequestDTOTest {

    private Validator validator;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testValidCodeGenerationRequestDTO() {
        CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        Set<ConstraintViolation<CodeGenerationRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
        assertThat(dto.repositoryType()).isEqualTo(RepositoryType.SOLUTION);
    }

    @Test
    void testCodeGenerationRequestDTO_WithTemplateRepository() {
        CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(RepositoryType.TEMPLATE);

        Set<ConstraintViolation<CodeGenerationRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
        assertThat(dto.repositoryType()).isEqualTo(RepositoryType.TEMPLATE);
    }

    @Test
    void testCodeGenerationRequestDTO_WithTestsRepository() {
        CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(RepositoryType.TESTS);

        Set<ConstraintViolation<CodeGenerationRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
        assertThat(dto.repositoryType()).isEqualTo(RepositoryType.TESTS);
    }

    @Test
    void testCodeGenerationRequestDTO_WithNullRepositoryType() {
        CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(null);

        Set<ConstraintViolation<CodeGenerationRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("must not be null");
    }

    @Test
    void testCodeGenerationRequestDTO_JsonSerialization() throws JsonProcessingException {
        CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        String json = objectMapper.writeValueAsString(dto);

        assertThat(json).contains("repositoryType");
        assertThat(json).contains("SOLUTION");
    }

    @Test
    void testCodeGenerationRequestDTO_JsonDeserialization() throws JsonProcessingException {
        String json = "{\"repositoryType\":\"TEMPLATE\"}";

        CodeGenerationRequestDTO dto = objectMapper.readValue(json, CodeGenerationRequestDTO.class);

        assertThat(dto).isNotNull();
        assertThat(dto.repositoryType()).isEqualTo(RepositoryType.TEMPLATE);
    }

    @Test
    void testCodeGenerationRequestDTO_JsonIncludeNonEmpty() throws JsonProcessingException {
        CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(RepositoryType.TESTS);

        String json = objectMapper.writeValueAsString(dto);

        // Should include repositoryType since it's not empty
        assertThat(json).contains("repositoryType");
        assertThat(json).contains("TESTS");
    }

    @Test
    void testCodeGenerationRequestDTO_EqualityAndHashCode() {
        CodeGenerationRequestDTO dto1 = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);
        CodeGenerationRequestDTO dto2 = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);
        CodeGenerationRequestDTO dto3 = new CodeGenerationRequestDTO(RepositoryType.TEMPLATE);

        // Test equality
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1).isNotEqualTo(dto3);

        // Test hash code
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        assertThat(dto1.hashCode()).isNotEqualTo(dto3.hashCode());
    }

    @Test
    void testCodeGenerationRequestDTO_ToString() {
        CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(RepositoryType.SOLUTION);

        String toString = dto.toString();

        assertThat(toString).contains("CodeGenerationRequestDTO");
        assertThat(toString).contains("solution");
    }

    @Test
    void testCodeGenerationRequestDTO_AllRepositoryTypes() {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            CodeGenerationRequestDTO dto = new CodeGenerationRequestDTO(repositoryType);

            assertThat(dto.repositoryType()).isEqualTo(repositoryType);

            // Validate that non-null repository types pass validation
            if (repositoryType != null) {
                Set<ConstraintViolation<CodeGenerationRequestDTO>> violations = validator.validate(dto);
                assertThat(violations).isEmpty();
            }
        }
    }
}
