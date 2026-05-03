package de.tum.cit.aet.artemis.proof;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.dto.ProofExerciseDTO;
import de.tum.cit.aet.artemis.proof.repository.ProofExerciseRepository;
import de.tum.cit.aet.artemis.proof.util.ProofExerciseFactory;
import de.tum.cit.aet.artemis.proof.util.ProofExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ProofExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "proofexercise";

    @Autowired
    private ProofExerciseRepository proofExerciseRepository;

    @Autowired
    private ProofExerciseUtilService proofExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private Course course;

    private ProofExercise exercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = proofExerciseUtilService.addCourseWithProofExercise();
        exercise = (ProofExercise) course.getExercises().iterator().next();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProofExercise_asInstructor_returnsCreated() throws Exception {
        ProofExerciseDTO newExercise = ProofExerciseFactory.generateProofExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        ProofExerciseDTO result = request.postWithResponseBody("/api/proof/proof-exercises", newExercise, ProofExerciseDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.description()).isEqualTo(newExercise.description());
        assertThat(result.predefinedCheckboxState()).isEqualTo(newExercise.predefinedCheckboxState());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createProofExercise_asStudent_returnsForbidden() throws Exception {
        ProofExerciseDTO newExercise = ProofExerciseFactory.generateProofExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        request.postWithResponseBody("/api/proof/proof-exercises", newExercise, ProofExerciseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProofExercise_withExistingId_returnsBadRequest() throws Exception {
        ProofExerciseDTO exerciseWithId = ProofExerciseDTO.of(exercise);

        request.postWithResponseBody("/api/proof/proof-exercises", exerciseWithId, ProofExerciseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getProofExercise_asTutor_returnsOk() throws Exception {
        ProofExerciseDTO result = request.get("/api/proof/proof-exercises/" + exercise.getId(), HttpStatus.OK, ProofExerciseDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(exercise.getId());
        assertThat(result.description()).isEqualTo(exercise.getDescription());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getProofExercise_asStudent_returnsForbidden() throws Exception {
        request.get("/api/proof/proof-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, ProofExerciseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getProofExercisesForCourse_returnsList() throws Exception {
        var results = request.getList("/api/proof/courses/" + course.getId() + "/proof-exercises", HttpStatus.OK, ProofExerciseDTO.class);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().id()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProofExercise_asInstructor_returnsOk() throws Exception {
        exercise.setDescription("Updated description");
        ProofExerciseDTO updateDTO = ProofExerciseDTO.of(exercise);

        ProofExerciseDTO result = request.putWithResponseBody("/api/proof/proof-exercises", updateDTO, ProofExerciseDTO.class, HttpStatus.OK);

        assertThat(result.description()).isEqualTo("Updated description");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteProofExercise_asInstructor_returnsOk() throws Exception {
        request.delete("/api/proof/proof-exercises/" + exercise.getId(), HttpStatus.OK);

        assertThat(proofExerciseRepository.findById(exercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteProofExercise_asStudent_returnsForbidden() throws Exception {
        request.delete("/api/proof/proof-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProofExercise_asInstructor_returnsCreated() throws Exception {
        ProofExerciseDTO importTarget = ProofExerciseFactory.generateProofExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        ProofExerciseDTO result = request.postWithResponseBody("/api/proof/proof-exercises/import/" + exercise.getId(), importTarget, ProofExerciseDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotEqualTo(exercise.getId());
        assertThat(result.description()).isEqualTo(exercise.getDescription());
    }
}
