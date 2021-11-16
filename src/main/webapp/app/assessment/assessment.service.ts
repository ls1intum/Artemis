import { Exercise } from 'app/entities/exercise.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Result } from 'app/entities/result.model';

/**
 * For team exercises, the team tutor is the assessor and handles both complaints and feedback requests himself
 * For individual exercises, complaints are handled by a secondary reviewer and feedback requests by the assessor himself
 * For exam test runs, the original assessor is allowed to respond to complaints.
 */
export const isAllowedToRespondToComplaintAction = (isAtLeastInstructor: boolean, isTestRun: boolean, isAssessor: boolean, complaint: Complaint, exercise?: Exercise): boolean => {
    if (isAtLeastInstructor) {
        return true;
    }
    if (exercise?.teamMode) {
        return isAssessor;
    } else {
        if (isTestRun) {
            return isAssessor;
        }
        if (complaint.result && complaint.result.assessor == undefined) {
            return true;
        }
        return complaint!.complaintType === ComplaintType.COMPLAINT ? !isAssessor : isAssessor;
    }
};

export const isAllowedToModifyFeedback = (
    isAtLeastInstructor: boolean,
    isTestRun: boolean,
    isAssessor: boolean,
    hasAssessmentDueDatePassed: boolean,
    complaint?: Complaint,
    result?: Result,
    exercise?: Exercise,
): boolean => {
    if (isAtLeastInstructor) {
        return true;
    }
    if (complaint) {
        return isAllowedToRespondToComplaintAction(isAtLeastInstructor, isTestRun, isAssessor, complaint, exercise);
    }
    if (!complaint && result?.completionDate) {
        return hasAssessmentDueDatePassed;
    }
    return true;
};
