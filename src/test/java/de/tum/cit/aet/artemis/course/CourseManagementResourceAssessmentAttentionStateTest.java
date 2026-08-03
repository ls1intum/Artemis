package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.iris.AbstractIrisChatSessionTest;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentAttentionDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;

/**
 * HTTP-contract tests for the {@code GET courses/{courseId}/assessment-attention-state} endpoint of
 * {@link de.tum.cit.aet.artemis.course.web.CourseManagementResource}.
 */
class CourseManagementResourceAssessmentAttentionStateTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "coursemanagementattention";

    @Autowired
    private IrisAssessmentRepository irisAssessmentRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnFalseWhenNoAssessmentsExist() throws Exception {
        var result = request.get(url(), HttpStatus.OK, IrisAssessmentAttentionDTO.class);

        assertThat(result.needsAttention()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnTrueWhenUnreviewedSuspiciousAssessmentExists() throws Exception {
        createAssessment(IrisVerdict.SUSPICIOUS, null);

        var result = request.get(url(), HttpStatus.OK, IrisAssessmentAttentionDTO.class);

        assertThat(result.needsAttention()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnFalseWhenSuspiciousAssessmentAlreadyReviewed() throws Exception {
        createAssessment(IrisVerdict.SUSPICIOUS, IrisVerdictReview.ACCEPTED);

        var result = request.get(url(), HttpStatus.OK, IrisAssessmentAttentionDTO.class);

        assertThat(result.needsAttention()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnFalseWhenAssessmentVerdictIsUnsuspicious() throws Exception {
        createAssessment(IrisVerdict.UNSUSPICIOUS, null);

        var result = request.get(url(), HttpStatus.OK, IrisAssessmentAttentionDTO.class);

        assertThat(result.needsAttention()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldReturnForbiddenWhenTutorRequestsAttentionState() throws Exception {
        request.get(url(), HttpStatus.FORBIDDEN, IrisAssessmentAttentionDTO.class);
    }

    private void createAssessment(IrisVerdict verdict, IrisVerdictReview review) {
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var assessment = new IrisAssessment(student, programmingExercise);
        assessment.setVerdict(verdict);
        assessment.setVerdictReview(review);
        irisAssessmentRepository.save(assessment);
    }

    private String url() {
        return "/api/core/courses/" + course.getId() + "/assessment-attention-state";
    }
}
