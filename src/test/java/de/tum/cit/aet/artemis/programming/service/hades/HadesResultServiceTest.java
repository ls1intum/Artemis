package de.tum.cit.aet.artemis.programming.service.hades;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.localci.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.hades.dto.HadesTestResultsDTO;

@ExtendWith(MockitoExtension.class)
class HadesResultServiceTest {

    @Mock
    private ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Mock
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private HadesResultService hadesResultService;

    @BeforeEach
    void setUp() {
        hadesResultService = new HadesResultService(feedbackCreationService, testCaseRepository, programmingExerciseBuildConfigRepository);
    }

    @Test
    void convertBuildResult_withValidMap_returnsHadesTestResultsDTO() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("isBuildSuccessful", true);
        requestBody.put("passed", 5);

        var result = hadesResultService.convertBuildResult(requestBody);

        assertThat(result).isInstanceOf(HadesTestResultsDTO.class);
        HadesTestResultsDTO dto = (HadesTestResultsDTO) result;
        assertThat(dto.isBuildSuccessful()).isTrue();
        assertThat(dto.passed()).isEqualTo(5);
    }

    @Test
    void convertBuildResult_withBuildFailure_returnsFailed() {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("isBuildSuccessful", false);
        requestBody.put("passed", 0);

        var result = hadesResultService.convertBuildResult(requestBody);

        assertThat(result).isInstanceOf(HadesTestResultsDTO.class);
        assertThat(((HadesTestResultsDTO) result).isBuildSuccessful()).isFalse();
    }
}
