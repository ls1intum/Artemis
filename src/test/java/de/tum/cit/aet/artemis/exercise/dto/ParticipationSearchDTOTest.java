package de.tum.cit.aet.artemis.exercise.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class ParticipationSearchDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldHaveNoViolationsWhenPageAndPageSizeAreValid() {
        var search = new ParticipationSearchDTO(0, 20, null, null, null, null);

        Set<ConstraintViolation<ParticipationSearchDTO>> violations = validator.validate(search);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldHaveNoViolationsAtBoundaryValues() {
        var search = new ParticipationSearchDTO(0, 200, null, null, null, null);

        Set<ConstraintViolation<ParticipationSearchDTO>> violations = validator.validate(search);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldHaveViolationWhenPageIsNegative() {
        var search = new ParticipationSearchDTO(-1, 20, null, null, null, null);

        Set<ConstraintViolation<ParticipationSearchDTO>> violations = validator.validate(search);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("page");
    }

    @Test
    void shouldHaveViolationWhenPageSizeIsZero() {
        var search = new ParticipationSearchDTO(0, 0, null, null, null, null);

        Set<ConstraintViolation<ParticipationSearchDTO>> violations = validator.validate(search);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("pageSize");
    }

    @Test
    void shouldHaveViolationWhenPageSizeExceedsMaximum() {
        var search = new ParticipationSearchDTO(0, 201, null, null, null, null);

        Set<ConstraintViolation<ParticipationSearchDTO>> violations = validator.validate(search);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("pageSize");
    }
}
