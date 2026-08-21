package de.tum.cit.aet.artemis.iris.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentDTO.IrisAssessmentExerciseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationDTO.IrisAssessmentForParticipationDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Unit tests for the plain mapping/factory logic of the iris-assessment DTOs (no Spring context needed).
 */
class IrisAssessmentDTOTest {

    @Test
    void ofReturnsNullWhenAssessmentIsNull() {
        assertThat(IrisAssessmentDTO.of(null)).isNull();
    }

    @Test
    void ofMapsAssessmentFields() {
        var student = new User();
        student.setId(1L);
        student.setLogin("student1");
        student.setFirstName("Ada");

        var exercise = new ProgrammingExercise();
        exercise.setId(2L);
        exercise.setTitle("Sorting");

        var assessment = new IrisAssessment(student, exercise);
        assessment.setId(3L);
        assessment.setVerdict(IrisVerdict.SUSPICIOUS);
        assessment.setVerdictReview(IrisVerdictReview.ACCEPTED);

        var dto = IrisAssessmentDTO.of(assessment);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(3L);
        assertThat(dto.verdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
        assertThat(dto.verdictReview()).isEqualTo(IrisVerdictReview.ACCEPTED);
        assertThat(dto.student().login()).isEqualTo("student1");
        assertThat(dto.exercise().id()).isEqualTo(2L);
        assertThat(dto.exercise().title()).isEqualTo("Sorting");
        assertThat(dto.exercise().type()).isEqualTo(exercise.getType());
    }

    @Test
    void exerciseDtoOfReturnsNullWhenExerciseIsNull() {
        assertThat(IrisAssessmentExerciseDTO.of(null)).isNull();
    }

    @Test
    void studentDtoOfReturnsNullWhenStudentIsNull() {
        assertThat(StudentIrisAssessmentDTO.of(null)).isNull();
    }

    @Test
    void studentDtoOfMapsLoginAndName() {
        var student = new User();
        student.setLogin("student1");
        student.setFirstName("Ada");
        student.setLastName("Lovelace");

        var dto = StudentIrisAssessmentDTO.of(student);

        assertThat(dto).isNotNull();
        assertThat(dto.login()).isEqualTo("student1");
        assertThat(dto.name()).isEqualTo(student.getName());
    }

    @Test
    void participationDtoOfReturnsNullWhenParticipationIsNull() {
        assertThat(IrisAssessmentProgrammingStudentParticipationDTO.of(null, false)).isNull();
    }

    @Test
    void participationDtoOfUsesRegularAssessmentWhenNotInClass() {
        var exercise = new ProgrammingExercise();
        exercise.setId(1L);
        var student = new User();
        student.setLogin("student1");

        var regularAssessment = new IrisAssessment(student, exercise);
        regularAssessment.setId(10L);
        regularAssessment.setVerdict(IrisVerdict.UNSUSPICIOUS);

        var inClassAssessment = new IrisAssessment(student, exercise);
        inClassAssessment.setId(20L);
        inClassAssessment.setVerdict(IrisVerdict.SUSPICIOUS);

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(5L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(student);
        participation.setRepositoryUri("https://example.org/repo.git");
        participation.setBuildPlanId("BUILD-1");
        participation.setIrisAssessment(regularAssessment);
        participation.setIrisAssessmentInClass(inClassAssessment);

        var dto = IrisAssessmentProgrammingStudentParticipationDTO.of(participation, false, "https://ci.example.org/build");

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.exerciseId()).isEqualTo(1L);
        assertThat(dto.repositoryUri()).isEqualTo("https://example.org/repo.git");
        assertThat(dto.buildPlanId()).isEqualTo("BUILD-1");
        assertThat(dto.student().login()).isEqualTo("student1");
        assertThat(dto.irisAssessment().id()).isEqualTo(10L);
        assertThat(dto.irisAssessment().verdict()).isEqualTo(IrisVerdict.UNSUSPICIOUS);
    }

    @Test
    void participationDtoOfUsesInClassAssessmentWhenInClass() {
        var exercise = new ProgrammingExercise();
        exercise.setId(1L);
        var student = new User();
        student.setLogin("student1");

        var inClassAssessment = new IrisAssessment(student, exercise);
        inClassAssessment.setId(20L);
        inClassAssessment.setVerdict(IrisVerdict.SUSPICIOUS);

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(5L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(student);
        participation.setIrisAssessmentInClass(inClassAssessment);

        var dto = IrisAssessmentProgrammingStudentParticipationDTO.of(participation, true);

        assertThat(dto).isNotNull();
        assertThat(dto.irisAssessment().id()).isEqualTo(20L);
        assertThat(dto.irisAssessment().verdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
    }

    @Test
    void participationDtoOfReturnsNullIrisAssessmentWhenNoneExists() {
        var exercise = new ProgrammingExercise();
        exercise.setId(1L);
        var student = new User();
        student.setLogin("student1");

        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(5L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(student);

        var dto = IrisAssessmentProgrammingStudentParticipationDTO.of(participation, false);

        assertThat(dto).isNotNull();
        assertThat(dto.irisAssessment()).isNull();
    }

    @Test
    void participationForAssessmentDtoOfReturnsNullWhenAssessmentIsNull() {
        assertThat(IrisAssessmentForParticipationDTO.of(null)).isNull();
    }

    @Test
    void projectionToDtoMapsFieldsAndCombinesStudentName() {
        var projection = new IrisAssessmentProgrammingStudentParticipationProjectionDTO(5L, 1L, "https://example.org/repo.git", "BUILD-1", "student1", "Ada", "Lovelace", 10L,
                IrisVerdict.SUSPICIOUS, IrisVerdictReview.REJECTED);

        var dto = projection.toDto(3);

        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.exerciseId()).isEqualTo(1L);
        assertThat(dto.submissionCount()).isEqualTo(3);
        assertThat(dto.repositoryUri()).isEqualTo("https://example.org/repo.git");
        assertThat(dto.buildPlanId()).isEqualTo("BUILD-1");
        assertThat(dto.buildPlanUrl()).isNull();
        assertThat(dto.student().login()).isEqualTo("student1");
        assertThat(dto.student().name()).isEqualTo("Ada Lovelace");
        assertThat(dto.irisAssessment().id()).isEqualTo(10L);
        assertThat(dto.irisAssessment().verdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
        assertThat(dto.irisAssessment().verdictReview()).isEqualTo(IrisVerdictReview.REJECTED);
    }

    @Test
    void projectionToDtoOmitsLastNameWhenBlank() {
        var projection = new IrisAssessmentProgrammingStudentParticipationProjectionDTO(5L, 1L, null, null, "student1", "Ada", "", null, null, null);

        var dto = projection.toDto(null);

        assertThat(dto.student().name()).isEqualTo("Ada");
        assertThat(dto.irisAssessment()).isNull();
    }
}
