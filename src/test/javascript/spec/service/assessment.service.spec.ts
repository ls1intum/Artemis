import { isAllowedToModifyFeedback, isAllowedToRespondToComplaintAction } from 'app/assessment/assessment.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

describe('Assessment Service', () => {
    const user = {} as User;
    const resultLock = { assessor: user } as Result;
    const result = { completionDate: dayjs().subtract(5, 'day'), assessor: user } as Result;
    const automaticResult = { completionDate: dayjs().subtract(5, 'day') } as Result;
    const complaint = { complaintType: ComplaintType.COMPLAINT, result } as Complaint;
    const feedbackRequest = { complaintType: ComplaintType.MORE_FEEDBACK, result } as Complaint;
    const complaintOnAutomaticResult = { ...complaint, result: automaticResult };
    const feedbackRequestOnAutomaticResult = { ...feedbackRequest, result: automaticResult };
    const exercise = { teamMode: false, assessmentType: AssessmentType.MANUAL } as Exercise;
    const exerciseAutomatic = { teamMode: false, assessmentType: AssessmentType.AUTOMATIC } as Exercise;
    const teamExercise = { teamMode: true, assessmentType: AssessmentType.MANUAL } as Exercise;
    describe('isAllowedToModifyFeedback', () => {
        it('should show during assessment', () => {
            const isAllowedBeforeDueDate = isAllowedToModifyFeedback(false, false, true, false, resultLock, undefined, exercise);
            const isAllowedAfterDueDate = isAllowedToModifyFeedback(false, false, true, true, resultLock, undefined, exercise);
            expect(isAllowedBeforeDueDate).toBe(true);
            expect(isAllowedAfterDueDate).toBe(true);
        });

        it('should hide after assessment without complaint', () => {
            const isAllowed = isAllowedToModifyFeedback(false, false, true, true, result, undefined, exercise);
            expect(isAllowed).toBe(false);
        });

        it('should show correctly after assessment with complaint', () => {
            const isAllowedAssessor = isAllowedToModifyFeedback(false, false, true, true, result, complaint, exercise);
            const isAllowedNotAssessor = isAllowedToModifyFeedback(false, false, false, true, result, complaint, exercise);
            expect(isAllowedAssessor).toBe(false);
            expect(isAllowedNotAssessor).toBe(true);
        });

        it('should hide for feedback requests', () => {
            complaint.result = result;
            const isAllowed = isAllowedToModifyFeedback(false, false, true, true, result, feedbackRequest, exercise);
            expect(isAllowed).toBe(false);
        });

        it('should hide if no complaint is set', () => {
            const isAllowed = isAllowedToModifyFeedback(false, false, true, true, result, undefined, exercise);
            expect(isAllowed).toBe(false);
        });

        it('should show if no complaint is set and the assessment is still running', () => {
            const isAllowedSubmitted = isAllowedToModifyFeedback(false, false, true, false, resultLock, undefined, exercise);
            const isAllowedNotSubmitted = isAllowedToModifyFeedback(false, false, true, false, result, undefined, exercise);
            expect(isAllowedSubmitted).toBe(true);
            expect(isAllowedNotSubmitted).toBe(true);
        });
    });

    describe('isAllowedToRespondToComplaintAction', () => {
        it('should allow if is assessor in teammode', () => {
            const isAllowedComplaint = isAllowedToRespondToComplaintAction(false, false, true, complaint, teamExercise);
            const isAllowedFeedbackRequest = isAllowedToRespondToComplaintAction(false, false, true, feedbackRequest, teamExercise);
            expect(isAllowedComplaint).toBe(true);
            expect(isAllowedFeedbackRequest).toBe(true);
        });

        it('should not allow if is not assessor in teammode', () => {
            const isAllowedComplaint = isAllowedToRespondToComplaintAction(false, false, false, complaint, teamExercise);
            const isAllowedFeedbackRequest = isAllowedToRespondToComplaintAction(false, false, false, feedbackRequest, teamExercise);
            expect(isAllowedComplaint).toBe(false);
            expect(isAllowedFeedbackRequest).toBe(false);
        });

        it('should allow for assessor if on a test run', () => {
            const isAllowedAssessor = isAllowedToRespondToComplaintAction(false, true, true, complaint, exercise);
            const isAllowedNotAssessor = isAllowedToRespondToComplaintAction(false, true, false, complaint, exercise);
            expect(isAllowedAssessor).toBe(true);
            expect(isAllowedNotAssessor).toBe(false);
        });

        it('should allow if assessor is not defined on individual exercises', () => {
            const isAllowedComplaint = isAllowedToRespondToComplaintAction(false, false, true, complaintOnAutomaticResult, exerciseAutomatic);
            const isAllowedFeedbackRequest = isAllowedToRespondToComplaintAction(false, false, true, feedbackRequestOnAutomaticResult, exerciseAutomatic);
            expect(isAllowedComplaint).toBe(true);
            expect(isAllowedFeedbackRequest).toBe(true);
        });

        it('should allow correctly for complaint', () => {
            const isAllowedAssessor = isAllowedToRespondToComplaintAction(false, false, true, complaint, exercise);
            const isAllowedNotAssessor = isAllowedToRespondToComplaintAction(false, false, false, complaint, exercise);
            expect(isAllowedAssessor).toBe(false);
            expect(isAllowedNotAssessor).toBe(true);
        });

        it('should allow correctly for feedback request', () => {
            const isAllowedAssessor = isAllowedToRespondToComplaintAction(false, false, true, feedbackRequest, exercise);
            const isAllowedNotAssessor = isAllowedToRespondToComplaintAction(false, false, false, feedbackRequest, exercise);
            expect(isAllowedAssessor).toBe(true);
            expect(isAllowedNotAssessor).toBe(false);
        });
    });
});
