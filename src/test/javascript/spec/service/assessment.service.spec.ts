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
        it.each([[exercise], [exerciseAutomatic]])('should show during assessment', (exercise: Exercise) => {
            const isAllowedBeforeDueDate = isAllowedToModifyFeedback(false, true, false, resultLock, undefined, exercise);
            const isAllowedAfterDueDate = isAllowedToModifyFeedback(false, true, true, resultLock, undefined, exercise);
            expect(isAllowedBeforeDueDate).toBeTrue();
            expect(isAllowedAfterDueDate).toBeTrue();
        });

        it.each([[exercise], [exerciseAutomatic]])('should hide after assessment without complaint', (exercise: Exercise) => {
            const isAllowed = isAllowedToModifyFeedback(false, true, true, result, undefined, exercise);
            expect(isAllowed).toBeFalse();
        });

        it.each([[exercise], [exerciseAutomatic]])('should show correctly after assessment with complaint', (exercise: Exercise) => {
            const isAllowedAssessor = isAllowedToModifyFeedback(false, true, true, result, complaint, exercise);
            const isAllowedNotAssessor = isAllowedToModifyFeedback(false, false, true, result, complaint, exercise);
            expect(isAllowedAssessor).toBeFalse();
            expect(isAllowedNotAssessor).toBeTrue();
        });

        it.each([[exercise], [exerciseAutomatic]])('should hide for feedback requests', (exercise: Exercise) => {
            complaint.result = result;
            const isAllowed = isAllowedToModifyFeedback(false, true, true, result, feedbackRequest, exercise);
            expect(isAllowed).toBeFalse();
        });

        it.each([[exercise], [exerciseAutomatic]])('should hide if no complaint is set', (exercise: Exercise) => {
            const isAllowed = isAllowedToModifyFeedback(false, true, true, result, undefined, exercise);
            expect(isAllowed).toBeFalse();
        });

        it.each([[exercise], [exerciseAutomatic]])('should show if no complaint is set and the assessment is still running', (exercise: Exercise) => {
            const isAllowedSubmitted = isAllowedToModifyFeedback(false, true, false, resultLock, undefined, exercise);
            const isAllowedNotSubmitted = isAllowedToModifyFeedback(false, true, false, result, undefined, exercise);
            expect(isAllowedSubmitted).toBeTrue();
            expect(isAllowedNotSubmitted).toBeTrue();
        });
    });

    describe('isAllowedToRespondToComplaintAction', () => {
        it('should allow if is assessor in teammode', () => {
            const isAllowedComplaint = isAllowedToRespondToComplaintAction(false, true, complaint, teamExercise);
            const isAllowedFeedbackRequest = isAllowedToRespondToComplaintAction(false, true, feedbackRequest, teamExercise);
            expect(isAllowedComplaint).toBeTrue();
            expect(isAllowedFeedbackRequest).toBeTrue();
        });

        it('should not allow if is not assessor in teammode', () => {
            const isAllowedComplaint = isAllowedToRespondToComplaintAction(false, false, complaint, teamExercise);
            const isAllowedFeedbackRequest = isAllowedToRespondToComplaintAction(false, false, feedbackRequest, teamExercise);
            expect(isAllowedComplaint).toBeFalse();
            expect(isAllowedFeedbackRequest).toBeFalse();
        });

        it('should allow for assessor if on a test run', () => {
            const isAllowedAssessor = isAllowedToRespondToComplaintAction(true, true, complaint, exercise);
            const isAllowedNotAssessor = isAllowedToRespondToComplaintAction(true, false, complaint, exercise);
            expect(isAllowedAssessor).toBeTrue();
            expect(isAllowedNotAssessor).toBeFalse();
        });

        it('should allow if assessor is not defined on individual exercises', () => {
            const isAllowedComplaint = isAllowedToRespondToComplaintAction(false, true, complaintOnAutomaticResult, exerciseAutomatic);
            const isAllowedFeedbackRequest = isAllowedToRespondToComplaintAction(false, true, feedbackRequestOnAutomaticResult, exerciseAutomatic);
            expect(isAllowedComplaint).toBeTrue();
            expect(isAllowedFeedbackRequest).toBeTrue();
        });

        it('should allow correctly for complaint', () => {
            const isAllowedAssessor = isAllowedToRespondToComplaintAction(false, true, complaint, exercise);
            const isAllowedNotAssessor = isAllowedToRespondToComplaintAction(false, false, complaint, exercise);
            expect(isAllowedAssessor).toBeFalse();
            expect(isAllowedNotAssessor).toBeTrue();
        });

        it('should allow correctly for feedback request', () => {
            const isAllowedAssessor = isAllowedToRespondToComplaintAction(false, true, feedbackRequest, exercise);
            const isAllowedNotAssessor = isAllowedToRespondToComplaintAction(false, false, feedbackRequest, exercise);
            expect(isAllowedAssessor).toBeTrue();
            expect(isAllowedNotAssessor).toBeFalse();
        });
    });
});
