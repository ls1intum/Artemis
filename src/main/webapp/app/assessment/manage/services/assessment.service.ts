import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

/**
 * For team exercises, the team tutor is the assessor and handles both complaints and feedback requests themself
 * For individual exercises, complaints are handled by a secondary reviewer and feedback requests by the assessor themself
 * For exam test runs, the original assessor is allowed to respond to complaints.
 */
export const isAllowedToRespondToComplaintAction = (isTestRun: boolean, isAssessor: boolean, complaint: Complaint, exercise?: Exercise): boolean => {
    if (exercise?.isAtLeastInstructor) {
        return true;
    }
    if (exercise?.teamMode || isTestRun) {
        return isAssessor;
    }
    if (exercise?.assessmentType === AssessmentType.AUTOMATIC && complaint.result && complaint.result.assessor === undefined) {
        return true;
    }
    return complaint!.complaintType === ComplaintType.COMPLAINT ? !isAssessor : isAssessor;
};

/**
 * During assessment, modifying the feedback should be allowed.
 * After the assessment, modification should be allowed if the assessment due date hasn't passed yet. After that, it should be prevented.
 * If a feedback request was filed, the feedback should not be modifiable.
 * If a complaint was filed, the feedback should be only modifiable if the user is allowed to handle the complaint.
 */
export const isAllowedToModifyFeedback = (
    isTestRun: boolean,
    isAssessor: boolean,
    hasAssessmentDueDatePassed: boolean,
    result?: Result,
    complaint?: Complaint,
    exercise?: Exercise,
): boolean => {
    if (exercise?.isAtLeastInstructor) {
        return true;
    }
    if (!result) {
        return false;
    }
    if (!result.completionDate) {
        return true;
    }
    if (complaint) {
        return complaint.complaintType === ComplaintType.COMPLAINT && isAllowedToRespondToComplaintAction(isTestRun, isAssessor, complaint, exercise);
    }
    return !hasAssessmentDueDatePassed;
};
