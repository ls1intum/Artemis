package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.domain.Feedback.SUBMISSION_POLICY_FEEDBACK_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.util.ModelFactory;

class SubmissionPolicyIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseGradingService gradingService;

    private Long programmingExerciseId;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        database.addUsers(2, 1, 1, 1);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addInstructor("other-instructor-group", "other-instructor");
        database.addEditor("other-editor-group", "other-editor");
        database.addStudent("other-student-group", "other-student");
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        programmingExerciseId = programmingExercise.getId();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    // Beginning of getSubmissionPolicyOfProgrammingExercise tests

    @Test
    @WithMockUser(username = "other-student1", roles = "USER")
    void test_getSubmissionPolicyOfProgrammingExercise_forbidden_foreignStudent() throws Exception {
        test_getSubmissionPolicyOfProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    void test_getSubmissionPolicyToProgrammingExercise_forbidden_foreignInstructor() throws Exception {
        test_getSubmissionPolicyOfProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_getSubmissionPolicyToProgrammingExercise_ok() throws Exception {
        int submissionLimitIdentifier = 531267835;
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().limit(submissionLimitIdentifier).active(true).policy());
        SubmissionPolicy policy = request.get(requestUrl(), HttpStatus.OK, LockRepositoryPolicy.class);
        assertThat(policy.getSubmissionLimit()).isEqualTo(submissionLimitIdentifier);
    }

    // Beginning of addSubmissionPolicyToProgrammingExercise tests

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void test_addSubmissionPolicyToProgrammingExercise_forbidden_student() throws Exception {
        test_addSubmissionPolicyToProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void test_addSubmissionPolicyToProgrammingExercise_forbidden_tutor() throws Exception {
        test_addSubmissionPolicyToProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void test_addSubmissionPolicyToProgrammingExercise_forbidden_editor() throws Exception {
        test_addSubmissionPolicyToProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_forbidden_foreignInstructor() throws Exception {
        test_addSubmissionPolicyToProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_notFound_exerciseNotPresent() throws Exception {
        request.post(requestUrlWrongId(), SubmissionPolicyBuilder.any(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_badRequest_exerciseHasPolicy() throws Exception {
        addAnySubmissionPolicyToExercise();
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.any());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_badRequest_policyHasId() throws Exception {
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.lockRepo().id(12L).policy());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_badRequest_submissionLimitNull() throws Exception {
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.lockRepo().active(true).limit(null).policy());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_badRequest_submissionLimitSmallerOne() throws Exception {
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.lockRepo().active(true).limit(0).policy());
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.lockRepo().active(true).limit(-1000).policy());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_badRequest_submissionPenaltyNull() throws Exception {
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.submissionPenalty().active(true).limit(10).penalty(null).policy());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_badRequest_submissionPenaltySmallerEqualZero() throws Exception {
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.submissionPenalty().active(true).limit(10).penalty(0.0).policy());
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.submissionPenalty().active(true).limit(10).penalty(-12.0).policy());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_badRequest_activeNull() throws Exception {
        addSubmissionPolicy_badRequest(SubmissionPolicyBuilder.submissionPenalty().active(null).limit(10).penalty(10.0).policy());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_ok_lockRepositoryPolicy() throws Exception {
        request.post(requestUrl(), SubmissionPolicyBuilder.lockRepo().active(false).limit(10).policy(), HttpStatus.CREATED);
        assertThat(updatedExercise().getSubmissionPolicy().getClass()).isEqualTo(LockRepositoryPolicy.class);
        assertThat(updatedExercise().getSubmissionPolicy().getSubmissionLimit()).isEqualTo(10);
        assertThat(updatedExercise().getSubmissionPolicy().isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_addSubmissionPolicyToProgrammingExercise_ok_submissionPenaltyPolicy() throws Exception {
        request.post(requestUrl(), SubmissionPolicyBuilder.submissionPenalty().active(false).limit(15).penalty(14.0).policy(), HttpStatus.CREATED);
        assertThat(updatedExercise().getSubmissionPolicy().getClass()).isEqualTo(SubmissionPenaltyPolicy.class);
        assertThat(updatedExercise().getSubmissionPolicy().getSubmissionLimit()).isEqualTo(15);
        assertThat(updatedExercise().getSubmissionPolicy().isActive()).isFalse();
        assertThat(((SubmissionPenaltyPolicy) updatedExercise().getSubmissionPolicy()).getExceedingPenalty()).isEqualTo(14.0);
    }

    // Beginning of updateSubmissionPolicy tests

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void test_updateSubmissionPolicy_forbidden_student() throws Exception {
        test_updateSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void test_updateSubmissionPolicy_forbidden_tutor() throws Exception {
        test_updateSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "TA")
    void test_updateSubmissionPolicy_forbidden_editor() throws Exception {
        test_updateSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    void test_updateSubmissionPolicy_forbidden_foreignInstructor() throws Exception {
        test_updateSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_updateSubmissionPolicy_notFound_exerciseNotPresent() throws Exception {
        request.patch(requestUrlWrongId(), SubmissionPolicyBuilder.any(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_updateSubmissionPolicy_badRequest_policyNotPresent() throws Exception {
        request.patch(requestUrl(), SubmissionPolicyBuilder.any(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_updateSubmissionPolicy_ok_lockRepositoryPolicy() throws Exception {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().active(true).limit(10).policy());
        request.patch(requestUrl(), SubmissionPolicyBuilder.lockRepo().active(true).limit(15).policy(), HttpStatus.OK);
        assertThat(updatedExercise().getSubmissionPolicy().getSubmissionLimit()).isEqualTo(15);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_updateSubmissionPolicy_ok_lockRepositoryPolicy_newLimitGreater() throws Exception {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().active(true).limit(2).policy());
        ProgrammingExerciseStudentParticipation participation1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        ProgrammingExerciseStudentParticipation participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        database.addProgrammingSubmissionToResultAndParticipation(new Result().score(20.0), participation1, "commit1");
        database.addProgrammingSubmissionToResultAndParticipation(new Result().score(25.0), participation2, "commit2");
        database.addProgrammingSubmissionToResultAndParticipation(new Result().score(30.0), participation2, "commit3");
        String repositoryName = programmingExercise.getProjectKey().toLowerCase() + "-student2";
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.mockUserExists("student2");
        bitbucketRequestMockProvider.mockGiveWritePermission(programmingExercise, repositoryName, "student2", HttpStatus.OK);
        bitbucketRequestMockProvider.mockProtectBranches(programmingExercise, repositoryName);
        request.patch(requestUrl(), SubmissionPolicyBuilder.lockRepo().active(true).limit(3).policy(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_updateSubmissionPolicy_ok_lockRepositoryPolicy_newLimitSmaller() throws Exception {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().active(true).limit(3).policy());
        ProgrammingExerciseStudentParticipation participation1 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        ProgrammingExerciseStudentParticipation participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        database.addProgrammingSubmissionToResultAndParticipation(new Result().score(20.0), participation1, "commit1");
        database.addProgrammingSubmissionToResultAndParticipation(new Result().score(25.0), participation2, "commit2");
        database.addProgrammingSubmissionToResultAndParticipation(new Result().score(30.0), participation2, "commit3");
        String repositoryName = programmingExercise.getProjectKey().toLowerCase() + "-student2";
        User student2 = userRepository.getUserByLoginElseThrow("student2");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        mockSetRepositoryPermissionsToReadOnly(participation2.getVcsRepositoryUrl(), programmingExercise.getProjectKey(), Set.of(student2));
        bitbucketRequestMockProvider.mockProtectBranches(programmingExercise, repositoryName);
        request.patch(requestUrl(), SubmissionPolicyBuilder.lockRepo().active(true).limit(2).policy(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_updateSubmissionPolicy_ok_submissionPenaltyPolicy() throws Exception {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.submissionPenalty().active(true).limit(10).penalty(10.0).policy());
        request.patch(requestUrl(), SubmissionPolicyBuilder.submissionPenalty().active(true).limit(15).penalty(10.0).policy(), HttpStatus.OK);
        assertThat(updatedExercise().getSubmissionPolicy().getSubmissionLimit()).isEqualTo(15);
        assertThat(((SubmissionPenaltyPolicy) updatedExercise().getSubmissionPolicy()).getExceedingPenalty()).isEqualTo(10.0);
    }

    // Beginning of toggleSubmissionPolicy tests

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void test_toggleSubmissionPolicy_forbidden_student() throws Exception {
        test_toggleSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void test_toggleSubmissionPolicy_forbidden_tutor() throws Exception {
        test_toggleSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void test_toggleSubmissionPolicy_forbidden_editor() throws Exception {
        test_toggleSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    void test_toggleSubmissionPolicy_forbidden_foreignInstructor() throws Exception {
        test_toggleSubmissionPolicy_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_toggleSubmissionPolicy_notFound_exerciseNotPresent() throws Exception {
        request.put(requestUrlWrongId() + activate(true), SubmissionPolicyBuilder.any(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_toggleSubmissionPolicy_badRequest_policyNotPresent() throws Exception {
        request.put(requestUrl() + activate(true), SubmissionPolicyBuilder.any(), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_toggleSubmissionPolicy_badRequest_policyActiveStatusMatchesRequest(boolean activate) throws Exception {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().active(activate).limit(10).policy());
        request.put(requestUrl() + activate(activate), SubmissionPolicyBuilder.any(), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_toggleSubmissionPolicy_ok_lockRepositoryPolicy(boolean activate) throws Exception {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().active(activate).limit(10).policy());
        request.put(requestUrl() + activate(!activate), SubmissionPolicyBuilder.any(), HttpStatus.OK);
        assertThat(updatedExercise().getSubmissionPolicy().isActive()).isEqualTo(!activate);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_toggleSubmissionPolicy_ok_submissionPenaltyPolicy(boolean activate) throws Exception {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.submissionPenalty().active(activate).penalty(10.0).limit(10).policy());
        request.put(requestUrl() + activate(!activate), SubmissionPolicyBuilder.any(), HttpStatus.OK);
        assertThat(updatedExercise().getSubmissionPolicy().isActive()).isEqualTo(!activate);
    }

    // Beginning of removeSubmissionPolicyFromProgrammingExercise tests

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void test_removeSubmissionPolicyFromProgrammingExercise_forbidden_student() throws Exception {
        test_removeSubmissionPolicyFromProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void test_removeSubmissionPolicyFromProgrammingExercise_forbidden_tutor() throws Exception {
        test_removeSubmissionPolicyFromProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void test_removeSubmissionPolicyFromProgrammingExercise_forbidden_editor() throws Exception {
        test_removeSubmissionPolicyFromProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    void test_removeSubmissionPolicyFromProgrammingExercise_forbidden_foreignInstructor() throws Exception {
        test_removeSubmissionPolicyFromProgrammingExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_removeSubmissionPolicyFromProgrammingExercise_badRequest_noPolicyPresent() throws Exception {
        request.delete(requestUrl(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_removeSubmissionPolicyFromProgrammingExercise_notFound_exerciseNotPresent() throws Exception {
        request.delete(requestUrlWrongId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_removeSubmissionPolicyFromProgrammingExercise_ok() throws Exception {
        addAnySubmissionPolicyToExercise();
        request.delete(requestUrl(), HttpStatus.OK);
    }

    // Beginning of other tests

    private enum EnforcePolicyTestType {
        POLICY_NULL, POLICY_ACTIVE, POLICY_INACTIVE
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(EnforcePolicyTestType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_enforceLockRepositoryPolicyOnStudentParticipation(EnforcePolicyTestType type) throws Exception {
        if (type != EnforcePolicyTestType.POLICY_NULL) {
            addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().limit(1).active(type == EnforcePolicyTestType.POLICY_ACTIVE).policy());
        }
        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        String repositoryName = programmingExercise.getProjectKey().toLowerCase() + "-student1";
        var resultNotification = ModelFactory.generateBambooBuildResult(repositoryName, null, null, null, List.of("test1"), List.of("test2", "test3"), new ArrayList<>());
        if (type == EnforcePolicyTestType.POLICY_ACTIVE) {
            mockBitbucketRequests(participation);
        }
        Optional<Result> result = gradingService.processNewProgrammingExerciseResult(participation, resultNotification);
        assertThat(result).isPresent();

        database.addProgrammingSubmissionToResultAndParticipation(new Result().score(25.0), participation, "commit1");
        result = gradingService.processNewProgrammingExerciseResult(participation, resultNotification);
        assertThat(result).isPresent();
        if (type == EnforcePolicyTestType.POLICY_ACTIVE) {
            assertThat(result.get().isRated()).isFalse();
        }
        else {
            assertThat(result.get().isRated()).isTrue();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(EnforcePolicyTestType.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void test_enforceSubmissionPenaltyPolicyOnStudentParticipation(EnforcePolicyTestType type) throws Exception {
        programmingExercise.setMaxPoints(10.0);
        programmingExerciseRepository.save(programmingExercise);
        if (type != EnforcePolicyTestType.POLICY_NULL) {
            addSubmissionPolicyToExercise(SubmissionPolicyBuilder.submissionPenalty().limit(1).penalty(1.0).active(type == EnforcePolicyTestType.POLICY_ACTIVE).policy());
        }
        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        String repositoryName = programmingExercise.getProjectKey().toLowerCase() + "-student1";
        var resultNotification = ModelFactory.generateBambooBuildResult(repositoryName, null, null, null, List.of("test1", "test2", "test3"), List.of(), new ArrayList<>());
        if (type == EnforcePolicyTestType.POLICY_ACTIVE) {
            mockBitbucketRequests(participation);
        }
        Optional<Result> result = gradingService.processNewProgrammingExerciseResult(participation, resultNotification);
        assertThat(result).isPresent();
        assertThat(result.get().getScore()).isEqualTo(25);
        result = gradingService.processNewProgrammingExerciseResult(participation, resultNotification);
        assertThat(result).isPresent();
        if (type == EnforcePolicyTestType.POLICY_ACTIVE) {
            assertThat(result.get().getScore()).isEqualTo(15);
            assertThat(result.get().getFeedbacks().stream().anyMatch(feedback -> feedback.getText().startsWith(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER))).isTrue();
        }
        else {
            assertThat(result.get().getScore()).isEqualTo(25);
            assertThat(result.get().getFeedbacks().stream().anyMatch(feedback -> feedback.getText().startsWith(SUBMISSION_POLICY_FEEDBACK_IDENTIFIER))).isFalse();
        }
    }

    private void mockBitbucketRequests(ProgrammingExerciseParticipation participation) throws Exception {
        User student = userRepository.getUserByLoginElseThrow("student1");
        bitbucketRequestMockProvider.enableMockingOfRequests();
        mockSetRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUrl(), programmingExercise.getProjectKey(), Set.of(student));
        bitbucketRequestMockProvider.mockProtectBranches(programmingExercise, programmingExercise.getProjectKey().toLowerCase() + "-student1");
    }

    private void test_getSubmissionPolicyOfProgrammingExercise_forbidden() throws Exception {
        request.get(requestUrl(), HttpStatus.FORBIDDEN, SubmissionPolicy.class);
    }

    private void test_addSubmissionPolicyToProgrammingExercise_forbidden() throws Exception {
        request.post(requestUrl(), SubmissionPolicyBuilder.any(), HttpStatus.FORBIDDEN);
    }

    private void test_updateSubmissionPolicy_forbidden() throws Exception {
        request.patch(requestUrl(), SubmissionPolicyBuilder.any(), HttpStatus.FORBIDDEN);
    }

    private void test_toggleSubmissionPolicy_forbidden() throws Exception {
        request.put(requestUrl() + activate(true), null, HttpStatus.FORBIDDEN);
    }

    private void test_removeSubmissionPolicyFromProgrammingExercise_forbidden() throws Exception {
        request.delete(requestUrl(), HttpStatus.FORBIDDEN);
    }

    private ProgrammingExercise updatedExercise() {
        return programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(programmingExerciseId);
    }

    private String requestUrl() {
        return "/api/programming-exercises/" + programmingExerciseId + "/submission-policy";
    }

    private String requestUrlWrongId() {
        return "/api/programming-exercises/" + Long.MAX_VALUE + "/submission-policy";
    }

    private String activate(boolean activate) {
        return "?activate=" + activate;
    }

    private void addSubmissionPolicyToExercise(SubmissionPolicy policy) {
        database.addSubmissionPolicyToExercise(policy, programmingExercise);
    }

    private void addAnySubmissionPolicyToExercise() {
        addSubmissionPolicyToExercise(SubmissionPolicyBuilder.lockRepo().active(true).limit(5).policy());
    }

    private void addSubmissionPolicy_badRequest(SubmissionPolicy submissionPolicy) throws Exception {
        request.post(requestUrl(), submissionPolicy, HttpStatus.BAD_REQUEST);
    }

    private record SubmissionPolicyBuilder(SubmissionPolicy policy) {

        static SubmissionPolicy any() {
            return new LockRepositoryPolicy();
        }

        static SubmissionPolicyBuilder lockRepo() {
            return new SubmissionPolicyBuilder(new LockRepositoryPolicy());
        }

        static SubmissionPolicyBuilder submissionPenalty() {
            return new SubmissionPolicyBuilder(new SubmissionPenaltyPolicy());
        }

        SubmissionPolicyBuilder id(Long id) {
            policy.setId(id);
            return this;
        }

        SubmissionPolicyBuilder active(Boolean active) {
            policy.setActive(active);
            return this;
        }

        SubmissionPolicyBuilder limit(Integer limit) {
            policy.setSubmissionLimit(limit);
            return this;
        }

        SubmissionPolicyBuilder penalty(Double penalty) {
            ((SubmissionPenaltyPolicy) policy).setExceedingPenalty(penalty);
            return this;
        }
    }

}
